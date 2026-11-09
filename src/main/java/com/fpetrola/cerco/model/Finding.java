package com.fpetrola.cerco.model;

import java.util.Comparator;

/**
 * One violation of one rule. The key names it without a line number, so the baseline still recognises it after the
 * code above it moves; the detail is what a reader needs to find and fix it.
 */
public record Finding(String rule, String file, int line, String key, String detail) implements Comparable<Finding> {
  private static final Comparator<Finding> ORDER =
      Comparator.comparing(Finding::file).thenComparingInt(Finding::line).thenComparing(Finding::key);

  @Override
  public int compareTo(Finding other) {
    return ORDER.compare(this, other);
  }

  public String fileOfKey() {
    int bar = key.indexOf('|');
    return bar < 0 ? key : key.substring(0, bar);
  }
}
