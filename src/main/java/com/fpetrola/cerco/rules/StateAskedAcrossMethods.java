package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A field its own class tests in three or more methods, as a flag or against named constants: the object behaves
 * in several ways and each method asks which one it is in. Changed along the way, the field is a state; fixed at
 * construction, a kind. Candidate from the plan of model smells (the state checking of JDeodorant, extended to flags).
 */
public final class StateAskedAcrossMethods extends FileRule {
  private static final int METHODS = 3;
  private final Compared compared = new Compared();
  private final Flags flags = new Flags();
  private final Hypothesis state = new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
      "the field changes while the object lives, and methods behave by it",
      "a state object per value, which the methods delegate to and which knows what comes next");
  private final Hypothesis kind = new Hypothesis(MissingModel.VARIATION_AS_DATA,
      "the field is fixed when the object is built, and methods behave by it",
      "a subclass or a strategy per value, chosen once where the object is made");

  @Override
  public String id() {
    return "state-asked-across-methods";
  }

  @Override
  public String principle() {
    return "an object that behaves in several ways is several objects: none of them asks which one it is";
  }

  @Override
  public String description() {
    return "a field is tested, as a flag or against named constants, in " + METHODS + " or more methods of its own "
        + "class: the class is several behaviours in one, and each method asks which is on";
  }

  @Override
  public String remedy() {
    return "when the field changes, a state object per value the methods delegate to; when it is fixed at "
        + "construction, a subclass or a strategy per value";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("an enum in place of the boolean with the same tests, or the tests gathered in one private "
        + "method every other calls", state, kind);
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      Set<String> fields = type.getFields().stream().filter(field -> !field.isStatic())
          .flatMap(field -> field.getVariables().stream()).map(VariableDeclarator::getNameAsString)
          .collect(Collectors.toSet());
      Map<String, Set<String>> asking = new TreeMap<>();
      for (MethodDeclaration method : type.getMethods()) {
        tested(method).stream().filter(fields::contains).forEach(field ->
            asking.computeIfAbsent(field, k -> new TreeSet<>()).add(method.getNameAsString() + "()"));
      }
      asking.forEach((field, methods) -> {
        if (methods.size() >= METHODS) {
          findings.add(finding(file, type, enclosing.nameOf(type) + "." + field, "`" + field + "` is tested in "
              + methods.size() + " methods of " + enclosing.nameOf(type) + ": " + clipped(String.join(", ", methods)))
              .read(read(type, field, methods.size())));
        }
      });
    }
    return findings;
  }

  /** A field some method assigns is a state on the move; one only its construction sets is a kind. */
  private Reading read(TypeDeclaration<?> type, String field, int methods) {
    Optional<String> changed = type.getMethods().stream().filter(method -> !method.findAll(AssignExpr.class,
        assignment -> named(assignment.getTarget()).filter(field::equals).isPresent()).isEmpty())
        .map(method -> method.getNameAsString() + "()").findFirst();
    return changed.map(where -> state.because("`" + field + "` is changed in " + where + " and tested in " + methods
        + " methods")).orElseGet(() -> kind.because("`" + field + "` is never changed by a method and is tested in "
        + methods + " methods"));
  }

  /** The names a method tests in its conditions, its own parameters and locals left out. */
  private Set<String> tested(MethodDeclaration method) {
    Set<String> own = Stream.concat(method.getParameters().stream().map(Parameter::getNameAsString),
        method.findAll(VariableDeclarator.class).stream().map(VariableDeclarator::getNameAsString))
        .collect(Collectors.toSet());
    Node body = method;
    Stream<Expression> conditions = Stream.of(
        body.findAll(IfStmt.class).stream().map(IfStmt::getCondition),
        body.findAll(WhileStmt.class).stream().map(WhileStmt::getCondition),
        body.findAll(ConditionalExpr.class).stream().map(ConditionalExpr::getCondition)).flatMap(found -> found);
    return conditions.flatMap(condition -> Stream.concat(condition.accept(flags, null).stream(),
            condition.findAll(BinaryExpr.class).stream().flatMap(test -> compared.in(test).stream())
                .filter(term -> term.what() == Decision.Case.NAMED).map(Compared.Term::subject)
                .flatMap(Optional::stream).map(this::named).flatMap(Optional::stream)))
        .filter(name -> !own.contains(name)).collect(Collectors.toSet());
  }

  /** `field` or `this.field` as a name. */
  private Optional<String> named(Expression expression) {
    return expression.toNameExpr().map(NameExpr::getNameAsString)
        .or(() -> expression.toFieldAccessExpr().filter(access -> access.getScope().isThisExpr())
            .map(FieldAccessExpr::getNameAsString));
  }

  /** The names a condition uses as flags: alone, negated, or joined by `&&` and `||`. */
  private final class Flags extends ThroughParentheses<List<String>> {

    @Override
    public List<String> defaultAction(Node node, Void none) {
      return List.of();
    }

    @Override
    public List<String> visit(NameExpr name, Void none) {
      return List.of(name.getNameAsString());
    }

    @Override
    public List<String> visit(FieldAccessExpr access, Void none) {
      return named(access).map(List::of).orElse(List.of());
    }

    @Override
    public List<String> visit(UnaryExpr negated, Void none) {
      return negated.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT
          ? negated.getExpression().accept(this, none) : List.of();
    }

    @Override
    public List<String> visit(BinaryExpr joined, Void none) {
      boolean logical = joined.getOperator() == BinaryExpr.Operator.AND
          || joined.getOperator() == BinaryExpr.Operator.OR;
      List<String> both = new ArrayList<>(logical ? joined.getLeft().accept(this, none) : List.of());
      both.addAll(logical ? joined.getRight().accept(this, none) : List.of());
      return both;
    }
  }
}
