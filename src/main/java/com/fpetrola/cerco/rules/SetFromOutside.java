package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A method that calls three or more setters, declared in the project, on one other object: it assembles the object
 * from outside, field by field. Candidate born from the plan of model smells: 0 in oozx's emulator, 9 in machine.
 */
public final class SetFromOutside extends ProjectRule<SetFromOutside.Gathered> {
  private static final int SETTERS = 3;

  /** The setters one member calls on one receiver, and how many of their arguments are getters of one same object. */
  public record Site(String file, int line, String member, String receiver, Set<String> setters, int copied,
      String source) {
  }

  /** The project's names, and the candidate sites. */
  public record Gathered(Vocabulary vocabulary, List<Site> sites) {
  }

  @Override
  public String id() {
    return "set-from-outside";
  }

  @Override
  public String principle() {
    return "encapsulation: an object is built or changed by its own operations, not field by field from outside";
  }

  @Override
  public String description() {
    return "a method calls " + SETTERS + " or more of the project's setters on one other object: it assembles that "
        + "object from outside, and every such method must know all its parts";
  }

  @Override
  public String remedy() {
    return "give the object the operation (a constructor, or a method taking what the setters were given); when the "
        + "values come from another object, the target takes from the source, or they are one structure";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a builder that calls the same setters, or the setters moved to a helper: the object is "
        + "still assembled from outside",
        new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
            "the setters together do one thing to the object",
            "a method of the object that does it"),
        new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
            "the values set travel together elsewhere too",
            "the class they form, given to the object whole"),
        new Hypothesis(MissingModel.KNOWLEDGE_WITHOUT_ONE_OWNER,
            "each argument is a getter of one same other object (a copy property by property)",
            "the target takes from the source in one call, or both are one structure and the copy goes away"));
  }

  @Override
  protected Gathered start() {
    return new Gathered(new Vocabulary(), new ArrayList<>());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Gathered gathered) {
    gathered.vocabulary().learn(unit);
    for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
      Map<String, List<MethodCallExpr>> byReceiver = callable.findAll(MethodCallExpr.class, this::isSetter).stream()
          .filter(call -> call.findAncestor(CallableDeclaration.class).orElse(null) == callable)
          .collect(Collectors.groupingBy(call -> receiver(call).orElse(""), TreeMap::new, Collectors.toList()));
      byReceiver.forEach((receiver, calls) -> {
        Map<String, Long> sources = calls.stream().map(call -> call.getArgument(0)).filter(this::isGetterCall)
            .collect(Collectors.groupingBy(argument -> argument.asMethodCallExpr().getScope().orElseThrow()
                .toString(), Collectors.counting()));
        Map.Entry<String, Long> source = sources.entrySet().stream().max(Map.Entry.comparingByValue())
            .orElse(Map.entry("", 0L));
        gathered.sites().add(new Site(file.name(), line(calls.get(0)), enclosing.signatureOf(callable), receiver,
            calls.stream().map(MethodCallExpr::getNameAsString).collect(Collectors.toCollection(TreeSet::new)),
            source.getValue().intValue(), source.getKey()));
      });
    }
  }

  @Override
  protected List<Finding> conclude(Gathered gathered, Project project) {
    List<Finding> findings = new ArrayList<>();
    for (Site site : gathered.sites()) {
      Set<String> ours = site.setters().stream().filter(gathered.vocabulary()::declaresMethod)
          .collect(Collectors.toCollection(TreeSet::new));
      if (!site.receiver().isEmpty() && ours.size() >= SETTERS) {
        String copy = site.copied() >= SETTERS ? "; " + site.copied() + " of the values are getters of "
            + site.source() + ", a copy property by property" : "";
        findings.add(new Finding(id(), site.file(), site.line(), site.file() + "|" + site.member() + "|"
            + site.receiver(), site.member() + " sets " + ours.size() + " properties of `" + clipped(site.receiver())
            + "`: " + String.join(", ", ours.stream().limit(6).toList()) + (ours.size() > 6 ? ", ..." : "")
            + copy));
      }
    }
    return findings;
  }

  private boolean isSetter(MethodCallExpr call) {
    return call.getNameAsString().matches("set[A-Z].*") && call.getArguments().size() == 1
        && receiver(call).isPresent();
  }

  private boolean isGetterCall(Expression expression) {
    return expression.isMethodCallExpr() && expression.asMethodCallExpr().getArguments().isEmpty()
        && expression.asMethodCallExpr().getNameAsString().matches("(get|is)[A-Z].*")
        && expression.asMethodCallExpr().getScope().filter(scope -> !scope.isThisExpr()).isPresent();
  }

  private java.util.Optional<String> receiver(MethodCallExpr call) {
    return call.getScope().filter(scope -> !scope.isThisExpr() && !scope.isSuperExpr()).map(Expression::toString);
  }

}
