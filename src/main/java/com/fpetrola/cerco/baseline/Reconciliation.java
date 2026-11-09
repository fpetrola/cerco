package com.fpetrola.cerco.baseline;

import com.fpetrola.cerco.model.Finding;
import java.util.List;

/** What comparing today's findings with the baseline says: the new ones, how many went away, how many still stand. */
public record Reconciliation(List<Finding> fresh, int solved, int frozen, Multiset updated) {

  /** The same, with findings that went away for another reason (their file is gone) counted as solved. */
  public Reconciliation plusSolved(int more) {
    return new Reconciliation(fresh, solved + more, frozen, updated);
  }
}
