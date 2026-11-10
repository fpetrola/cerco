package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.rules.BudgetRule;
import com.fpetrola.cerco.rules.Inspection;
import com.fpetrola.cerco.rules.Measuring;
import com.fpetrola.cerco.rules.Rule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The live runs of every active rule, each fed the same file in turn, and the digest of each file's code. */
final class Runs {
  private final Map<String, Inspection> inspections = new LinkedHashMap<>();
  private final Map<String, Measuring> measurings = new LinkedHashMap<>();
  private final Map<String, String> digests = new TreeMap<>();
  private final Project project = new Project();

  Runs(List<Rule> rules) {
    for (Rule rule : rules) {
      rule.asBudget().ifPresentOrElse(budget -> measurings.put(budget.id(), budget.measuring()),
          () -> inspections.put(rule.id(), rule.inspection(project)));
    }
  }

  void visit(ParsedFile file) {
    project.learn(file);
    inspections.values().forEach(inspection -> inspection.visit(file));
    measurings.values().forEach(measuring -> measuring.visit(file));
    digests.put(file.name(), file.digest());
  }

  /** Every file of the project went through: the rules may take what the project does not declare as foreign. */
  void sawTheWholeProject() {
    project.markWhole();
  }

  Map<String, String> digests() {
    return digests;
  }

  List<Finding> findings(Rule rule) {
    return inspections.get(rule.id()).findings();
  }

  List<Measure> measures(BudgetRule rule) {
    return measurings.get(rule.id()).measures();
  }
}
