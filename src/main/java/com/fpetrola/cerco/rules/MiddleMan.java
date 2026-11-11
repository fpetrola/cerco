package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class of three or more methods that each do nothing but pass the call on to one same field: it stands between
 * its callers and the object that does the work. Candidate from the plan of model smells (middle man).
 */
public final class MiddleMan extends FileRule {
  private static final int METHODS = 3;

  @Override
  public String id() {
    return "middle-man";
  }

  @Override
  public String principle() {
    return "a class earns its place by what it does, not by standing in front of another";
  }

  @Override
  public String description() {
    return "every method of a class, " + METHODS + " or more of them, only passes the call on to one same field: "
        + "the class adds a name and a hop, and no behaviour";
  }

  @Override
  public String remedy() {
    return "let the callers hold the object that does the work; or give the class what would make it more than a "
        + "passage (a narrower interface, a decision, a state of its own)";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("renaming the passed-on methods, or adding one method with a body to break the count",
        new Hypothesis(MissingModel.HIERARCHY_WITHOUT_IS_A,
            "a class stands between its callers and the object that does the work",
            "the callers hold that object; or the class takes on behaviour of its own"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class,
        one -> !one.isInterface())) {
      List<Optional<String>> passed = type.getMethods().stream().map(this::passedOnTo).toList();
      Set<String> to = passed.stream().flatMap(Optional::stream).collect(Collectors.toSet());
      if (passed.size() >= METHODS && passed.stream().allMatch(Optional::isPresent) && to.size() == 1) {
        findings.add(finding(file, type, enclosing.nameOf(type), enclosing.nameOf(type) + " only passes its "
            + passed.size() + " methods on to `" + to.iterator().next() + "`"));
      }
    }
    return findings;
  }

  /** The field a method passes its call on to, when that is all it does. */
  private Optional<String> passedOnTo(MethodDeclaration method) {
    Optional<Statement> only = method.getBody().filter(body -> body.getStatements().size() == 1)
        .map(body -> body.getStatement(0));
    Optional<Expression> done = only.flatMap(Statement::toExpressionStmt).map(told -> told.getExpression())
        .or(() -> only.flatMap(Statement::toReturnStmt).flatMap(returned -> returned.getExpression()));
    return done.flatMap(Expression::toMethodCallExpr).flatMap(MethodCallExpr::getScope)
        .filter(scope -> scope.isNameExpr() || scope.isFieldAccessExpr()).map(Expression::toString)
        .map(text -> text.replaceFirst("^this\\.", ""));
  }
}
