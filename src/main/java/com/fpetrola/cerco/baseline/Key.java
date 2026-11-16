package com.fpetrola.cerco.baseline;

/** A baseline key: the file it belongs to, a bar, and what the rule remembers about the spot. */
public record Key(String text) {

  public String file() {
    int bar = text.indexOf('|');
    return bar < 0 ? text : text.substring(0, bar);
  }

  /** What the rule remembers about the spot, wherever its file lives: the same code moved keeps it. */
  public String spot() {
    return text.substring(text.indexOf('|') + 1);
  }
}
