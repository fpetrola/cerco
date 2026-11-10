package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.MissingModel;

/**
 * A rule that catches code deciding over a set of values (switches, type codes, chains, enum constants tested from
 * outside): whatever the construct, what such a decision hides is the same, so the family says it once.
 */
public abstract class DecisionRule extends FileRule {

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a map of lambdas keyed by the same constants, a chain of Optional.or, or the switch "
        + "split in two: the same decision in another place",
        new Hypothesis(MissingModel.VARIATION_AS_DATA,
            "the value is fixed for the object's life (a kind, a type code, a mode chosen once)",
            "subtypes, or the behaviour on the enum constants"),
        new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
            "the branches reassign the value they test",
            "a state object per value"),
        new Hypothesis(MissingModel.VALUE_WITHOUT_A_TYPE,
            "the cases are strings or numbers",
            "a type or an enum for the value, owning what the cases decide"));
  }
}
