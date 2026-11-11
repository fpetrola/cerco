package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The same condition, put together from what another object answers, written in two or more members: a question
 * about that object that has no name, so everyone composes it again. Candidate from the plan of model smells.
 */
public final class ConditionRepeated extends RepeatedRule<ConditionRepeated.Site> {
  private static final int MEMBERS = 2;
  private static final Set<BinaryExpr.Operator> JOINING = Set.of(BinaryExpr.Operator.AND, BinaryExpr.Operator.OR);

  /**
   * One condition: where, in which member, how it is written, and its shape: the same text with the objects it asks
   * left unnamed, so that two members asking the same of differently named variables read alike.
   */
  public record Site(String file, int line, String member, String condition, String shape) implements Place {
  }

  public ConditionRepeated() {
    super(MEMBERS);
  }

  @Override
  public String id() {
    return "condition-repeated";
  }

  @Override
  public String principle() {
    return "a question asked of an object has a name, and the object answers it";
  }

  @Override
  public String description() {
    return "the same condition, composed of two or more things other objects answer, is written in " + MEMBERS
        + " or more members: a question with no name, composed again by everyone who asks it";
  }

  @Override
  public String remedy() {
    return "a method named for the question on the object that holds the answers; the members ask that";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a static predicate that takes the object, or a local boolean with the same expression",
        new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
            "several places compose the same question from one object's answers",
            "a query on that object, named for what is asked"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    Stream<Expression> conditions = Stream.of(
        unit.findAll(IfStmt.class).stream().map(IfStmt::getCondition),
        unit.findAll(WhileStmt.class).stream().map(WhileStmt::getCondition),
        unit.findAll(ConditionalExpr.class).stream().map(ConditionalExpr::getCondition)).flatMap(found -> found);
    conditions.filter(this::composed).forEach(condition -> sites.add(new Site(file.name(), line(condition),
        enclosing.of(condition), condition.toString().replaceAll("\\s+", " "), shape(condition))));
  }

  /** The condition with every variable it asks something of written as `_`. */
  private String shape(Expression condition) {
    String shape = condition.toString().replaceAll("\\s+", " ");
    Set<String> asked = Stream.concat(
        condition.findAll(MethodCallExpr.class).stream().map(MethodCallExpr::getScope).flatMap(Optional::stream),
        condition.findAll(FieldAccessExpr.class).stream().map(FieldAccessExpr::getScope))
        .filter(Expression::isNameExpr).map(Expression::toString)
        .filter(name -> Character.isLowerCase(name.charAt(0))).collect(Collectors.toSet());
    for (String name : asked) {
      shape = shape.replaceAll("\\b" + name + "\\.", "_.");
    }
    return shape;
  }

  /** Joined by `&&` or `||`, and asking another object at least twice: more than one thing put together. */
  private boolean composed(Expression condition) {
    boolean joined = condition.toBinaryExpr().filter(binary -> JOINING.contains(binary.getOperator())).isPresent();
    long asked = condition.findAll(MethodCallExpr.class, call -> call.getScope()
        .filter(scope -> !scope.isThisExpr()).isPresent()).size()
        + condition.findAll(FieldAccessExpr.class, access -> !access.getScope().isThisExpr()).size();
    return joined && asked >= 2;
  }

  @Override
  protected String what(Site place, Project project) {
    return place.shape();
  }

  @Override
  protected Finding found(Site place, Shared<Site> shared, Project project) {
    return finding(place, shared.what(), "`" + clipped(place.condition()) + "` in " + place.member() + "; "
        + shared.members() + " members write this same condition");
  }
}
