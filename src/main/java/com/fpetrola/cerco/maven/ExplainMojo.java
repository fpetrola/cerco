package com.fpetrola.cerco.maven;

import com.fpetrola.cerco.engine.Engine;
import com.fpetrola.cerco.exemptions.ExemptionsFile;
import com.fpetrola.cerco.rules.Rule;
import org.apache.maven.plugins.annotations.Mojo;

/** Every rule the gate applies, what it protects, how to satisfy it, and how much of it the baseline froze. */
@Mojo(name = "explain", aggregator = true)
public final class ExplainMojo extends AbstractCercoMojo {

  @Override
  protected void run() {
    Engine engine = engine();
    StringBuilder out = new StringBuilder("cerco rules (baseline frozen on "
        + engine.store().frozenOn().orElse("never") + "):\n");
    engine.rules().each(rule -> {
      int frozen = rule.asBudget().map(budget -> engine.store().readCeilings(budget.id()).size())
          .orElseGet(() -> engine.store().read(rule.id()).total());
      out.append("\n").append(rule.id()).append("  (frozen: ").append(frozen).append(")\n")
          .append("  principle: ").append(rule.principle()).append('\n')
          .append("  what: ").append(rule.description()).append('\n')
          .append("  do: ").append(rule.remedy()).append('\n');
      rule.diagnosis().hypotheses().forEach(hypothesis -> out.append("  may hide ").append(hypothesis.line())
          .append('\n'));
    });
    out.append("\nExemptions: .cerco/exemptions.yaml, each with a rule, a target glob, a reason, a category "
        + "(false-positive, migration, decision) and one limit (until, untilVersion or budget), signed by a human "
        + "with `mvn cerco:approve` at a terminal. The file looks like this:\n\n")
        .append(ExemptionsFile.EXAMPLE);
    System.out.print(out);
  }
}
