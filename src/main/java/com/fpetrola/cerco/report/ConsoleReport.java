package com.fpetrola.cerco.report;

import com.fpetrola.cerco.engine.Outcome;
import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.rules.Rule;
import com.fpetrola.cerco.rules.Rules;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The report as the agent reads it from the hook's stderr: the verdict, then per rule what it protects and what to
 * do, then each new finding. Written to be acted on, not admired.
 */
public final class ConsoleReport {
  private static final int PER_RULE = 25;
  private final Map<String, Rule> rules;

  public ConsoleReport(Rules rules) {
    this.rules = rules.all().stream().collect(Collectors.toMap(Rule::id, Function.identity()));
  }

  public String render(Outcome outcome) {
    StringBuilder out = new StringBuilder(headline(outcome)).append('\n');
    Map<String, List<Finding>> byRule = outcome.fresh().stream()
        .collect(Collectors.groupingBy(Finding::rule, TreeMap::new, Collectors.toList()));
    byRule.forEach((rule, findings) -> out.append(section(rule, findings)));
    if (!outcome.problems().isEmpty()) {
      out.append("problems:\n");
      outcome.problems().forEach(problem -> out.append("  - ").append(problem).append('\n'));
    }
    if (!outcome.notes().isEmpty()) {
      out.append("notes:\n");
      outcome.notes().forEach(note -> out.append("  - ").append(note).append('\n'));
    }
    if (!outcome.green()) {
      out.append("Exemptions are a human's decision, signed with `mvn cerco:approve` at a terminal; .cerco/ is not "
          + "yours to edit, and the baseline only shrinks. Fix the code.\n");
    }
    return out.toString();
  }

  private String headline(Outcome outcome) {
    String verdict = outcome.green() ? "cerco GREEN" : "cerco RED";
    return verdict + ": " + outcome.fresh().size() + " new finding(s) in " + outcome.label() + " (" + outcome.parsed()
        + " files, " + outcome.frozen() + " frozen, " + outcome.solved() + " solved, " + outcome.exempted().size()
        + " exempted, " + outcome.problems().size() + " problem(s), " + outcome.millis() + " ms)";
  }

  private String section(String ruleId, List<Finding> findings) {
    StringBuilder out = new StringBuilder();
    Rule rule = rules.get(ruleId);
    out.append("\n[").append(ruleId).append("] ").append(findings.size()).append(" new\n");
    if (rule != null) {
      out.append("  principle: ").append(rule.principle()).append('\n');
      out.append("  do: ").append(rule.remedy()).append('\n');
    }
    findings.stream().limit(PER_RULE).forEach(finding -> out.append("  ").append(finding.file()).append(':')
        .append(finding.line()).append("  ").append(finding.detail()).append('\n'));
    if (findings.size() > PER_RULE) {
      out.append("  ... and ").append(findings.size() - PER_RULE).append(" more\n");
    }
    return out.toString();
  }
}
