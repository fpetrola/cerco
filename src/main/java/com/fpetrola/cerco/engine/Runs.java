package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.project.Recall;
import com.fpetrola.cerco.rules.BudgetRule;
import com.fpetrola.cerco.rules.Inspection;
import com.fpetrola.cerco.rules.Measuring;
import com.fpetrola.cerco.rules.Rule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The live runs of every active rule, each fed the same file in turn, and the digest of each file's code. */
final class Runs {
  private final Map<String, Run> byRule = new LinkedHashMap<>();
  private final Digests digests = new Digests();
  private final Project project = new Project();

  /** One rule at work: looking for findings, or measuring sizes. */
  private record Run(Optional<Inspection> inspection, Optional<Measuring> measuring) {
    void visit(ParsedFile file) {
      inspection.ifPresent(looking -> looking.visit(file));
      measuring.ifPresent(sizing -> sizing.visit(file));
    }
  }

  Runs(List<Rule> rules) {
    for (Rule rule : rules) {
      Optional<Measuring> measuring = rule.asBudget().map(BudgetRule::measuring);
      byRule.put(rule.id(), new Run(Optional.of(rule).filter(plain -> measuring.isEmpty())
          .map(plain -> plain.inspection(project)), measuring));
    }
  }

  void visit(ParsedFile file) {
    project.learn(file);
    byRule.values().forEach(run -> run.visit(file));
    digests.keep(file);
  }

  /** A test of the project: heard by every run for who uses what, and part of what the project's digest covers. */
  void hear(ParsedFile test) {
    byRule.values().forEach(run -> run.inspection().ifPresent(looking -> looking.hear(test)));
    digests.keep(test);
  }

  /**
   * Every file of the project went through: the rules may take what the project does not declare as foreign, and
   * what it declares is kept for the runs over a few files.
   */
  void sawTheWholeProject(Recall recall) {
    project.markWhole();
    recall.keep(project);
  }

  /** A run over a few files starts from what the last whole run learnt; answers with what went wrong, if it did. */
  Optional<String> remember(Recall recall) {
    return recall.remind(project);
  }

  Digests digests() {
    return digests;
  }

  List<Finding> findings(Rule rule) {
    return byRule.get(rule.id()).inspection().map(Inspection::findings).orElse(List.of());
  }

  List<Measure> measures(BudgetRule rule) {
    return byRule.get(rule.id()).measuring().map(Measuring::measures).orElse(List.of());
  }
}
