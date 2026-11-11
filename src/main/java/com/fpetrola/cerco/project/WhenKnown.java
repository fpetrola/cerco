package com.fpetrola.cerco.project;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import java.util.Optional;

/**
 * A reading of syntax that answers only for the kinds of node it knows about, and has nothing to say of the rest.
 *
 * @param <T> what it answers with
 * @param <A> what it is handed along the way
 */
public abstract class WhenKnown<T, A> extends GenericVisitorWithDefaults<Optional<T>, A> {

  @Override
  public Optional<T> defaultAction(Node node, A along) {
    return Optional.empty();
  }
}
