package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.MissingModel;

/** One model a smell may hide: the question about the code that points to it, and the transformation it asks for. */
public record Hypothesis(MissingModel model, String when, String then) {

  public String line() {
    return model.id() + ": when " + when + " -> " + then;
  }
}
