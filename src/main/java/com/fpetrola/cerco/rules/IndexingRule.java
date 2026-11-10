package com.fpetrola.cerco.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A project-wide rule that indexes what the project declares (types, enums, methods) by name, and the sites that use
 * it, and decides at the end which sites ask from outside what a declaration should answer.
 *
 * @param <D> what is known about each declared name
 * @param <S> one site
 */
public abstract class IndexingRule<D, S> extends ProjectRule<IndexingRule.Index<D, S>> {

  /** The declarations by name, and the sites. */
  public record Index<D, S>(Map<String, D> declared, List<S> sites) {
  }

  @Override
  protected final Index<D, S> start() {
    return new Index<>(new HashMap<>(), new ArrayList<>());
  }
}
