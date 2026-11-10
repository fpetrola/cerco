package com.fpetrola.cerco.rules;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the branches of a decision do: whether each only gives a value (returns it, yields it, assigns it to the same
 * thing as the others), which is data of the case, or does work, which is behaviour of the case.
 */
final class Arms {
  private static final String REFUSES = "throw";
  private final Gives gives = new Gives();

  /** True when every branch gives a value the same way; one that only throws refuses its case and does not count. */
  boolean allAnswer(List<List<Statement>> bodies, boolean valued) {
    List<Optional<String>> given = bodies.stream().filter(body -> !body.isEmpty())
        .map(body -> only(body, valued)).toList();
    Set<String> ways = given.stream().flatMap(Optional::stream).filter(way -> !way.equals(REFUSES))
        .collect(Collectors.toSet());
    return given.stream().allMatch(Optional::isPresent) && ways.size() == 1;
  }

  /** How a branch gives its value, when it is one statement besides its break. */
  private Optional<String> only(List<Statement> body, boolean valued) {
    List<Statement> work = body.stream().filter(statement -> !statement.isBreakStmt()).toList();
    return work.size() == 1 ? work.get(0).accept(gives, valued) : Optional.empty();
  }

  /** How one statement gives a value: by returning, yielding or assigning it; an expression alone when `valued`. */
  private final class Gives extends GenericVisitorWithDefaults<Optional<String>, Boolean> {

    @Override
    public Optional<String> defaultAction(Node node, Boolean valued) {
      return Optional.empty();
    }

    @Override
    public Optional<String> visit(BlockStmt block, Boolean valued) {
      return only(block.getStatements(), valued);
    }

    @Override
    public Optional<String> visit(ReturnStmt returned, Boolean valued) {
      return returned.getExpression().map(value -> "return");
    }

    @Override
    public Optional<String> visit(YieldStmt yielded, Boolean valued) {
      return Optional.of("yield");
    }

    @Override
    public Optional<String> visit(ThrowStmt thrown, Boolean valued) {
      return Optional.of(REFUSES);
    }

    @Override
    public Optional<String> visit(ExpressionStmt statement, Boolean valued) {
      return statement.getExpression().toAssignExpr().map(assignment -> "= " + assignment.getTarget())
          .or(() -> Optional.of("yield").filter(way -> valued));
    }
  }
}
