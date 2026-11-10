package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.rules.Decision.Case;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.RecordPatternExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a condition compares and with what: `x == Kind.A`, `"a".equals(x)`, `x instanceof T`, `x == null`, alone or
 * joined by `||`. Each kind of expression says it in its own way; whatever is no such comparison is not one.
 */
final class Compared {
  private static final Set<BinaryExpr.Operator> EQUALITY = Set.of(BinaryExpr.Operator.EQUALS,
      BinaryExpr.Operator.NOT_EQUALS);
  private final Kinds kinds = new Kinds();
  private final Terms terms = new Terms();

  /** One comparison: what is compared, when the condition shows it, with what kind of thing, and its name. */
  record Term(Optional<Expression> subject, Case what, Optional<String> name) {
  }

  /** The comparisons a condition makes. */
  List<Term> in(Expression condition) {
    return condition.accept(terms, null);
  }

  /** A switch label, which is what the selector is compared with. */
  Term label(Expression label) {
    return new Term(Optional.empty(), label.accept(kinds, null), name(label));
  }

  Term unknown() {
    return new Term(Optional.empty(), Case.OTHER, Optional.empty());
  }

  /** The constant a label or an operand names: `A` in `A` and in `Kind.A`. */
  private Optional<String> name(Expression constant) {
    return constant.toNameExpr().map(NameExpr::getNameAsString)
        .or(() -> constant.toFieldAccessExpr().map(FieldAccessExpr::getNameAsString));
  }

  /** Of two operands, the one that is a constant is what the other is compared with. */
  private List<Term> between(Expression left, Expression right) {
    Case ofLeft = left.accept(kinds, null);
    Case ofRight = right.accept(kinds, null);
    if (ofRight != Case.OTHER) {
      return List.of(new Term(Optional.of(left), ofRight, name(right)));
    }
    return List.of(ofLeft == Case.OTHER ? unknown() : new Term(Optional.of(right), ofLeft, name(left)));
  }

  /** What kind of constant an expression is; a name counts when it is written as constants are. */
  private final class Kinds extends ThroughParentheses<Case> {

    @Override
    public Case defaultAction(Node node, Void none) {
      return Case.OTHER;
    }

    @Override
    public Case visit(NameExpr name, Void none) {
      return Character.isUpperCase(name.getNameAsString().charAt(0)) ? Case.NAMED : Case.OTHER;
    }

    @Override
    public Case visit(FieldAccessExpr constant, Void none) {
      return Character.isUpperCase(constant.getNameAsString().charAt(0)) ? Case.NAMED : Case.OTHER;
    }

    @Override
    public Case visit(StringLiteralExpr text, Void none) {
      return Case.TEXT;
    }

    @Override
    public Case visit(IntegerLiteralExpr number, Void none) {
      return Case.NUMBER;
    }

    @Override
    public Case visit(LongLiteralExpr number, Void none) {
      return Case.NUMBER;
    }

    @Override
    public Case visit(CharLiteralExpr letter, Void none) {
      return Case.TEXT;
    }

    @Override
    public Case visit(UnaryExpr signed, Void none) {
      return signed.getExpression().accept(this, none);
    }

    @Override
    public Case visit(NullLiteralExpr absent, Void none) {
      return Case.ABSENT;
    }

    @Override
    public Case visit(TypePatternExpr type, Void none) {
      return Case.TYPE;
    }

    @Override
    public Case visit(RecordPatternExpr type, Void none) {
      return Case.TYPE;
    }
  }

  /** The comparisons an expression makes when it is used as a condition. */
  private final class Terms extends ThroughParentheses<List<Term>> {

    @Override
    public List<Term> defaultAction(Node node, Void none) {
      return List.of(unknown());
    }

    @Override
    public List<Term> visit(UnaryExpr negated, Void none) {
      return negated.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT
          ? negated.getExpression().accept(this, none) : List.of(unknown());
    }

    @Override
    public List<Term> visit(BinaryExpr binary, Void none) {
      if (binary.getOperator() == BinaryExpr.Operator.OR) {
        List<Term> both = new ArrayList<>(binary.getLeft().accept(this, none));
        both.addAll(binary.getRight().accept(this, none));
        return both;
      }
      return EQUALITY.contains(binary.getOperator()) ? between(binary.getLeft(), binary.getRight())
          : List.of(unknown());
    }

    @Override
    public List<Term> visit(MethodCallExpr call, Void none) {
      return call.getScope().filter(scope -> call.getNameAsString().equals("equals")
          && call.getArguments().size() == 1).map(scope -> between(scope, call.getArgument(0)))
          .orElseGet(() -> List.of(unknown()));
    }

    @Override
    public List<Term> visit(InstanceOfExpr test, Void none) {
      return List.of(new Term(Optional.of(test.getExpression()), Case.TYPE, Optional.empty()));
    }
  }
}
