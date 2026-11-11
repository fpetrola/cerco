package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Chain;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Names;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The same two calls made one after the other on one object, in three or more members: the object is told one thing
 * in two steps, and every caller has to remember both and their order. Candidate from the plan of model smells; it
 * widens notified-by-caller, which saw one such pair.
 */
public final class SaidInTwoCalls extends RepeatedRule<SaidInTwoCalls.Site> {
  private static final int MEMBERS = 3;

  /**
   * Two calls in a row on one receiver: where, in which member, on what, how it is reached, for the
   * project to say its type, and the two calls with their arities.
   */
  public record Site(String file, int line, String member, String receiver, Optional<Chain> told, Said first,
      Said second) implements Place {
  }

  /** A call by its name and how many arguments it takes. */
  public record Said(String name, int arguments) {
  }

  public SaidInTwoCalls() {
    super(MEMBERS);
  }

  @Override
  public String id() {
    return "said-in-two-calls";
  }

  @Override
  public String principle() {
    return "an object is told what to do in one message: it knows the steps and their order, its callers do not";
  }

  @Override
  public String description() {
    return "the same two methods of the project are called one after the other on one object in " + MEMBERS
        + " or more members: one thing said in two steps, which every caller has to repeat in order";
  }

  @Override
  public String remedy() {
    return "one method of that object that does both, named for what the pair achieves; the two steps become its "
        + "own business";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a helper that makes the two calls, kept outside the object",
        new Hypothesis(MissingModel.PROTOCOL_BY_CONVENTION,
            "the two calls always go together, in that order",
            "one operation of the receiver that does both"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    Names names = new Names(new Imports(unit));
    for (BlockStmt block : unit.findAll(BlockStmt.class)) {
      List<Statement> statements = block.getStatements();
      for (int at = 0; at + 1 < statements.size(); at++) {
        Optional<MethodCallExpr> first = told(statements.get(at));
        Optional<MethodCallExpr> second = told(statements.get(at + 1));
        first.flatMap(one -> second.filter(other -> sameReceiver(one, other)).map(other -> new Site(file.name(),
            line(one), enclosing.of(one), one.getScope().map(Expression::toString).orElse(""),
            one.getScope().flatMap(names::chainOf), said(one), said(other)))).ifPresent(sites::add);
      }
    }
  }

  /** The call a statement is, when it is nothing but a call on a variable or a field. */
  private Optional<MethodCallExpr> told(Statement statement) {
    return statement.toExpressionStmt().flatMap(told -> told.getExpression().toMethodCallExpr())
        .filter(call -> call.getScope().filter(scope -> scope.isNameExpr() || scope.isFieldAccessExpr()).isPresent());
  }

  private boolean sameReceiver(MethodCallExpr one, MethodCallExpr other) {
    return one.getScope().map(Expression::toString).equals(other.getScope().map(Expression::toString))
        && !one.getNameAsString().equals(other.getNameAsString());
  }

  private Said said(MethodCallExpr call) {
    return new Said(call.getNameAsString(), call.getArguments().size());
  }

  @Override
  protected String what(Site place, Project project) {
    return place.first().name() + "(); " + place.second().name() + "()";
  }

  /**
   * Only an object of the project told two things that are not both setters: two calls on a library's object are
   * that library's protocol, and a run of setters is an object assembled from outside, which has its own rule.
   */
  @Override
  protected boolean counts(Site place, Project project) {
    boolean setters = Stream.of(place.first(), place.second()).allMatch(said -> said.name().matches("set[A-Z].*"));
    return !setters && place.told().flatMap(project::typeOf).filter(project::declares).isPresent();
  }

  @Override
  protected Finding found(Site place, Shared<Site> shared, Project project) {
    return finding(place, shared.what(), "`" + clipped(place.receiver()) + "` is told `" + shared.what() + "` in "
        + place.member() + "; " + shared.members() + " members say the same two calls in a row");
  }
}
