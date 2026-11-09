package com.fpetrola.cerco.baseline;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Keys with how many times each occurs: a baseline remembers three identical violations as three. */
public final class Multiset {
  private final TreeMap<String, Integer> counts = new TreeMap<>();

  public static Multiset of(Collection<String> keys) {
    Multiset set = new Multiset();
    keys.forEach(set::add);
    return set;
  }

  public void add(String key) {
    add(key, 1);
  }

  public void add(String key, int times) {
    if (times > 0) {
      counts.merge(key, times, Integer::sum);
    }
  }

  public int count(String key) {
    return counts.getOrDefault(key, 0);
  }

  public int total() {
    return counts.values().stream().mapToInt(Integer::intValue).sum();
  }

  public Set<String> keys() {
    return Collections.unmodifiableSet(counts.keySet());
  }

  public Map<String, Integer> view() {
    return Collections.unmodifiableMap(counts);
  }

  public Multiset where(Predicate<String> keep) {
    Multiset set = new Multiset();
    counts.forEach((key, times) -> {
      if (keep.test(key)) {
        set.add(key, times);
      }
    });
    return set;
  }

  public boolean sameAs(Multiset other) {
    return counts.equals(other.counts);
  }
}
