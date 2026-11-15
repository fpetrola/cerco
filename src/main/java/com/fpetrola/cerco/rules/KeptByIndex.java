package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A type that works by position: its methods take an int they use to index a collection the type keeps, or check it against the
 * size of that collection to keep it, or answer a
 * position (an OptionalInt, an indexOf) for the caller to keep and hand back. The element at that position has
 * behaviour of its own and is missing as an object. An int that is a value (a key's number) indexes nothing the type
 * keeps, and is left alone.
 */
public final class KeptByIndex extends FoundInPlaceRule {
  private final IntType intType = new IntType();
  private static final Pattern MANY = Pattern.compile(".*\\[\\]|(java\\.util\\.)?(List|ArrayList|LinkedList)(<.*>)?");
  private static final Set<String> BY_POSITION = Set.of("get", "set", "remove", "subList", "listIterator");

  @Override
  public String id() {
    return "kept-by-index";
  }

  @Override
  public String principle() {
    return "tell, don't ask: an element with behaviour of its own is an object the collection hands out or walks, "
        + "never a position others keep and pass back";
  }

  @Override
  public String description() {
    return "a non-private method that takes an int and indexes with it a collection the type keeps, or that answers "
        + "a position (OptionalInt, an indexOf) for the caller to hand back";
  }

  @Override
  public String remedy() {
    return "callers keep a position and hand it back: the element at that position is missing as an object; give it "
        + "the behaviour and hand it out (or walk the elements with a visitor)";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("the same type renamed, or the index wrapped in a record that only carries the int: the "
        + "position still travels", new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
            "callers keep ints and pass them back to the type that gave them",
            "an object per element, with what the index used to look up"));
  }

  @Override
  protected List<Finding> sites(ParsedFile file, CompilationUnit unit) {
    List<Finding> found = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      Set<String> kept = type.getFields().stream().filter(this::many)
          .flatMap(field -> field.getVariables().stream()).map(variable -> variable.getNameAsString())
          .collect(Collectors.toSet());
      List<String> methods = type.getMethods().stream().filter(method -> !method.isPrivate())
          .filter(method -> indexes(method, kept) || answersAPosition(method))
          .map(method -> method.getNameAsString() + "(" + method.getParameters().stream()
              .map(parameter -> parameter.getType().asString()).collect(Collectors.joining(", ")) + ")").toList();
      if (!methods.isEmpty()) {
        found.add(finding(file, type, type.getNameAsString(), type.getNameAsString() + " works by position: "
            + String.join(", ", methods) + ". Callers keep a position and hand it back: the element at that "
            + "position is missing as an object; give it the behaviour and hand it out"));
      }
    }
    return found;
  }

  private boolean many(FieldDeclaration field) {
    return field.getVariables().stream().anyMatch(variable -> MANY.matcher(variable.getType().asString()).matches());
  }

  /** An int parameter used as the index into a collection the type keeps: kept.get(i), kept[i]. */
  private boolean indexes(MethodDeclaration method, Set<String> kept) {
    Set<String> ints = method.getParameters().stream().filter(parameter -> intType.writes(parameter.getType())).map(Parameter::getNameAsString).collect(Collectors.toSet());
    if (ints.isEmpty() || kept.isEmpty()) {
      return false;
    }
    boolean byCall = method.findAll(MethodCallExpr.class, call -> BY_POSITION.contains(call.getNameAsString())
        && call.getScope().filter(scope -> kept.contains(named(scope))).isPresent()
        && call.getArguments().stream().anyMatch(argument -> mentions(argument, ints))).size() > 0;
    boolean byBracket = method.findAll(ArrayAccessExpr.class, access -> kept.contains(named(access.getName()))
        && mentions(access.getIndex(), ints)).size() > 0;
    return byCall || byBracket || bounded(method, kept, ints);
  }

  /** An int parameter checked against the size of a collection the type keeps: a position into it, kept for later. */
  private boolean bounded(MethodDeclaration method, Set<String> kept, Set<String> ints) {
    return method.findAll(com.github.javaparser.ast.expr.BinaryExpr.class, comparison -> mentions(comparison, ints)
        && (comparison.findAll(MethodCallExpr.class, call -> call.getNameAsString().equals("size")
            && call.getScope().filter(scope -> kept.contains(named(scope))).isPresent()).size() > 0
            || comparison.findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class, access -> access
                .getNameAsString().equals("length") && kept.contains(named(access.getScope()))).size() > 0))
        .size() > 0;
  }

  /** A method that answers where something is: an OptionalInt, or an int it gets from indexOf. */
  private boolean answersAPosition(MethodDeclaration method) {
    return method.getType().asString().equals("OptionalInt")
        || intType.writes(method.getType()) && method.findAll(MethodCallExpr.class,
        call -> call.getNameAsString().equals("indexOf")).size() > 0;
  }

  private boolean mentions(Expression expression, Set<String> names) {
    return expression.findAll(NameExpr.class, name -> names.contains(name.getNameAsString())).size() > 0;
  }

  /** The name a field is reached by: `kept` or `this.kept`. */
  private String named(Expression expression) {
    if (expression.isFieldAccessExpr() && expression.asFieldAccessExpr().getScope().isThisExpr()) {
      return expression.asFieldAccessExpr().getNameAsString();
    }
    return expression.isNameExpr() ? expression.asNameExpr().getNameAsString() : "";
  }
}
