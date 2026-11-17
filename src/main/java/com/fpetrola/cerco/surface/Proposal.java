package com.fpetrola.cerco.surface;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.project.Location;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The one open proposal to grow the seal: .cerco/surface/pending/proposal.md. cerco writes what grows, again on every whole
 * run; the agent writes why: what the sealed surface cannot do, what sealed member it considered, what the proposal
 * takes away, and the test that says what the new members promise. A person reads it and signs with approve.
 */
public final class Proposal {
  static final String GROWS = "Grows";
  static final String NEED = "Need";
  static final String CONSIDERED = "Considered";
  static final String REMOVES = "Removes";
  static final String CONTRACT = "Contract";
  static final int NEED_LENGTH = 150;
  static final int NONE_LENGTH = 40;
  private static final Pattern MEMBER = Pattern.compile("[\\w.$]+#[\\w<>$]+");
  private static final Pattern PATH = Pattern.compile("[\\w./-]+\\.java");
  private final Path root;
  private final Seal seal;
  private final Path file;

  public Proposal(Path root, Seal seal) {
    this.root = root;
    this.seal = seal;
    this.file = new com.fpetrola.cerco.model.Layout(root).proposal();
  }

  /** Rewrites what grows from a whole run's unsigned findings, keeping what the agent wrote; gone when none. */
  public void settle(List<Finding> unsigned) {
    try {
      if (unsigned.isEmpty()) {
        Files.deleteIfExists(file);
        return;
      }
      Map<String, String> written = sections(Files.isRegularFile(file) ? Files.readString(file) : "");
      Files.createDirectories(file.getParent());
      Files.writeString(file, text("# Surface proposal", unsigned, written), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write " + file, e);
    }
  }

  /** What the form still lacks to be read by a person; empty when it is complete. */
  public List<String> missing() {
    if (!Files.isRegularFile(file)) {
      return List.of(".cerco/surface/pending/proposal.md does not exist yet: a whole run (mvn cerco:audit) writes it");
    }
    Map<String, String> form = sections(read());
    List<String> missing = new ArrayList<>();
    if (form.getOrDefault(NEED, "").length() < NEED_LENGTH) {
      missing.add(NEED + ": what the sealed surface cannot do, in " + NEED_LENGTH + " characters or more");
    }
    if (!considered(form.getOrDefault(CONSIDERED, ""), growing(form.getOrDefault(GROWS, "")))) {
      missing.add(CONSIDERED + ": a sealed member, written Type#member as the seal has it, and why it falls short; "
          + "`none:` and why, in " + NONE_LENGTH + " characters or more, only when nothing is sealed yet in the "
          + "modules that grow");
    }
    if (form.getOrDefault(REMOVES, "").isBlank()) {
      missing.add(REMOVES + ": the sealed members this proposal takes away, or `none`");
    }
    List<Path> tests = PATH.matcher(form.getOrDefault(CONTRACT, "")).results().map(MatchResult::group)
        .filter(path -> path.contains("src/test/")).map(root::resolve).filter(Files::isRegularFile).toList();
    if (tests.isEmpty()) {
      missing.add(CONTRACT + ": the path of an existing test under src/test that says what the new members promise");
    }
    String said = tests.stream().map(this::read).collect(Collectors.joining("\n"));
    signatures(form.getOrDefault(GROWS, "")).stream().filter(signature -> !tests.isEmpty())
        .filter(signature -> !seal.had(signature)).filter(signature -> !new Named(signature).in(said)).forEach(signature -> missing.add(CONTRACT
            + ": no test it lists names `" + signature + "`; a member no test asks for is speculation: take it "
            + "back, or write the test that needs it"));
    return missing.stream().map(item -> ".cerco/surface/pending/proposal.md, " + item).toList();
  }

  /**
   * Whether the proposal says what sealed member it weighed. A module's first design has nothing sealed to weigh: when
   * every module that grows is empty in the seal, `none:` with a reason says so.
   */
  private boolean considered(String said, List<String> modules) {
    if (MEMBER.matcher(said).results().map(MatchResult::group).anyMatch(seal::says)) {
      return true;
    }
    return said.startsWith("none") && said.length() >= NONE_LENGTH && modules.stream().allMatch(seal::holdsNothing);
  }

  /** The modules the Grows section names, as cerco wrote them: `- module` lines, the project as the empty name. */
  private List<String> growing(String grows) {
    return grows.lines().filter(line -> line.startsWith("- ")).map(line -> line.substring(2).strip())
        .map(module -> module.equals("the project") ? "" : module).toList();
  }

  // A signature already signed, in another module or dropped since, moves: its contract was read when it was signed.

  /** The signatures the Grows section lists, as cerco wrote them: `  - `signature`` lines. */
  private List<String> signatures(String grows) {
    return grows.lines().map(String::strip).filter(line -> line.startsWith("- `") && line.endsWith("`"))
        .map(line -> line.substring(3, line.length() - 1)).toList();
  }

  /**
   * Grows the seal by what is unsigned now and files the proposal, signed, as .cerco/sealed/surface/signed/<NNNN>.md,
   * where an agent cannot edit it.
   */
  public void sign(List<Finding> unsigned, String signer, LocalDate day) {
    Map<String, String> written = sections(read());
    byModule(unsigned).forEach(seal::grow);
    try {
      Path archive = new com.fpetrola.cerco.model.Layout(root).seal().resolve("signed");
      Files.createDirectories(archive);
      Path signed = archive.resolve(String.format("%04d.md", filed(archive).size() + 1));
      Files.writeString(signed, text("# Surface proposal, signed by " + signer + " on " + day, unsigned, written),
          StandardCharsets.UTF_8);
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new UncheckedIOException("could not file " + file, e);
    }
  }

  private String text(String title, List<Finding> unsigned, Map<String, String> written) {
    StringBuilder grows = new StringBuilder();
    byModule(unsigned).forEach((module, signatures) -> {
      grows.append("- ").append(module.isEmpty() ? "the project" : module).append('\n');
      signatures.forEach(signature -> grows.append("  - `").append(signature).append("`\n"));
    });
    return title + "\n\n<!-- cerco rewrites Grows on every whole run. Fill the other sections; a person reads them "
        + "and signs with mvn cerco:approve, or says no and the members are taken back. -->\n\n"
        + "## " + GROWS + "\n\n" + grows + "\n"
        + section(written, NEED, "What the sealed surface cannot do that the task needs, in "
            + NEED_LENGTH + " characters or more.")
        + section(written, CONSIDERED, "At least one sealed member, written Type#member, and why it falls short. "
            + "When nothing is sealed yet in the modules that grow: `none:` and why.")
        + section(written, REMOVES, "The sealed members this proposal takes away, or `none`.")
        + section(written, CONTRACT, "The path of an existing test under src/test that says what the new members "
            + "promise.");
  }

  private String section(Map<String, String> written, String heading, String guidance) {
    String body = written.getOrDefault(heading, "");
    return "## " + heading + "\n\n" + (body.isBlank() ? "<!-- " + guidance + " -->" : body) + "\n\n";
  }

  /** What the form says under each `## ` heading, the guidance in comments left out. */
  private Map<String, String> sections(String text) {
    Map<String, String> sections = new TreeMap<>();
    String[] parts = text.replaceAll("(?s)<!--.*?-->", "").split("(?m)^## ");
    Stream.of(parts).skip(1).forEach(part -> {
      int end = part.indexOf('\n');
      sections.put((end < 0 ? part : part.substring(0, end)).strip(), end < 0 ? "" : part.substring(end).strip());
    });
    return sections;
  }

  /** Each module with the signatures it grows by, as the findings' keys carry them. */
  private Map<String, List<String>> byModule(List<Finding> unsigned) {
    return unsigned.stream().collect(Collectors.groupingBy(finding -> new Location(finding.file()).module(),
        TreeMap::new, Collectors.mapping(finding -> finding.key().substring(finding.key().indexOf('|') + 1),
            Collectors.toList())));
  }

  /** The signed proposals filed in this directory, NNNN.md. */
  private List<Path> filed(Path directory) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(one -> one.getFileName().toString().matches("\\d{4}\\.md")).toList();
    }
  }

  private String read() {
    return read(file);
  }

  private String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + path, e);
    }
  }
}
