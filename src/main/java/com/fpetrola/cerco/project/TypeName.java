package com.fpetrola.cerco.project;

import java.util.Optional;
import java.util.Set;

/**
 * A type as one file names it: its simple name, its full name when the file says it (written out, or imported by
 * name), and otherwise where the file looks for a bare name. Two types of one simple name are told apart by that.
 */
public record TypeName(String simple, Optional<String> qualified, Reach reach) {

  /** Where a file finds a name it neither writes out nor imports: its own package, and those it imports whole. */
  public record Reach(String home, Set<String> onDemand) {

    boolean sees(Declared type) {
      return type.nested() || type.home().equals(home) || onDemand.contains(type.home());
    }
  }

  /** Whether a declaration of the project is the type this file means. */
  public boolean means(Declared type) {
    return qualified.map(full -> type.qualified().equals(full) || type.qualified().endsWith("." + full))
        .orElseGet(() -> reach.sees(type));
  }
}
