package com.fpetrola.cerco.project;

/** What sort of type a declaration is, and what that sort means to whoever reads a hierarchy. */
public enum Kind {
  CLASS(false, false), INTERFACE(true, false), ENUM(false, true), RECORD(false, false), ANNOTATION(false, false);

  private final boolean onlyPromises;
  private final boolean closed;

  Kind(boolean onlyPromises, boolean closed) {
    this.onlyPromises = onlyPromises;
    this.closed = closed;
  }

  /** A type that says what its members do and does none of it: an interface. */
  public boolean onlyPromises() {
    return onlyPromises;
  }

  /** A type whose instances are all named where it is declared: an enum. */
  public boolean isClosed() {
    return closed;
  }
}
