package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A private field nobody reads, or a private method nobody calls: what is private can only be used from its own
 * file, so the file alone says it is dead. Candidate born from the autopsies of oozx (eleven fields written and
 * never read). Public members without callers need what is outside the reactor, and are not looked at.
 */
public final class UnusedMember extends FileRule {
  private static final Set<String> CALLED_BY_THE_PLATFORM = Set.of("readObject", "writeObject", "readResolve",
      "writeReplace", "readObjectNoData", "serialVersionUID");

  @Override
  public String id() {
    return "unused-member";
  }

  @Override
  public String principle() {
    return "what the code keeps, it uses: a member nobody uses says something the model does not do";
  }

  @Override
  public String description() {
    return "a private field that is written and never read, or a private method nobody calls: it can only be used "
        + "from its own file, and nothing there does";
  }

  @Override
  public String remedy() {
    return "delete it, with the code that only fed it; if it was meant to be used, the use is what is missing";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    unit.findAll(FieldDeclaration.class, field -> field.isPrivate() && field.getAnnotations().isEmpty()).stream()
        .flatMap(field -> field.getVariables().stream()).filter(field -> !isRead(unit, field) && !keepsAnEffect(field))
        .forEach(field -> findings.add(finding(file, field, enclosing.typeOf(field) + "." + field.getNameAsString(),
            "nobody reads private field " + enclosing.typeOf(field) + "." + field.getNameAsString())));
    unit.findAll(MethodDeclaration.class, method -> method.isPrivate() && method.getAnnotations().isEmpty()
            && !isCalled(unit, method))
        .forEach(method -> findings.add(finding(file, method, enclosing.signatureOf(method),
            "nobody calls private method " + enclosing.signatureOf(method))));
    return findings;
  }

  /** A field set from a call may be there for what the call does (a registration it keeps open), not to be read. */
  private boolean keepsAnEffect(VariableDeclarator field) {
    return field.getInitializer().filter(Expression::isMethodCallExpr).isPresent();
  }

  private boolean isRead(CompilationUnit unit, VariableDeclarator field) {
    String name = field.getNameAsString();
    Stream<Expression> uses = Stream.concat(
        unit.findAll(NameExpr.class, use -> use.getNameAsString().equals(name)).stream(),
        unit.findAll(FieldAccessExpr.class, use -> use.getNameAsString().equals(name)).stream());
    return CALLED_BY_THE_PLATFORM.contains(name) || uses.anyMatch(use -> !isOnlyWritten(use));
  }

  /** The left side of a plain `=`: the value goes in and nothing comes out. */
  private boolean isOnlyWritten(Expression use) {
    return enclosing.parentOf(use, AssignExpr.class)
        .filter(assign -> assign.getTarget() == use && assign.getOperator() == AssignExpr.Operator.ASSIGN).isPresent();
  }

  /** Called from somewhere other than itself, or handed over as a reference. */
  private boolean isCalled(CompilationUnit unit, MethodDeclaration method) {
    String name = method.getNameAsString();
    return CALLED_BY_THE_PLATFORM.contains(name)
        || !unit.findAll(MethodReferenceExpr.class, reference -> reference.getIdentifier().equals(name)).isEmpty()
        || unit.findAll(MethodCallExpr.class, call -> call.getNameAsString().equals(name)).stream()
            .anyMatch(call -> call.findAncestor(MethodDeclaration.class).filter(method::equals).isEmpty());
  }
}
