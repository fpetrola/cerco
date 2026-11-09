package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.rules.BudgetRule;
import com.fpetrola.cerco.rules.Inspection;
import com.fpetrola.cerco.rules.Measuring;
import com.fpetrola.cerco.rules.Rule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The live runs of every active rule, each fed the same file in turn. */
final class Runs {
  private final Map<String, Inspection> inspections = new LinkedHashMap<>();
  private final Map<String, Measuring> measurings = new LinkedHashMap<>();

  Runs(List<Rule> rules) {
    for (Rule rule : rules) {
      rule.asBudget().ifPresentOrElse(budget -> measurings.put(budget.id(), budget.measuring()),
          () -> inspections.put(rule.id(), rule.inspection()));
    }
  }

  void visit(ParsedFile file) {
    inspections.values().forEach(inspection -> inspection.visit(file));
    measurings.values().forEach(measuring -> measuring.visit(file));
  }

  List<Finding> findings(Rule rule) {
    return inspections.get(rule.id()).findings();
  }

  List<Measure> measures(BudgetRule rule) {
    return measurings.get(rule.id()).measures();
  }
}
