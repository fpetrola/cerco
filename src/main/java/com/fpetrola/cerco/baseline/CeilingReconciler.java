package com.fpetrola.cerco.baseline;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.rules.BudgetRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Matches today's measures against frozen ceilings. Above its ceiling, a thing is a fresh finding; below it, the
 * ceiling comes down to meet it; at or under the default, the entry is gone.
 */
public final class CeilingReconciler {

  public record Outcome(List<Finding> fresh, int solved, int frozen, Map<String, Integer> updated) {

    public Outcome plusSolved(int more) {
      return new Outcome(fresh, solved + more, frozen, updated);
    }
  }

  public Outcome reconcile(BudgetRule rule, Map<String, Integer> stored, Set<String> scopeFiles,
      List<Measure> measures) {
    Map<String, Integer> updated = new TreeMap<>();
    stored.forEach((key, ceiling) -> {
      if (!scopeFiles.contains(new Key(key).file())) {
        updated.put(key, ceiling);
      }
    });
    List<Finding> fresh = new ArrayList<>();
    for (Measure measure : measures) {
      Integer ceiling = stored.get(measure.key());
      int limit = ceiling == null ? rule.ceiling() : ceiling;
      if (measure.value() > limit) {
        fresh.add(rule.finding(measure, limit));
      }
      if (ceiling != null && measure.value() > rule.ceiling()) {
        updated.put(measure.key(), Math.min(ceiling, measure.value()));
      }
    }
    List<String> inScope = stored.keySet().stream().filter(key -> scopeFiles.contains(new Key(key).file())).toList();
    int kept = (int) inScope.stream().filter(updated::containsKey).count();
    return new Outcome(fresh.stream().sorted().toList(), inScope.size() - kept, kept, updated);
  }

  /** The ceilings to freeze on day one: every measure above the default, at its current value. */
  public Map<String, Integer> freeze(BudgetRule rule, List<Measure> measures) {
    Map<String, Integer> ceilings = new TreeMap<>();
    for (Measure measure : measures) {
      if (measure.value() > rule.ceiling()) {
        ceilings.put(measure.key(), measure.value());
      }
    }
    return ceilings;
  }
}
