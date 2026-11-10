package com.fpetrola.cerco.report;

import com.fpetrola.cerco.engine.Survey.Surveyed;
import com.fpetrola.cerco.model.Finding;
import java.util.List;

/**
 * A survey as a person reads it to decide on a rule: per module, how many findings each rule has and how many per
 * thousand lines, so modules of known quality can be compared, then a sample of the findings to judge by hand.
 */
public final class SurveyReport {
  private static final int SAMPLE = 10;
  private final List<String> rules;

  public SurveyReport(List<String> rules) {
    this.rules = List.copyOf(rules);
  }

  public String render(List<Surveyed> modules) {
    StringBuilder out = new StringBuilder("cerco survey: findings per module, and per 1000 lines\n\n");
    out.append(String.format("%-40s %8s", "module", "lines"));
    rules.forEach(rule -> out.append(String.format(" %24s", clipped(rule, 24))));
    out.append('\n');
    for (Surveyed module : modules) {
      out.append(String.format("%-40s %8d", clipped(module.module(), 40), module.lines()));
      rules.forEach(rule -> out.append(String.format(" %14d %9.2f", module.of(rule).size(),
          module.perThousandLines(rule))));
      out.append('\n');
    }
    for (String rule : rules) {
      out.append("\n[").append(rule).append("] up to ").append(SAMPLE).append(" per module, to judge by hand\n");
      for (Surveyed module : modules) {
        module.of(rule).stream().limit(SAMPLE).forEach(finding -> out.append(line(module, finding)));
      }
    }
    return out.toString();
  }

  private String line(Surveyed module, Finding finding) {
    return "  " + module.module() + "  " + finding.file() + ":" + finding.line() + "  " + finding.detail() + '\n';
  }

  private String clipped(String text, int width) {
    return text.length() <= width ? text : "…" + text.substring(text.length() - width + 1);
  }
}
