package com.fpetrola.cerco.surface;

import java.util.Set;

/**
 * One thing a module offers to others: a type, or a method, constructor, record component, enum constant or field
 * of one. Its signature is the line a seal keeps; its name is how a caller says it, to tell whether anyone does.
 */
public record Member(String signature, String name, boolean isType, boolean promised, Openness openness,
    Set<Hint> hints, String file, int line) {

  /** Whether this member is surface at that reach: an interface or a method of one, or open enough. */
  public boolean within(Reach reach) {
    return reach.takes(openness, promised, signature.contains("#<init>("));
  }

  public boolean says(Hint hint) {
    return hints.contains(hint);
  }
}
