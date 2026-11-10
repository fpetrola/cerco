package com.fpetrola.cerco.model;

/**
 * What one finding says about itself: of the models its rule suspects, the one the code at this place points to, the
 * evidence that chose it, and the transformation it asks for.
 */
public record Reading(MissingModel model, String because, String then) {

  public String line() {
    return model.id() + ": " + because + " -> " + then;
  }
}
