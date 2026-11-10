package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A rule that catches code deciding over a set of values (switches, type codes, chains, enum constants tested from
 * outside): whatever the construct, what such a decision hides is the same, so the family says it once, and reads
 * each decision against the project when the run ends.
 */
public abstract class DecisionRule extends SiteRule<Decision> {
  private final DecisionDiagnosis suspected = new DecisionDiagnosis();

  @Override
  public final Diagnosis diagnosis() {
    return new Diagnosis(suspected.hypotheses(), "a map of lambdas keyed by the same constants, a chain of "
        + "Optional.or, or the switch split in two: the same decision in another place");
  }

  /** The decisions of a file this rule catches, each with what will be needed to read it. */
  protected abstract List<Decision> decisions(ParsedFile file, CompilationUnit unit, Decisions reader);

  @Override
  protected final List<Decision> sites(ParsedFile file, CompilationUnit unit) {
    return decisions(file, unit, new Decisions(unit));
  }

  @Override
  protected final Finding found(Decision decision) {
    return decision.finding();
  }

  @Override
  protected final Function<Decision, Optional<Reading>> reader(List<Decision> decisions, Project project) {
    return decision -> suspected.read(decision, project);
  }
}
