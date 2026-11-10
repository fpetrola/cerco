package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.List;

/** A switch written as if / else if / else: the cases are still enumerated at the caller. */
public final class ConditionalChain extends DecisionRule {
  private static final int BRANCHES = 3;

  @Override
  public String id() {
    return "no-conditional-chain";
  }

  @Override
  public String principle() {
    return "polymorphism: a set of cases is a set of types, not a ladder of conditions";
  }

  @Override
  public String description() {
    return "an if / else if chain of " + BRANCHES + " or more branches is a switch without the keyword; every new "
        + "case has to be remembered here, and the chain grows at every such place";
  }

  @Override
  public String remedy() {
    return "give each case an object that knows its own answer and let the caller ask one of them, or keep two "
        + "branches at most: a guard clause and the one thing the method does";
  }

  @Override
  public Diagnosis diagnosis() {
    List<Hypothesis> hypotheses = new ArrayList<>(super.diagnosis().hypotheses());
    hypotheses.add(new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT, "the arms test whether something is there",
        "a null object, or a mandatory collaborator"));
    return new Diagnosis(hypotheses, super.diagnosis().notAFix());
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(IfStmt.class).stream()
        .filter(this::isChainHead)
        .filter(head -> branches(head) >= BRANCHES)
        .map(head -> finding(file, head, enclosing.of(head) + "|if-chain",
            branches(head) + " branches in " + enclosing.of(head)))
        .toList();
  }

  private boolean isChainHead(IfStmt statement) {
    return enclosing.parentOf(statement, IfStmt.class)
        .map(parent -> parent.getElseStmt().filter(otherwise -> otherwise == statement).isEmpty())
        .orElse(true);
  }

  private int branches(IfStmt statement) {
    int count = 1;
    Statement otherwise = statement.getElseStmt().orElse(null);
    while (otherwise != null) {
      count++;
      otherwise = otherwise.toIfStmt().flatMap(IfStmt::getElseStmt).orElse(null);
    }
    return count;
  }
}
