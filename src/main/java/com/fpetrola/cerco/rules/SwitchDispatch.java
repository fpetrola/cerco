package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.SwitchStmt;
import java.util.ArrayList;
import java.util.List;

/** A switch: the cases of a concept written out at the call site instead of living with the concept. */
public final class SwitchDispatch extends DecisionRule {

  @Override
  public String id() {
    return "no-switch";
  }

  @Override
  public String principle() {
    return "polymorphism: each case is a type that knows its own behaviour";
  }

  @Override
  public String description() {
    return "a switch over an enum, a type or a code enumerates the cases where it is used; the same enumeration tends "
        + "to reappear at every use and each new case has to find them all";
  }

  @Override
  public String remedy() {
    return "give the enum or the hierarchy a method and let each case implement it, or map each case to the object "
        + "that handles it; a decoder at the edge of the system that must be a table is a human's exemption";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> out = new ArrayList<>();
    for (SwitchStmt statement : unit.findAll(SwitchStmt.class)) {
      String where = enclosing.of(statement);
      String selector = clipped(statement.getSelector().toString());
      out.add(finding(file, statement, where + "|switch(" + selector + ")",
          "switch over `" + selector + "` with " + statement.getEntries().size() + " entries in " + where));
    }
    for (SwitchExpr expression : unit.findAll(SwitchExpr.class)) {
      String where = enclosing.of(expression);
      String selector = clipped(expression.getSelector().toString());
      out.add(finding(file, expression, where + "|switch(" + selector + ")",
          "switch expression over `" + selector + "` with " + expression.getEntries().size() + " entries in " + where));
    }
    return out;
  }
}
