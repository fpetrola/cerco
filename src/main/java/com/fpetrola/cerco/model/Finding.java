package com.fpetrola.cerco.model;

import java.util.Comparator;
import java.util.Optional;

/**
 * One violation of one rule. The key names it without a line number, so the baseline still recognises it after the
 * code above it moves; the detail is what a reader needs to find and fix it; the reading, when the rule could tell,
 * is which model the code at this place is missing. The reading is advice: it never enters the key.
 */
public record Finding(String rule, String file, int line, String key, String detail, Optional<Reading> reading)
    implements Comparable<Finding> {
  private static final Comparator<Finding> ORDER =
      Comparator.comparing(Finding::file).thenComparingInt(Finding::line).thenComparing(Finding::key);

  public Finding(String rule, String file, int line, String key, String detail) {
    this(rule, file, line, key, detail, Optional.empty());
  }

  /** The same finding, saying which model the code at this place points to. */
  public Finding read(Reading reading) {
    return new Finding(rule, file, line, key, detail, Optional.of(reading));
  }

  @Override
  public int compareTo(Finding other) {
    return ORDER.compare(this, other);
  }

  public String fileOfKey() {
    int bar = key.indexOf('|');
    return bar < 0 ? key : key.substring(0, bar);
  }
}
