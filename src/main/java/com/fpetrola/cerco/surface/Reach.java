package com.fpetrola.cerco.surface;

/**
 * How far the surface of a module reaches: only its public interfaces and their methods, its interfaces of any
 * openness but no classes (promises), everything public, or everything that is not private. Which one a project seals is a design decision; a survey measures the three.
 */
public enum Reach {
  INTERFACES("interfaces", Openness.PUBLIC, true), PROMISES("promises", Openness.PACKAGE, true),
  PUBLIC("public", Openness.PUBLIC, false), ALL("all", Openness.PACKAGE, false);

  private final String word;
  private final Openness least;
  private final boolean onlyPromises;

  Reach(String word, Openness least, boolean onlyPromises) {
    this.word = word;
    this.least = least;
    this.onlyPromises = onlyPromises;
  }

  /** The reach a project names in its configuration, by its word; none for a word that is not one. */
  public static java.util.Optional<Reach> from(String word) {
    return java.util.stream.Stream.of(values()).filter(reach -> reach.word.equals(word)).findFirst();
  }

  public String word() {
    return word;
  }

  /** Whether a member that open, and declared or not by an interface, is surface at this reach. */
  boolean takes(Openness openness, boolean promised) {
    return openness.compareTo(least) >= 0 && (promised || !onlyPromises);
  }
}
