package com.fpetrola.cerco.rules;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.Optional;

/** The name of the member a node sits in, as the baseline will remember it: {@code Type.method(Params)} or {@code Type}. */
public final class Enclosing {

  public String of(Node node) {
    Optional<CallableDeclaration> callable = node.findAncestor(CallableDeclaration.class);
    if (callable.isPresent()) {
      return typeOf(callable.get()) + "." + callable.get().getSignature().asString();
    }
    return typeOf(node);
  }

  /** The nearest type a node belongs to, by its qualified name when it has one. */
  public String typeOf(Node node) {
    return node.findAncestor(TypeDeclaration.class).map(this::nameOf).orElse("<file>");
  }

  public String nameOf(TypeDeclaration<?> type) {
    return type.getFullyQualifiedName().orElse(type.getNameAsString());
  }

  /** The direct parent of a node when it is of that kind; empty otherwise. */
  public <N extends Node> Optional<N> parentOf(Node node, Class<N> kind) {
    Node parent = node.getParentNode().orElse(node);
    return node.findAncestor(kind, ancestor -> ancestor == parent);
  }

  public String signatureOf(CallableDeclaration<?> callable) {
    return typeOf(callable) + "." + callable.getSignature().asString();
  }
}
