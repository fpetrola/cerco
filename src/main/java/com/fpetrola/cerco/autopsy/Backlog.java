package com.fpetrola.cerco.autopsy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * The rules the filed autopsies ask for, most asked first: a rule proposed once is an idea, one that keeps coming
 * back from unrelated fixes is the gate's next rule. `same-as:` counts for the rule of the autopsy it names. A rule
 * asked for is owed until the running cerco has it or a person declines it: proposing is not enough.
 */
public final class Backlog {
  private final Catalog catalog;

  public Backlog(Catalog catalog) {
    this.catalog = catalog;
  }

  /** One proposed rule and the autopsies that asked for it. */
  public record Wanted(String ruleId, List<String> autopsies) {
  }

  public List<Wanted> wanted() {
    Map<String, Form> done = catalog.done();
    Map<String, List<String>> byRule = new TreeMap<>();
    done.forEach((id, form) -> byRule.computeIfAbsent(proposal(done, form, 0), rule -> new ArrayList<>()).add(id));
    byRule.remove("");
    return byRule.entrySet().stream().map(entry -> new Wanted(entry.getKey(), List.copyOf(entry.getValue())))
        .sorted(Comparator.comparingInt((Wanted wanted) -> -wanted.autopsies().size()).thenComparing(Wanted::ruleId))
        .toList();
  }

  /** The rules asked for that this cerco does not have and nobody declined, each owed with what building it takes. */
  public List<Owed> unbuilt(Set<String> known, List<String> declined) {
    return wanted().stream().filter(one -> !known.contains(one.ruleId()) && !declined.contains(one.ruleId()))
        .map(one -> new Owed("rule-" + one.ruleId(), one.ruleId(), "autopsies/" + one.autopsies().get(0) + ".md",
            List.of(building(one)))).toList();
  }

  private String building(Wanted one) {
    return "the rule `" + one.ruleId() + "` is proposed by " + String.join(", ", one.autopsies()) + " and this cerco "
        + "does not have it. Build it in cerco's sources at " + source() + ", on a branch, with the autopsies as its "
        + "specification: the rule and its tests, cerco's tests green (its self-check included), a new version, "
        + "`mvn install` there, and that version in this project's pom; it then arrives and freezes its findings "
        + "like any new rule. Only a person can decline it, under <declinedRules> in the plugin configuration, "
        + "signed with `mvn cerco:approve`";
  }

  private String source() {
    Properties properties = new Properties();
    try (InputStream in = Backlog.class.getResourceAsStream("/cerco-source.properties")) {
      if (in != null) {
        properties.load(in);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("could not read where cerco's sources are", e);
    }
    return properties.getProperty("source", "cerco's own repository");
  }

  /** The rule an autopsy proposes, following `same-as:` to the autopsy it names; empty when it proposes none. */
  private String proposal(Map<String, Form> done, Form form, int depth) {
    String first = form.section(Form.CANDIDATE).lines().findFirst().orElse("").strip();
    String rule = first.startsWith("rule:") ? word(first.substring("rule:".length())) : "";
    String same = first.startsWith("same-as:") ? word(first.substring("same-as:".length())) : "";
    boolean follow = !same.isEmpty() && done.containsKey(same) && depth < done.size();
    return follow ? proposal(done, done.get(same), depth + 1) : rule;
  }

  private String word(String text) {
    String stripped = text.strip();
    int space = stripped.indexOf(' ');
    return space < 0 ? stripped : stripped.substring(0, space);
  }
}
