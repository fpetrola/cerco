package com.fpetrola.cerco.exemptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads .cerco/exemptions.yaml and says what is wrong with it. An exemption needs a known rule, a target, a reason
 * long enough to be one, a category, and exactly one limit; and the file may not hold more than the cap.
 */
public final class ExemptionsFile {
  /** A valid file, as `explain` prints it: the shape nobody should have to read from the bytecode. */
  public static final String EXAMPLE = """
      exemptions:
        - rule: no-cast
          target: vista-swing/src/main/java/com/fpetrola/vista/swing/ComponentKind.java
          reason: the decoder between Swing's hierarchy, which is not ours, and our capabilities; the one cast
          category: decision
          until: 2027-06-30
        - rule: no-type-keyed-map
          target: vista-swing/**
          reason: the registry of kinds by Swing class keeps template discovery automatic, a deliberate choice
          category: decision
          untilVersion: 2.0.0
        - rule: no-instanceof
          target: oop/zx-transform/**
          reason: JavaParser nodes are dispatched by type until the visitors land, task T-77
          category: migration
          budget: 12

      # rule: a rule id from `explain`. target: a glob over paths relative to the root (** spans directories).
      # reason: at least 20 characters. category: false-positive | migration | decision.
      # Exactly one limit: until (a date, YYYY-MM-DD), untilVersion (the project version that ends it) or
      # budget (how many findings it may cover at most). At most 10 exemptions. Then: mvn cerco:approve
      """;

  private static final Set<String> CATEGORIES = Set.of("false-positive", "migration", "decision");
  private final Path path;
  private final Set<String> knownRules;
  private final int cap;

  public ExemptionsFile(Path path, Set<String> knownRules, int cap) {
    this.path = path;
    this.knownRules = knownRules;
    this.cap = cap;
  }

  public Path path() {
    return path;
  }

  public Loaded load() {
    if (!Files.exists(path)) {
      return Loaded.none();
    }
    byte[] bytes = read();
    String digest = new Digest(bytes).hex();
    List<String> problems = new ArrayList<>();
    List<Exemption> exemptions = new ArrayList<>();
    for (ExemptionEntry entry : entries(new String(bytes, StandardCharsets.UTF_8), problems)) {
      exemption(exemptions.size() + 1, entry, problems).ifPresent(exemptions::add);
    }
    if (exemptions.size() > cap) {
      problems.add("exemptions.yaml holds " + exemptions.size() + " exemptions and the cap is " + cap
          + ": exemptions are a budget, not a second rule set; fix code or raise the cap in the pom with approval");
    }
    return new Loaded(List.copyOf(exemptions), List.copyOf(problems), Optional.of(digest));
  }

  private List<ExemptionEntry> entries(String text, List<String> problems) {
    try {
      Yaml yaml = new Yaml(new Constructor(ExemptionsDocument.class, new LoaderOptions()));
      ExemptionsDocument document = yaml.load(text);
      return document == null ? List.of() : document.getExemptions();
    } catch (YAMLException e) {
      problems.add("exemptions.yaml is not readable: " + e.getMessage());
      return List.of();
    }
  }

  private Optional<Exemption> exemption(int ordinal, ExemptionEntry entry, List<String> problems) {
    List<String> wrong = new ArrayList<>();
    if (!knownRules.contains(entry.getRule())) {
      wrong.add("rule '" + entry.getRule() + "' is not one of " + knownRules.stream().sorted().toList());
    }
    if (entry.getTarget().isBlank()) {
      wrong.add("target is empty; it is a glob over paths relative to the project root");
    }
    if (entry.getReason().strip().length() < 20) {
      wrong.add("reason must say why in at least 20 characters");
    }
    if (!CATEGORIES.contains(entry.getCategory())) {
      wrong.add("category must be one of " + CATEGORIES.stream().sorted().toList());
    }
    Optional<Limit> limit = limit(entry, wrong);
    if (!wrong.isEmpty()) {
      problems.add("exemption #" + ordinal + ": " + String.join("; ", wrong));
      return Optional.empty();
    }
    return limit.map(l -> new Exemption(ordinal, entry.getRule(), entry.getTarget(), entry.getReason().strip(),
        entry.getCategory(), l));
  }

  private Optional<Limit> limit(ExemptionEntry entry, List<String> wrong) {
    List<Limit> limits = new ArrayList<>();
    if (!entry.getUntil().isBlank()) {
      date(entry.getUntil(), wrong).ifPresent(date -> limits.add(new Limit.Until(date)));
    }
    if (!entry.getUntilVersion().isBlank()) {
      limits.add(new Limit.UntilVersion(entry.getUntilVersion().strip()));
    }
    if (entry.getBudget() != null) {
      limits.add(new Limit.Budget(entry.getBudget()));
    }
    if (limits.size() != 1) {
      wrong.add("exactly one limit is required: until (a date), untilVersion, or budget (a count)");
      return Optional.empty();
    }
    return Optional.of(limits.get(0));
  }

  private Optional<LocalDate> date(String text, List<String> wrong) {
    try {
      return Optional.of(LocalDate.parse(text.strip()));
    } catch (DateTimeParseException e) {
      wrong.add("until must be a date like 2027-03-31, not '" + text + "' (" + e.getMessage() + ")");
      return Optional.empty();
    }
  }

  private byte[] read() {
    try {
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + path, e);
    }
  }
}
