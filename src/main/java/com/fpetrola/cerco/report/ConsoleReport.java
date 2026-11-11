package com.fpetrola.cerco.report;

import com.fpetrola.cerco.autopsy.Owed;
import com.fpetrola.cerco.engine.Outcome;
import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.rules.Diagnosis;
import com.fpetrola.cerco.rules.Rule;
import com.fpetrola.cerco.rules.Rules;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The report as the agent reads it from the hook's stderr: the verdict, then per rule what it protects and what to
 * do, then each new finding. Written to be acted on, not admired.
 */
public final class ConsoleReport {
  private static final int PER_RULE = 25;
  private final Map<String, Known> rules;

  /** A rule, and how many autopsies of this project named each missing model for it. */
  private record Known(Rule rule, Map<String, Long> found) {
  }

  /** The rules, and per rule how many autopsies of this project named each missing model. */
  public ConsoleReport(Rules rules, Map<String, Map<String, Long>> modelsByRule) {
    this.rules = rules.all().stream().collect(Collectors.toMap(Rule::id,
        rule -> new Known(rule, modelsByRule.getOrDefault(rule.id(), Map.of()))));
  }

  public String render(Outcome outcome) {
    StringBuilder out = new StringBuilder(headline(outcome)).append('\n');
    Map<String, List<Finding>> byRule = outcome.fresh().stream()
        .collect(Collectors.groupingBy(Finding::rule, TreeMap::new, Collectors.toList()));
    byRule.forEach((rule, findings) -> out.append(section(rule, findings)));
    if (!outcome.owed().isEmpty()) {
      out.append(autopsies(outcome.owed()));
    }
    if (!outcome.problems().isEmpty()) {
      out.append("problems:\n");
      outcome.problems().forEach(problem -> out.append("  - ").append(problem).append('\n'));
    }
    if (!outcome.notes().isEmpty()) {
      out.append("notes:\n");
      outcome.notes().forEach(note -> out.append("  - ").append(note).append('\n'));
    }
    if (!outcome.fresh().isEmpty()) {
      out.append("Exemptions are a human's decision, signed with `mvn cerco:approve` at a terminal; .cerco/ is not "
          + "yours to edit, and the baseline only shrinks. Fix the code.\n");
    } else if (!outcome.problems().isEmpty() && outcome.owed().isEmpty()) {
      out.append("No new finding: what remains is a human's to settle (`mvn cerco:approve` at a terminal, or editing "
          + ".cerco/exemptions.yaml). Not yours; tell them and carry on.\n");
    }
    return out.toString();
  }

  private String headline(Outcome outcome) {
    String verdict = outcome.green() ? "cerco GREEN" : "cerco RED";
    return verdict + ": " + outcome.fresh().size() + " new finding(s) in " + outcome.label() + " (" + outcome.parsed()
        + " files, " + outcome.frozen() + " frozen, " + outcome.solved() + " solved, " + outcome.exempted().size()
        + " exempted, " + outcome.owed().size() + " autopsy(ies) owed, " + outcome.problems().size() + " problem(s), "
        + outcome.millis() + " ms)";
  }

  /**
   * What a fix still owes. Said every run until the forms are done, because the moment to look at why the code had
   * that shape is right after changing it, while the surroundings are still in mind.
   */
  private String autopsies(List<Owed> owed) {
    StringBuilder out = new StringBuilder("\n[autopsy] ").append(owed.size()).append(" owed\n")
        .append("  Findings went away: the symptom is gone, the cause is not understood yet. Fill each form below: the\n")
        .append("  model that was missing, the places around the fix you read, where else the same cause shows, the\n")
        .append("  principle, and the rule that would catch it next time. cerco files a complete form in autopsies/ by\n")
        .append("  itself; until then it stays red, and .cerco/autopsies keeps the debt even if the form is deleted.\n")
        .append("  A rule a filed autopsy proposes is owed the same way until the running cerco has it.\n");
    for (Owed one : owed) {
      out.append("  ").append(one.form()).append("  (").append(one.ruleId()).append(")\n");
      one.missing().forEach(missing -> out.append("    - ").append(missing).append('\n'));
    }
    return out.toString();
  }

  /**
   * Which models the smell usually hides, so the fix starts from the design: the agent names the one it found in the
   * autopsy, and what earlier autopsies of this project found is said first.
   */
  private String diagnosis(Known known, List<Finding> findings) {
    Diagnosis diagnosis = known.rule().diagnosis();
    if (!diagnosis.says()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    if (findings.stream().allMatch(finding -> finding.reading().isPresent())) {
      out.append("  what is missing is said under each finding; the autopsy names it as `model: <id>`\n");
    } else {
      out.append("  probably missing (find which in the code; the autopsy names it as `model: <id>`):\n");
      diagnosis.hypotheses().forEach(hypothesis -> out.append("    ").append(hypothesis.line()).append('\n'));
    }
    out.append("  not a fix: ").append(diagnosis.notAFix()).append('\n');
    Map<String, Long> found = known.found();
    if (!found.isEmpty()) {
      out.append("  in this project so far: ").append(found.entrySet().stream()
          .map(entry -> entry.getKey() + " " + entry.getValue()).collect(Collectors.joining(", "))).append('\n');
    }
    return out.toString();
  }

  /** Which model the code at this finding is missing and what it asks for, when its rule could tell. */
  private String reading(Finding finding) {
    return finding.reading().map(reading -> "      missing: " + reading.model().id() + ", because "
        + reading.because() + "\n      do: " + reading.then() + "\n").orElse("");
  }

  private String section(String ruleId, List<Finding> findings) {
    StringBuilder out = new StringBuilder();
    out.append("\n[").append(ruleId).append("] ").append(findings.size()).append(" new\n");
    Optional.ofNullable(rules.get(ruleId)).ifPresent(known -> {
      out.append("  principle: ").append(known.rule().principle()).append('\n');
      out.append("  do: ").append(known.rule().remedy()).append('\n');
      out.append(diagnosis(known, findings));
    });
    findings.stream().limit(PER_RULE).forEach(finding -> out.append("  ").append(finding.file()).append(':')
        .append(finding.line()).append("  ").append(finding.detail()).append('\n').append(reading(finding)));
    if (findings.size() > PER_RULE) {
      out.append("  ... and ").append(findings.size() - PER_RULE).append(" more\n");
    }
    return out.toString();
  }
}
