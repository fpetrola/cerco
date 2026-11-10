package com.fpetrola.cerco.rules;

/**
 * A rule whose findings in one file depend on the others: the same question asked from several places, a clone, a
 * cycle. It gathers what it needs from each file and concludes at the end. Never runs on a single edit.
 *
 * @param <S> what the rule remembers between files
 */
public abstract class ProjectRule<S> extends GatheringRule<S> {

  @Override
  public final boolean perFile() {
    return false;
  }
}
