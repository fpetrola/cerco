package com.fpetrola.cerco.report;

import com.fpetrola.cerco.engine.Survey.Surveyed;
import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.surface.Hint;
import com.fpetrola.cerco.surface.Reach;
import com.fpetrola.cerco.surface.Surface;
import java.util.List;

/**
 * A survey as a person reads it to decide on a rule: per module, how many findings each rule has and how many per
 * thousand lines, so modules of known quality can be compared, what each module offers to others, then a sample of
 * the findings to judge by hand.
 */
public final class SurveyReport {
  private final List<String> rules;
  private final int sample;

  /** The rules to report, and how many findings per rule and module to show for judging by hand. */
  public SurveyReport(List<String> rules, int sample) {
    this.rules = List.copyOf(rules);
    this.sample = sample;
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
    out.append(missing(modules));
    out.append(surfaces(modules));
    for (String rule : rules) {
      out.append("\n[").append(rule).append("] up to ").append(sample).append(" per module, to judge by hand\n");
      for (Surveyed module : modules) {
        module.of(rule).stream().limit(sample).forEach(finding -> out.append(line(module, finding)));
      }
    }
    return out.toString();
  }

  /** Where each kind of modelling is lacking: per module, how many findings read as each missing model. */
  private String missing(List<Surveyed> modules) {
    StringBuilder out = new StringBuilder("\nwhat is missing, per module: findings read as each model, and those "
        + "their rule could not read\n\n").append(String.format("%-40s", "module"));
    List.of(MissingModel.values()).forEach(model -> out.append(String.format(" %10s", model.word())));
    out.append(String.format(" %10s%n", "unread"));
    for (Surveyed module : modules) {
      out.append(String.format("%-40s", clipped(module.module(), 40)));
      List.of(MissingModel.values()).forEach(model -> out.append(String.format(" %10d", module.readAs(model))));
      out.append(String.format(" %10d%n", module.unread()));
    }
    return out.toString();
  }

  /**
   * What each module offers to others at each reach, types and members; the part of its public members an interface
   * declares; how many public signatures say a text, an object, a flag or a function; and how many nothing names.
   */
  private String surfaces(List<Surveyed> modules) {
    StringBuilder out = new StringBuilder("\nsurface, per module: types/members at each reach, the part of the "
        + "public members an interface declares, public signatures by what they say, public members nothing names\n\n")
        .append(String.format("%-40s", "module"));
    List.of(Reach.values()).forEach(reach -> out.append(String.format(" %12s", reach.word())));
    out.append(String.format(" %9s", "promised"));
    List.of(Hint.values()).forEach(hint -> out.append(String.format(" %9s", hint.word())));
    out.append(String.format(" %9s%n", "unnamed"));
    for (Surveyed module : modules) {
      Surface surface = module.surface();
      out.append(String.format("%-40s", clipped(module.module(), 40)));
      List.of(Reach.values()).forEach(reach -> out.append(String.format(" %12s",
          surface.types(reach) + "/" + surface.operations(reach))));
      out.append(String.format(" %8.0f%%", surface.promised() * 100));
      List.of(Hint.values()).forEach(hint -> out.append(String.format(" %9d", surface.saying(hint))));
      out.append(String.format(" %9d%n", surface.unnamed().size()));
    }
    return out.toString();
  }

  private String line(Surveyed module, Finding finding) {
    return "  " + module.module() + "  " + finding.file() + ":" + finding.line() + "  " + finding.detail()
        + finding.reading().map(reading -> "\n      " + reading.line()).orElse("") + '\n';
  }

  private String clipped(String text, int width) {
    return text.length() <= width ? text : "…" + text.substring(text.length() - width + 1);
  }
}
