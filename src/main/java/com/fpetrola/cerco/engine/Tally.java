package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.baseline.CeilingReconciler;
import com.fpetrola.cerco.baseline.Reconciliation;
import com.fpetrola.cerco.model.Finding;
import java.util.ArrayList;
import java.util.List;

/** The running totals of a run across rules. */
final class Tally {
  private final List<Finding> fresh = new ArrayList<>();
  private int solved;
  private int frozen;

  void add(Reconciliation reconciliation) {
    fresh.addAll(reconciliation.fresh());
    solved += reconciliation.solved();
    frozen += reconciliation.frozen();
  }

  void add(CeilingReconciler.Outcome outcome) {
    fresh.addAll(outcome.fresh());
    solved += outcome.solved();
    frozen += outcome.frozen();
  }

  List<Finding> fresh() {
    return fresh.stream().sorted().toList();
  }

  int solved() {
    return solved;
  }

  int frozen() {
    return frozen;
  }
}
