package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Declared;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Names;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.project.TypeName;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A call handed three or more parts of one same object of the project, each asked for in the argument list: the
 * object is taken apart for a callee that works on what it is made of. Candidate from the plan of model smells.
 */
public final class UnpackedAtTheCall extends PlacesRule<UnpackedAtTheCall.Site> {
  private static final int PARTS = 3;

  /**
   * One call: where, in which member, what is called, the object taken apart with the type it was declared as when
   * the file shows it, and the parts asked of it, by name and as written.
   */
  public record Site(String file, int line, String member, String called, String owner, Optional<TypeName> type,
      List<String> parts, String written) implements Place {
  }

  @Override
  public String id() {
    return "unpacked-at-the-call";
  }

  @Override
  public String principle() {
    return "tell, don't ask: an object is handed whole, or does the work itself";
  }

  @Override
  public String description() {
    return "a call is handed " + PARTS + " or more parts of one same object of the project (`f(a.x(), a.y(), "
        + "a.z())`): the caller takes the object apart, and the callee works on what another object is made of";
  }

  @Override
  public String remedy() {
    return "hand over the object itself; or, when what is done with its parts is one thing, make it a method of "
        + "that object";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a record made on the spot from the same getters, or the getters moved into locals first",
        new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
            "the callee works on the parts of one object",
            "hand it the object, or make the operation a method of that object"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    Names names = new Names(new Imports(unit));
    unit.findAll(MethodCallExpr.class).forEach(call -> sites.addAll(
        unpacked(file, call, call.getNameAsString(), call.getArguments(), names)));
    unit.findAll(ObjectCreationExpr.class).forEach(made -> sites.addAll(
        unpacked(file, made, "new " + made.getType().getNameAsString(), made.getArguments(), names)));
  }

  private List<Site> unpacked(ParsedFile file, Node call, String called, NodeList<Expression> arguments,
      Names names) {
    Map<String, List<Expression>> byOwner = new TreeMap<>();
    arguments.forEach(argument -> whole(argument).filter(Expression::isNameExpr).ifPresent(owner ->
        byOwner.computeIfAbsent(owner.toString(), k -> new ArrayList<>()).add(argument)));
    List<Site> sites = new ArrayList<>();
    byOwner.forEach((owner, asked) -> {
      if (asked.size() >= PARTS) {
        sites.add(new Site(file.name(), line(call), enclosing.of(call), called, owner,
            asked.stream().findFirst().flatMap(this::whole).flatMap(names::typeOf),
            asked.stream().map(this::part).toList(),
            clipped(asked.stream().map(Expression::toString).reduce((a, b) -> a + ", " + b).orElse(""))));
      }
    });
    return sites;
  }

  /** What an argument takes a part of: `a` in `a.x()` and in `a.x`. */
  private Optional<Expression> whole(Expression argument) {
    return argument.toMethodCallExpr().filter(call -> call.getArguments().isEmpty())
        .flatMap(MethodCallExpr::getScope).or(() -> argument.toFieldAccessExpr().map(FieldAccessExpr::getScope));
  }

  /** The part an argument asks for, by name: `x` in `a.x()` and in `a.x`. */
  private String part(Expression argument) {
    return argument.toMethodCallExpr().map(MethodCallExpr::getNameAsString)
        .or(() -> argument.toFieldAccessExpr().map(FieldAccessExpr::getNameAsString)).orElse("");
  }

  /** An object of the project, asked for what it holds: its fields, or what a getter of its own answers. */
  @Override
  protected List<Finding> conclude(List<Site> sites, Project project) {
    return sites.stream().filter(site -> site.type().filter(project::declares).map(TypeName::simple)
            .flatMap(project::declared).filter(owner -> site.parts().stream().allMatch(part -> holds(owner, part)))
            .isPresent())
        .map(site -> new Finding(id(), site.file(), site.line(), site.file() + "|" + site.member() + "|"
            + site.called() + "|" + site.owner(), "`" + site.called() + "(...)` is handed " + site.parts().size()
            + " parts of `" + site.owner() + "`: " + site.written())).toList();
  }

  private boolean holds(Declared owner, String part) {
    return owner.field(part).isPresent() || part.matches("(get|is)[A-Z].*");
  }
}
