package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.Reading;

/** One model a smell may hide: the question about the code that points to it, and the transformation it asks for. */
public record Hypothesis(MissingModel model, String when, String then) {

  public String line() {
    return model.id() + ": when " + when + " -> " + then;
  }

  /** This hypothesis as the reading of a finding whose rule suspects nothing else: what it supposes is the case. */
  public Reading itself() {
    return new Reading(model, when, then);
  }

  /** This hypothesis chosen for one finding, with the evidence at that place that chose it. */
  public Reading because(String evidence) {
    return new Reading(model, evidence, then);
  }
}
