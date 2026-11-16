package com.fpetrola.cerco.baseline;

import com.fpetrola.cerco.model.Finding;
import java.util.List;

/**
 * What comparing today's findings with the baseline says: the new ones, how many went away, how many still stand,
 * and the entries whose file is gone that moved code took with it to its new path.
 */
public record Reconciliation(List<Finding> fresh, int solved, int frozen, Multiset updated, Multiset carried) {

  /** The keys among these that left the baseline because their finding went away, not because their code moved. */
  public List<String> solvedAmong(Multiset considered) {
    return considered.keys().stream().filter(key -> considered.count(key) > updated.count(key) + carried.count(key))
        .toList();
  }

}
