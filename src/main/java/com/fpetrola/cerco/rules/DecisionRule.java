package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A rule that catches code deciding over a set of values (switches, type codes, chains, enum constants tested from
 * outside): whatever the construct, what such a decision hides is the same, so the family says it once, and reads
 * each decision against the project when the run ends.
 */
public abstract class DecisionRule extends GatheringRule<List<Decision>> {
  private final DecisionDiagnosis suspected = new DecisionDiagnosis();

  @Override
  public final Diagnosis diagnosis() {
    return new Diagnosis(suspected.hypotheses(), "a map of lambdas keyed by the same constants, a chain of "
        + "Optional.or, or the switch split in two: the same decision in another place");
  }

  /** The decisions of a file this rule catches, each with what will be needed to read it. */
  protected abstract List<Decision> decisions(ParsedFile file, CompilationUnit unit, Decisions reader);

  @Override
  protected final List<Decision> start() {
    return new ArrayList<>();
  }

  @Override
  protected final void gather(ParsedFile file, CompilationUnit unit, List<Decision> decisions) {
    decisions.addAll(decisions(file, unit, new Decisions(unit)));
  }

  @Override
  protected final List<Finding> conclude(List<Decision> decisions, Project project) {
    return decisions.stream().map(decision -> suspected.read(decision, project).map(decision.finding()::read)
        .orElse(decision.finding())).toList();
  }
}
