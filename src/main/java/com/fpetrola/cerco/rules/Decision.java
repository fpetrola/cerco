package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.project.TypeName;
import java.util.Optional;
import java.util.Set;

/**
 * One place where code decides over a set of values, with what telling the missing model apart will need once the
 * project is whole: what is decided on, what the cases are, and what the branches do.
 *
 * @param finding the finding as the baseline knows it, still without a reading
 * @param inside the type the decision sits in
 * @param cases what kinds of thing the cases are, and `names` the constants they name
 */
public record Decision(Finding finding, Selector selector, String inside, Set<Case> cases, Set<String> names,
    Effect effect) {

  /**
   * What is decided on: as written, the type it was declared as when the file shows it, and who it was asked of when
   * it is `x.kind()` or `x.kind`.
   */
  public record Selector(String text, Optional<TypeName> declared, Optional<Asked> asked) {
  }

  /**
   * The object a value was asked of: as written, the type it was declared as when the file shows it, the member
   * that answered, and whether that member is a method taking nothing.
   */
  public record Asked(String of, Optional<TypeName> type, String member, boolean called) {
  }

  /** What a case compares with. */
  public enum Case {
    NAMED, TEXT, NUMBER, TYPE, ABSENT, OTHER
  }

  /** What the branches do with the decision. */
  public enum Effect {
    REASSIGN, ANSWER, ACT, UNKNOWN
  }
}
