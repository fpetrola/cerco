package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.List;

/**
 * A visitor that drives the walk: inside visitX(X x) it calls x.accept...(...), choosing which parts of x to go
 * through, so it knows how x is built. A candidate: a visitor that descends on purpose (enter/leave, a visitor that
 * prunes) does the same, and has to be told apart by measuring.
 */
public final class WalkedByTheVisitor extends FoundInPlaceRule {

  @Override
  public String id() {
    return "walked-by-the-visitor";
  }

  @Override
  public String principle() {
    return "only the element knows its structure; the visitor is told what there is, it does not go looking";
  }

  @Override
  public String description() {
    return "a visit method that has the visited object accept a visitor again: the visitor picks which parts to walk";
  }

  @Override
  public String remedy() {
    return "let the element's one accept walk its parts; the visitor does nothing for the parts it does not need";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a getter of the parts called from the visitor: the same walk, done by hand",
        new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
            "a visitor reaches into what it visits to go on",
            "the element's accept walks its parts and every visitor receives them"));
  }

  @Override
  protected List<Finding> sites(ParsedFile file, CompilationUnit unit) {
    List<Finding> found = new ArrayList<>();
    for (MethodDeclaration method : unit.findAll(MethodDeclaration.class, this::visits)) {
      for (Parameter visited : method.getParameters()) {
        method.findAll(MethodCallExpr.class, call -> call.getNameAsString().startsWith("accept") && call.getScope()
            .filter(scope -> scope.isNameExpr() && scope.asNameExpr().getNameAsString()
                .equals(visited.getNameAsString())).isPresent())
            .forEach(call -> found.add(finding(file, call, method.getNameAsString() + "|" + clipped(call.toString()),
                "`" + clipped(call.toString()) + "` in " + method.getNameAsString() + ": the visitor walks "
                    + visited.getNameAsString() + " instead of being walked")));
      }
    }
    return found;
  }

  /** A visit method: named visit-something, or in a type whose name, or whose supertype's, ends in Visitor. */
  private boolean visits(MethodDeclaration method) {
    boolean inAVisitor = method.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
        .filter(type -> type.getNameAsString().endsWith("Visitor") || type.getImplementedTypes().stream()
            .anyMatch(supertype -> supertype.getNameAsString().endsWith("Visitor"))).isPresent();
    return method.getNameAsString().startsWith("visit") || inAVisitor;
  }
}
