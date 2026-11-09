package com.fpetrola.cerco.baseline;

import com.fpetrola.cerco.model.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Matches today's findings against the frozen ones by key, counting: three frozen and four found means one is new,
 * the last one by line. Entries for files outside the scope are carried over untouched.
 */
public final class Reconciler {

  public Reconciliation reconcile(Multiset baseline, Set<String> scopeFiles, List<Finding> current) {
    Multiset inScope = baseline.where(key -> scopeFiles.contains(new Key(key).file()));
    Multiset updated = baseline.where(key -> !scopeFiles.contains(new Key(key).file()));
    Map<String, List<Finding>> byKey = current.stream()
        .collect(Collectors.groupingBy(Finding::key, TreeMap::new, Collectors.toList()));
    List<Finding> fresh = new ArrayList<>();
    int frozen = 0;
    for (Map.Entry<String, List<Finding>> entry : byKey.entrySet()) {
      List<Finding> found = entry.getValue().stream().sorted(Comparator.comparingInt(Finding::line)).toList();
      int kept = Math.min(inScope.count(entry.getKey()), found.size());
      frozen += kept;
      updated.add(entry.getKey(), kept);
      fresh.addAll(found.subList(kept, found.size()));
    }
    return new Reconciliation(fresh.stream().sorted().toList(), inScope.total() - frozen, frozen, updated);
  }
}
