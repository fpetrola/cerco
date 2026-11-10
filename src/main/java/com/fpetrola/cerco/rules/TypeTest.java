package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.project.TypeName;
import java.util.Optional;
import java.util.Set;

/**
 * One place where a value is asked its type, with what telling the missing model apart will need once the project is
 * whole: the type tested, what it is tested on, where from, and whether it is sifting a collection.
 *
 * @param finding the finding as the baseline knows it, still without a reading
 * @param tested the type asked for, when the test names one
 * @param member the member the test sits in, and `inside` the type around it
 * @param alongside every type the same value is asked for in that member, this one included
 * @param pickedFrom the collection being sifted, when the test picks its elements by type
 * @param mute true when no model is missing behind the test: an `equals`, a type variable, an exception, a lambda
 *     or a null given its type
 */
public record TypeTest(Finding finding, Optional<TypeName> tested, Subject subject, String member, String inside,
    Set<String> alongside, Optional<String> pickedFrom, boolean mute) {

  /**
   * What is tested: as written, whether it is `this`, the type it was declared as when the file shows it, and the
   * call that looked it up by its class, when one did.
   */
  public record Subject(String text, boolean self, Optional<TypeName> declared, Optional<String> lookedUpBy) {
  }
}
