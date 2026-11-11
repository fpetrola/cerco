package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Three or more statements in a row that make the same call on different objects: everyone who has to hear of
 * something is told by hand, and whoever tells keeps the list of them in its code. Candidate from the plan of model
 * smells (Kerievsky's Replace Hard-Coded Notifications with Observer).
 */
public final class ToldOneByOne extends FileRule {
  private static final int TOLD = 3;

  @Override
  public String id() {
    return "told-one-by-one";
  }

  @Override
  public String principle() {
    return "whoever has something to say says it once; who listens is not written into the one who speaks";
  }

  @Override
  public String description() {
    return TOLD + " or more statements in a row make the same call on different objects: each one that has to "
        + "hear is named by hand, and a new one means finding every such place";
  }

  @Override
  public String remedy() {
    return "the objects told are a group: they register as listeners and are told in one call (an observer), or "
        + "they are parts of one thing that passes the call on (a composite)";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a private method holding the same row of calls, or an array literal walked on the spot",
        new Hypothesis(MissingModel.PROTOCOL_BY_CONVENTION,
            "the same thing is said to several objects, one statement each",
            "listeners that register and are told at once, or a composite that passes it on"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (BlockStmt block : unit.findAll(BlockStmt.class)) {
      List<Statement> statements = block.getStatements();
      int at = 0;
      while (at < statements.size()) {
        int run = run(statements, at);
        if (run >= TOLD) {
          MethodCallExpr first = told(statements.get(at)).orElseThrow();
          findings.add(finding(file, first, enclosing.of(first) + "|" + first.getNameAsString() + "()", run
              + " objects in a row are told `" + first.getNameAsString() + "()` in " + enclosing.of(first)));
        }
        at += Math.max(run, 1);
      }
    }
    return findings;
  }

  /** How many statements from there on make the same call as the first, each on another object. */
  private int run(List<Statement> statements, int from) {
    Optional<MethodCallExpr> first = told(statements.get(from));
    List<String> receivers = new ArrayList<>();
    int at = from;
    while (at < statements.size() && first.isPresent() && told(statements.get(at)).filter(next ->
        next.getNameAsString().equals(first.get().getNameAsString())
            && next.getArguments().size() == first.get().getArguments().size()
            && !receivers.contains(on(next))).isPresent()) {
      receivers.add(on(told(statements.get(at)).orElseThrow()));
      at++;
    }
    return at - from;
  }

  /** The call a statement is, when it is nothing but a call on a variable or a field. */
  private Optional<MethodCallExpr> told(Statement statement) {
    return statement.toExpressionStmt().flatMap(told -> told.getExpression().toMethodCallExpr())
        .filter(call -> call.getScope().filter(scope -> scope.isNameExpr() || scope.isFieldAccessExpr()).isPresent());
  }

  private String on(MethodCallExpr call) {
    return receiver(call).orElse("");
  }
}
