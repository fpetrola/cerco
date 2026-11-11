package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A method that is not private and answers with a collection its object keeps in a field, the very one: whoever
 * asks can walk it and change it, and the object no longer knows what it holds. It is where walked-from-outside
 * starts. Candidate from the plan of model smells (leaky encapsulation).
 */
public final class HandedOutWhole extends FileRule {

  @Override
  public String id() {
    return "handed-out-whole";
  }

  @Override
  public String principle() {
    return "an object answers questions about what it holds; it does not hand its insides over";
  }

  @Override
  public String description() {
    return "a method that is not private returns a collection the object keeps in a field, itself and not a view: "
        + "every client can walk it and change it behind the owner's back";
  }

  @Override
  public String remedy() {
    return "give the owner the operations its clients perform on the collection (a query with a name, an action "
        + "for each element, adding and removing); what is still handed out is a view nobody can change";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a copy or an unmodifiable wrapper returned by the same getter, with every walk still "
        + "outside",
        new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
            "the object keeps a collection and its clients work on it directly",
            "the operations the clients need, on the owner"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      Map<String, String> kept = type.getFields().stream().filter(field -> !field.isStatic())
          .flatMap(field -> field.getVariables().stream()).filter(this::isACollection)
          .collect(Collectors.toMap(VariableDeclarator::getNameAsString, VariableDeclarator::getTypeAsString,
              (first, second) -> first));
      for (MethodDeclaration method : type.getMethods()) {
        handedOut(method).filter(kept::containsKey).filter(field -> !method.isPrivate())
            .ifPresent(field -> findings.add(finding(file, method, enclosing.signatureOf(method) + "|" + field,
                enclosing.signatureOf(method) + " hands out `" + field + "`, the " + clipped(kept.get(field))
                    + " its object keeps")));
      }
    }
    return findings;
  }

  private boolean isACollection(VariableDeclarator field) {
    return field.getType().toClassOrInterfaceType()
        .filter(type -> type.getNameAsString().matches(".*(List|Set|Map|Collection|Deque|Queue)")).isPresent();
  }

  /** The field a method answers with, when answering with it is all the method does. */
  private Optional<String> handedOut(MethodDeclaration method) {
    return method.getBody().filter(body -> body.getStatements().size() == 1)
        .flatMap(body -> body.getStatement(0).toReturnStmt()).flatMap(ReturnStmt::getExpression)
        .map(Expression::toString).map(text -> text.replaceFirst("^this\\.", ""));
  }
}
