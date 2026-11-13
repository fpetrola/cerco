package com.fpetrola.cerco.surface;

/**
 * Who can name a declaration once the types around it are counted: anyone, the package and its subclasses, or only
 * its own top-level type. Each reach takes what is at least as open as it asks.
 */
public enum Openness {
  PRIVATE, PACKAGE, PUBLIC;

  /** The narrower of this and what encloses it: a public method of a package type is not public. */
  Openness within(Openness enclosing) {
    return compareTo(enclosing) <= 0 ? this : enclosing;
  }
}
