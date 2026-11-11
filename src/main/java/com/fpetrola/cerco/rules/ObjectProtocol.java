package com.fpetrola.cerco.rules;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;

/**
 * The methods of every Java object through which code asks or compares types: `equals`, `getClass`, `Class.cast`.
 * The rules recognise them by name, and the names are written here only.
 */
final class ObjectProtocol {

  /** `a.equals(b)`: one object compared with another. */
  boolean compares(MethodCallExpr call) {
    return comparesBy(call.getNameAsString()) && call.getArguments().size() == 1;
  }

  /** Whether a node sits in the `equals` its type declares, where asking the type is what the language asks for. */
  boolean insideEquals(Node node) {
    return node.findAncestor(MethodDeclaration.class).filter(method -> comparesBy(method.getNameAsString()))
        .isPresent();
  }

  /** `x.getClass()`: an object asked for its class. */
  boolean asksItsClass(MethodCallExpr call) {
    return call.getNameAsString().equals("getClass") && call.getArguments().isEmpty();
  }

  /** `Type.class.cast(x)` or `kind.cast(x)`: a cast spelled as a call. */
  boolean casts(MethodCallExpr call) {
    return castsBy(call.getNameAsString()) && call.getArguments().size() == 1 && call.getScope().isPresent();
  }

  /** `Type.class::cast`: a cast handed over as a function. */
  boolean casts(MethodReferenceExpr reference) {
    return castsBy(reference.getIdentifier());
  }

  private boolean comparesBy(String name) {
    return name.equals("equals");
  }

  private boolean castsBy(String name) {
    return name.equals("cast");
  }
}
