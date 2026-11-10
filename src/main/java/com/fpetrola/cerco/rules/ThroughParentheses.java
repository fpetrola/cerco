package com.fpetrola.cerco.rules;

import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;

/**
 * A reading of expressions for which parentheses change nothing: what is said of `(x)` is what is said of `x`.
 *
 * @param <R> what the reading answers
 */
abstract class ThroughParentheses<R> extends GenericVisitorWithDefaults<R, Void> {

  @Override
  public R visit(EnclosedExpr enclosed, Void none) {
    return enclosed.getInner().accept(this, none);
  }
}
