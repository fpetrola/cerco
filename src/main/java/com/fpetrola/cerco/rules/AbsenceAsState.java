package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A field of a class asked whether it is there in two or more of the class's methods: the object has two lives, with
 * and without it, and every method has to find out which one it is in. Candidate born from the plan of model smells:
 * the indicator gave 0 in oozx's emulator and 14 in the modules written by an agent.
 */
public final class AbsenceAsState extends FileRule {
  private static final int METHODS = 2;
  private static final Set<String> NULL_QUESTIONS = Set.of("isNull", "nonNull", "ofNullable");

  @Override
  public String id() {
    return "absence-as-state";
  }

  @Override
  public String principle() {
    return "an object is whole: a collaborator it may lack is a state it is in, and a state is an object";
  }

  @Override
  public String description() {
    return "a field compared with null in " + METHODS + " or more methods of its class: the object lives two lives, "
        + "with and without it, and each method asks which one it is in";
  }

  @Override
  public String remedy() {
    return "decide what the absence is: a collaborator present once the object is built goes in the constructor; an "
        + "optional one becomes a null object doing the empty thing; a phase of the object becomes its own object";
  }

  private final Hypothesis mandatory = new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
      "the field is always set once the object is in use",
      "make it a constructor argument, or build the object only when it can have it");
  private final Hypothesis optional = new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
      "the collaborator is truly optional",
      "a null object that does the empty thing, so no method asks");
  private final Hypothesis phases = new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
      "the object goes through phases (before and after something arrives)",
      "an object per phase, or two classes, the second built from the first");

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("an Optional tested with isPresent or ifPresent in the same methods, or a hasX() wrapping the "
        + "comparison: the same two lives", mandatory, optional, phases);
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      Map<VariableDeclarator, Set<String>> asked = new LinkedHashMap<>();
      type.getFields().stream().filter(field -> !field.isStatic()).map(FieldDeclaration::getVariables)
          .forEach(variables -> variables.forEach(variable -> asked.put(variable, new TreeSet<>())));
      Map<String, VariableDeclarator> byName = asked.keySet().stream()
          .collect(Collectors.toMap(VariableDeclarator::getNameAsString, variable -> variable));
      for (MethodDeclaration method : type.getMethods()) {
        questioned(method).stream().map(byName::get).filter(Objects::nonNull)
            .forEach(variable -> asked.get(variable).add(method.getNameAsString() + "()"));
      }
      asked.forEach((field, methods) -> {
        if (methods.size() >= METHODS) {
          String where = enclosing.nameOf(type) + "." + field.getNameAsString();
          Finding finding = finding(file, field, where, "`" + field.getNameAsString() + "` is compared with null "
              + "in " + methods.size() + " methods of " + enclosing.nameOf(type) + ": " + String.join(", ", methods));
          findings.add(arrival(type, field.getNameAsString()).map(finding::read).orElse(finding));
        }
      });
    }
    return findings;
  }

  /**
   * How the field comes to be there, which tells its absence apart: set back to null somewhere, it comes and goes;
   * given only when the object is built, it is optional; given later by a method and kept, it arrives once.
   */
  private Optional<Reading> arrival(TypeDeclaration<?> type, String field) {
    List<AssignExpr> given = type.findAll(AssignExpr.class, assignment -> assignment.getTarget().toString()
        .replaceFirst("^this\\.", "").equals(field));
    List<String> clearing = given.stream().filter(assignment -> assignment.getValue().isNullLiteralExpr())
        .map(enclosing::of).toList();
    List<String> later = given.stream().filter(assignment -> assignment.findAncestor(MethodDeclaration.class)
        .isPresent()).map(enclosing::of).toList();
    if (!clearing.isEmpty()) {
      return Optional.of(phases.because("`" + field + "` is given and taken away again (set to null in "
          + clearing.get(0) + ")"));
    }
    if (!later.isEmpty()) {
      return Optional.of(mandatory.because("`" + field + "` arrives after construction, in " + later.get(0)));
    }
    return Optional.of(optional.because("`" + field + "` is only given when the object is built: null there means "
        + "it has none")).filter(reading -> !given.isEmpty());
  }

  /** The names this method asks about being null, its own parameters and locals left out. */
  private Set<String> questioned(MethodDeclaration method) {
    Set<String> own = Stream.concat(method.getParameters().stream().map(Parameter::getNameAsString),
        method.findAll(VariableDeclarator.class).stream().map(VariableDeclarator::getNameAsString))
        .collect(Collectors.toSet());
    Stream<Expression> compared = method.findAll(BinaryExpr.class, this::comparesWithNull).stream()
        .map(test -> test.getLeft().isNullLiteralExpr() ? test.getRight() : test.getLeft());
    Stream<Expression> passed = method.findAll(MethodCallExpr.class,
            call -> NULL_QUESTIONS.contains(call.getNameAsString()) && call.getArguments().size() == 1).stream()
        .map(call -> call.getArgument(0));
    return Stream.concat(compared, passed).map(expression -> fieldName(expression, own)).flatMap(Optional::stream)
        .collect(Collectors.toSet());
  }

  private boolean comparesWithNull(BinaryExpr test) {
    boolean equality = test.getOperator() == BinaryExpr.Operator.EQUALS
        || test.getOperator() == BinaryExpr.Operator.NOT_EQUALS;
    return equality && (test.getLeft().isNullLiteralExpr() || test.getRight().isNullLiteralExpr());
  }

  /** `field` or `this.field`; a bare name is only a field when the method declares nothing by that name. */
  private Optional<String> fieldName(Expression expression, Set<String> own) {
    if (expression.isFieldAccessExpr() && expression.asFieldAccessExpr().getScope().isThisExpr()) {
      return Optional.of(expression.asFieldAccessExpr().getNameAsString());
    }
    return Optional.of(expression).filter(Expression::isNameExpr).map(name -> name.asNameExpr().getNameAsString())
        .filter(name -> !own.contains(name));
  }
}
