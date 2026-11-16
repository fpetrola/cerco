package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
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

  /**
   * Other rule ids an autopsy asked for that this rule answers: a need that turned out to be part of it, so the
   * autopsy's debt is paid by this rule.
   */
  default java.util.Set<String> alsoAnswers() {
    return java.util.Set.of();
  }

  /** True when a change to one file can only move findings in that file, so the rule can run on a single edit. */
  default boolean perFile() {
    return true;
  }

  /**
   * A fresh, stateful run of the rule. The engine feeds it every file in name order, each right after the project
   * learnt it: the project is whole only when the findings are asked for, so that is when a rule may ask it.
   */
  Inspection inspection(Project project);

  /** The rule over these files as if they were the whole project. */
  default List<Finding> inspect(List<ParsedFile> files) {
    return inspect(files, List.of());
  }

  /** The rule over these files as the whole project, with its tests heard for who uses what. */
  default List<Finding> inspect(List<ParsedFile> files, List<ParsedFile> tests) {
    Project project = new Project();
    files.forEach(project::learn);
    project.markWhole();
    Inspection inspection = inspection(project);
    files.forEach(inspection::visit);
    tests.forEach(inspection::hear);
    return inspection.findings();
  }

  /**
   * Whether the findings this rule has when it arrives are frozen into the baseline, and its new ones may be
   * exempted. A rule whose way out is a person's signature, not a baseline, says no.
   */
  default boolean baselined() {
    return true;
  }

  /**
   * Whether a finding at this spot is a frozen one at that spot grown smaller, so it stays frozen: what a rule whose
   * finding names a whole group (a package cycle) says when the group lost members. Plain findings never narrow.
   */
  default boolean narrows(String frozenSpot, String spot) {
    return false;
  }

  /** The rule as a budget rule when it measures sizes against ceilings; empty for a plain rule. */
  default Optional<BudgetRule> asBudget() {
    return Optional.empty();
  }
}
