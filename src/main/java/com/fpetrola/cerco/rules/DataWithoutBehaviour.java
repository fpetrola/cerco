package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Chain;
import com.fpetrola.cerco.project.Declared;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Names;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A member that asks three or more things of an object of the project that only holds data: the data is in one
 * class and what is done with it in another. It names the pair, not the data class alone, which may be a fair
 * record. Candidate from the plan of model smells (data class and feature envy, seen together).
 */
public final class DataWithoutBehaviour extends PlacesRule<DataWithoutBehaviour.Site> {
  private static final int ASKED = 3;
  private static final int FIELDS = 3;

  /** One member questioning one object: where, the object, how it is reached, and the getters it calls on it. */
  public record Site(String file, int line, String member, String asked, Optional<Chain> reached, Set<String> getters)
      implements Place {
  }

  @Override
  public String id() {
    return "data-without-behaviour";
  }

  @Override
  public String principle() {
    return "data and what is done with it live together: an object is asked to do, not asked what it has";
  }

  @Override
  public String description() {
    return "a member asks " + ASKED + " or more things of an object of the project that has only fields and "
        + "accessors: the work on that data is written outside the class that holds it";
  }

  @Override
  public String remedy() {
    return "move what the member does with those values into the class that holds them, as a method named for it";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a static helper that takes the data object, or the getters read into locals first",
        new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
            "one class holds the data and another does the work on it",
            "the work becomes a method of the class that holds the data"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    Names names = new Names(new Imports(unit));
    for (CallableDeclaration<?> member : unit.findAll(CallableDeclaration.class)) {
      Node body = member;
      Map<String, List<MethodCallExpr>> byObject = body.findAll(MethodCallExpr.class, this::asks).stream()
          .collect(Collectors.groupingBy(call -> call.getScope().map(Expression::toString).orElse("")));
      byObject.forEach((object, calls) -> {
        Set<String> getters = calls.stream().map(MethodCallExpr::getNameAsString)
            .collect(Collectors.toCollection(TreeSet::new));
        if (getters.size() >= ASKED) {
          sites.add(new Site(file.name(), line(calls.get(0)), enclosing.signatureOf(member), object,
              calls.get(0).getScope().flatMap(names::chainOf), getters));
        }
      });
    }
  }

  /** A call with no arguments on a variable: a question put to an object. */
  private boolean asks(MethodCallExpr call) {
    return call.getArguments().isEmpty() && call.getScope().filter(Expression::isNameExpr).isPresent();
  }

  @Override
  protected List<Finding> conclude(List<Site> sites, Project project) {
    return sites.stream().flatMap(site -> site.reached().flatMap(project::declaredAt)
            .filter(this::onlyHolds).filter(data -> !site.member().startsWith(data.qualified() + "."))
            .map(data -> new Finding(id(), site.file(), site.line(), site.file() + "|" + site.member() + "|"
                + data.name(), site.member() + " asks `" + site.asked() + "`, a " + data.name()
                + " that only holds data, " + site.getters().size() + " things: "
                + clipped(String.join(", ", site.getters())))).stream())
        .toList();
  }

  /** A type with fields whose every method reads or writes one of them. */
  private boolean onlyHolds(Declared type) {
    return type.fields().size() >= FIELDS && !type.operations().isEmpty() && type.operations().stream()
        .allMatch(operation -> operation.name().matches("(get|is|set)[A-Z].*")
            || type.fields().containsKey(operation.name()));
  }
}
