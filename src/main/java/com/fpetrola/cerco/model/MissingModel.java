package com.fpetrola.cerco.model;

/**
 * What a design was missing when a smell appeared: the closed vocabulary rules diagnose with and autopsies name, so
 * that a project learns which models its smells usually hide. Each says what is absent and how it usually comes in.
 */
public enum MissingModel {
  BEHAVIOUR_NOT_ON_OWNER("the object holding the data lacks the behaviour",
      "move the method to the owner, which offers the whole operation"),
  VARIATION_AS_DATA("a variant is encoded as a value that others decide on",
      "subtypes, behaviour on the enum constants, a strategy, or two methods"),
  DISPATCH_FROM_OUTSIDE("the hierarchy exists but the operation lives outside it",
      "a method on the common type; a visitor when two or more operations test it"),
  STRUCTURE_WALKED_FROM_OUTSIDE("the structure does not know how to walk itself",
      "an internal iterator, a named query on the owner, or a composite with a visitor"),
  CONCEPT_WITHOUT_A_CLASS("data that travels together has no type",
      "extract the class they form, a parameter object, or pass the whole object"),
  VALUE_WITHOUT_A_TYPE("a primitive or a string plays a role of the domain",
      "a value object owning its arithmetic, or an enum for a closed set"),
  STATE_WITHOUT_AN_OBJECT("a mode or a phase is kept in flags or in null",
      "a state object, a null object, two classes, or a mandatory collaborator"),
  SHAPE_REPEATED("the same shape is written again with one part varying",
      "name what is fixed; what varies becomes a parameter, a strategy or a subtype"),
  KNOWLEDGE_WITHOUT_ONE_OWNER("the same fact or decision lives in several places",
      "one owner keeps it, and everyone else asks the owner"),
  PROCEDURE_INSTEAD_OF_MODEL("a procedure is narrated step by step",
      "a method per step, and an object named by the concept when the steps share state"),
  PROTOCOL_BY_CONVENTION("a collaboration works only if callers do things in order",
      "the owner does the whole thing: execute-around, notifying itself, a builder");

  private final String absent;
  private final String transformation;

  MissingModel(String absent, String transformation) {
    this.absent = absent;
    this.transformation = transformation;
  }

  /** How rules print it and autopsies write it: `behaviour-not-on-owner`. */
  public String id() {
    return name().toLowerCase().replace('_', '-');
  }

  /** The first word of the id, for the head of a column. */
  public String word() {
    return id().substring(0, id().indexOf('-'));
  }

  public String absent() {
    return absent;
  }

  public String transformation() {
    return transformation;
  }
}
