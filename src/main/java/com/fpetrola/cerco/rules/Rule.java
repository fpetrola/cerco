package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.List;
import java.util.Optional;

/** A design rule: what it protects, how to satisfy it, and the findings it raises on a project's files. */
public interface Rule {

  String id();

  String principle();

  String description();

  String remedy();

  /** The models this smell usually hides; none for the rules about honesty rather than design. */
  default Diagnosis diagnosis() {
    return new Diagnosis("");
  }

  /** True when a change to one file can only move findings in that file, so the rule can run on a single edit. */
  default boolean perFile() {
    return true;
  }

  /** A fresh, stateful run of the rule; the engine feeds it every file in name order. */
  Inspection inspection();

  default List<Finding> inspect(List<ParsedFile> files) {
    Inspection inspection = inspection();
    files.forEach(inspection::visit);
    return inspection.findings();
  }

  /** The rule as a budget rule when it measures sizes against ceilings; empty for a plain rule. */
  default Optional<BudgetRule> asBudget() {
    return Optional.empty();
  }
}
