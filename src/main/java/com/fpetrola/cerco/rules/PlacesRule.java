package com.fpetrola.cerco.rules;

import java.util.ArrayList;
import java.util.List;

/**
 * A project-wide rule that keeps every place where it saw what it looks for and judges them at the end, when the
 * project can say whose the things at each place are.
 *
 * @param <S> one place
 */
public abstract class PlacesRule<S extends Place> extends ProjectRule<List<S>> {

  @Override
  protected final List<S> start() {
    return new ArrayList<>();
  }
}
