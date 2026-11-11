package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A member nobody uses: a private field nobody reads, a private method nobody calls, or a method that is not private
 * and that nothing in the project names, its tests included. What is private is judged by its file alone. What is
 * not is dead when neither main nor a test calls a method by that name: a consumer outside the project counts only
 * if a test uses the method the way the consumer does. Candidate born from the autopsies of oozx.
 */
public final class UnusedMember extends ProjectRule<UnusedMember.Seen> {
  private static final Set<String> CALLED_BY_THE_PLATFORM = Set.of("readObject", "writeObject", "readResolve",
      "writeReplace", "readObjectNoData", "serialVersionUID", "main");

  /** A method that is not private, where it is declared. */
  public record Site(String file, int line, String member, String called) implements Place {
  }

  /** What is already known dead, the methods open to others, and every method name the project calls or hands over. */
  public record Seen(List<Finding> dead, List<Site> open, Set<String> named) {

    Stream<Site> unnamed() {
      return open.stream().filter(site -> !named.contains(site.called()));
    }
  }

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
    return "a private field that is written and never read, a private method nobody calls, or a method that is not "
        + "private and that neither main nor a test of the project calls";
  }

  @Override
  public String remedy() {
    return "delete it, with the code that only fed it; when a consumer outside the project uses it, a test that "
        + "uses it the same way keeps it alive and says why it is there";
  }

  @Override
  protected Seen start() {
    return new Seen(new ArrayList<>(), new ArrayList<>(), new HashSet<>());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Seen seen) {
    unit.findAll(FieldDeclaration.class, field -> field.isPrivate() && field.getAnnotations().isEmpty()).stream()
        .flatMap(field -> field.getVariables().stream())
        .filter(field -> !isRead(unit, field) && !keepsAnEffect(field))
        .forEach(field -> seen.dead().add(finding(file, field, enclosing.typeOf(field) + "." + field.getNameAsString(),
            "nobody reads private field " + enclosing.typeOf(field) + "." + field.getNameAsString())));
    List<MethodDeclaration> plain = unit.findAll(MethodDeclaration.class, method -> method.getAnnotations().isEmpty()
        && !CALLED_BY_THE_PLATFORM.contains(method.getNameAsString()));
    plain.stream().filter(method -> method.isPrivate() && !isCalled(unit, method))
        .forEach(method -> seen.dead().add(finding(file, method, enclosing.signatureOf(method),
            "nobody calls private method " + enclosing.signatureOf(method))));
    plain.stream().filter(method -> !method.isPrivate() && method.getBody().isPresent())
        .forEach(method -> seen.open().add(new Site(file.name(), line(method), enclosing.signatureOf(method),
            method.getNameAsString())));
    overhear(unit, seen);
  }

  @Override
  protected void overhear(CompilationUnit unit, Seen seen) {
    unit.findAll(MethodCallExpr.class).forEach(call -> seen.named().add(call.getNameAsString()));
    unit.findAll(MethodReferenceExpr.class).forEach(reference -> seen.named().add(reference.getIdentifier()));
  }

  @Override
  protected List<Finding> conclude(Seen seen, Project project) {
    return Stream.concat(seen.dead().stream(), seen.unnamed().map(site -> new Finding(id(), site.file(), site.line(),
        site.file() + "|" + site.member(), "nothing in main or in the tests calls " + site.member()
            + Place.FOR_OUTSIDERS))).toList();
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
    return !unit.findAll(MethodReferenceExpr.class, reference -> reference.getIdentifier().equals(name)).isEmpty()
        || unit.findAll(MethodCallExpr.class, call -> call.getNameAsString().equals(name)).stream()
            .anyMatch(call -> call.findAncestor(MethodDeclaration.class).filter(method::equals).isEmpty());
  }
}
