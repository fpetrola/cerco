package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;
import java.util.List;

/** A catch that neither rethrows nor looks at the exception: a refusal dropped on the floor. */
public final class SwallowedException extends FileRule {

  @Override
  public String id() {
    return "no-swallowed-exception";
  }

  @Override
  public String principle() {
    return "honesty: a failure is answered or passed on, never hidden";
  }

  @Override
  public String description() {
    return "a catch block that does not rethrow and never uses the exception turns a failure into silence; the "
        + "program goes on in a state nobody chose";
  }

  @Override
  public String remedy() {
    return "rethrow it wrapped in the exception your layer speaks, or handle it for real using what it says; if "
        + "the failure truly does not matter, say so in a return value the caller can see";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(CatchClause.class).stream()
        .filter(this::swallows)
        .map(clause -> finding(file, clause,
            enclosing.of(clause) + "|catch(" + clause.getParameter().getTypeAsString() + ")",
            "catch (" + clause.getParameter() + ") neither rethrows nor uses the exception in "
                + enclosing.of(clause)))
        .toList();
  }

  private boolean swallows(CatchClause clause) {
    BlockStmt body = clause.getBody();
    String name = clause.getParameter().getNameAsString();
    boolean rethrows = !body.findAll(ThrowStmt.class).isEmpty();
    boolean uses = body.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(name));
    boolean restoresInterrupt = body.findAll(MethodCallExpr.class).stream()
        .anyMatch(call -> call.getNameAsString().equals("interrupt"));
    return !rethrows && !uses && !restoresInterrupt;
  }
}
