package com.fpetrola.cerco.surface;

/**
 * How far the surface of a module reaches: only its public interfaces and their methods, its interfaces of any
 * openness and the public constructors of public classes (promises), everything public, or everything that is not private. Which one a project seals is a design decision; a survey measures the three.
 */
public enum Reach {
  INTERFACES("interfaces", Openness.PUBLIC, true, false), PROMISES("promises", Openness.PACKAGE, true, true),
  PUBLIC("public", Openness.PUBLIC, false, false), ALL("all", Openness.PACKAGE, false, false);

  private final String word;
  private final Openness least;
  private final boolean onlyPromises;
  private final boolean entrances;

  Reach(String word, Openness least, boolean onlyPromises, boolean entrances) {
    this.entrances = entrances;
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
    return takes(openness, promised, false);
  }

  /** The same, for a constructor too: at promises, a public one of a public class is the module's entrance. */
  boolean takes(Openness openness, boolean promised, boolean constructor) {
    if (entrances && constructor && openness == Openness.PUBLIC) {
      return true;
    }
    return openness.compareTo(least) >= 0 && (promised || !onlyPromises);
  }
}
