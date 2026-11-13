package com.fpetrola.cerco.surface;

/**
 * What a sealed signature says that a model usually should say with a type of its own: a text, any object, a flag
 * that picks behaviour, or behaviour handed over as a function.
 */
public enum Hint {
  TEXT("String"), OBJECT("Object"), FLAG("boolean"), FUNCTION("function");

  private final String word;

  Hint(String word) {
    this.word = word;
  }

  public String word() {
    return word;
  }
}
