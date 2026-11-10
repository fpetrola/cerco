package com.fpetrola.cerco.rules;

import java.util.List;

/**
 * What a rule knows about the models its smell usually hides, so that a finding points at the design and not only at
 * the construct: the hypotheses, each with the question that tells it apart, and the known disguises of the same smell
 * that satisfy the rule by the letter.
 */
public record Diagnosis(List<Hypothesis> hypotheses, String notAFix) {

  public Diagnosis(String notAFix, Hypothesis... hypotheses) {
    this(List.of(hypotheses), notAFix);
  }

  public boolean says() {
    return !hypotheses.isEmpty();
  }
}
