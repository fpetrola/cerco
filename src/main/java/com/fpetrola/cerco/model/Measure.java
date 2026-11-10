package com.fpetrola.cerco.model;

import java.util.Optional;

/** A size measured for a budget rule: so many lines in this file, or this method. */
public record Measure(String key, String file, int line, int value, String detail, Optional<Reading> reading) {

  public Measure(String key, String file, int line, int value, String detail) {
    this(key, file, line, value, detail, Optional.empty());
  }

  /** The same measure, saying which model what it measured is missing. */
  public Measure read(Reading reading) {
    return new Measure(key, file, line, value, detail, Optional.of(reading));
  }
}
