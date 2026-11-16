package com.fpetrola.cerco.baseline;

import com.fpetrola.cerco.model.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Matches today's findings against the frozen ones by key, counting: three frozen and four found means one is new,
 * the last one by line. Entries for files outside the scope are carried over untouched. An entry whose file is gone
 * goes with a finding that is new only because its file moved: same spot, another path. A finding the rule says
 * narrows a frozen one that went away (a package cycle that lost packages) stays frozen in its place.
 */
public final class Reconciler {

  public Reconciliation reconcile(Multiset baseline, Set<String> scopeFiles, List<Finding> current,
      Multiset departed, BiPredicate<String, String> narrows) {
    Multiset inScope = baseline.where(key -> scopeFiles.contains(new Key(key).file()));
    Map<String, Integer> left = new TreeMap<>(departed.view());
    Map<String, List<Finding>> byKey = current.stream()
        .collect(Collectors.groupingBy(Finding::key, TreeMap::new, Collectors.toList()));
    List<Finding> fresh = new ArrayList<>();
    Multiset kept = new Multiset();
    for (Map.Entry<String, List<Finding>> entry : byKey.entrySet()) {
      List<Finding> found = entry.getValue().stream().sorted(Comparator.comparingInt(Finding::line)).toList();
      int stays = Math.min(inScope.count(entry.getKey()), found.size());
      int moved = take(left, new Key(entry.getKey()).spot(), found.size() - stays);
      kept.add(entry.getKey(), stays + moved);
      fresh.addAll(found.subList(stays + moved, found.size()));
    }
    Multiset carried = new Multiset();
    departed.view().forEach((key, count) -> carried.add(key, count - left.get(key)));
    List<String> vacated = inScope.keys().stream().filter(key -> kept.count(key) < inScope.count(key)).toList();
    for (Finding finding : List.copyOf(fresh)) {
      String spot = new Key(finding.key()).spot();
      vacated.stream().filter(key -> narrows.test(new Key(key).spot(), spot)).findFirst().ifPresent(key -> {
        fresh.remove(finding);
        kept.add(finding.key());
        carried.add(key, inScope.count(key) - kept.count(key) - carried.count(key));
      });
    }
    Multiset updated = baseline.where(key -> !scopeFiles.contains(new Key(key).file()) && carried.count(key) == 0);
    kept.view().forEach(updated::add);
    int vanished = departed.where(key -> baseline.count(key) == 0).total()
        - carried.where(key -> baseline.count(key) == 0).total();
    int gone = inScope.keys().stream().mapToInt(key -> Math.max(0, inScope.count(key) - kept.count(key)
        - carried.count(key))).sum();
    return new Reconciliation(fresh.stream().sorted().toList(), gone + vanished, current.size() - fresh.size(),
        updated, carried);
  }

  private int take(Map<String, Integer> left, String spot, int wanted) {
    int taken = 0;
    for (Map.Entry<String, Integer> entry : left.entrySet()) {
      int here = new Key(entry.getKey()).spot().equals(spot) ? Math.min(entry.getValue(), wanted - taken) : 0;
      entry.setValue(entry.getValue() - here);
      taken += here;
    }
    return taken;
  }
}
