package com.fpetrola.cerco.autopsy;

import com.fpetrola.cerco.baseline.Key;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** What one run took out of one rule's baseline: the keys of the findings that went away. */
public record Solved(String ruleId, List<String> keys) {

  public Set<String> files() {
    Set<String> files = new TreeSet<>();
    keys.forEach(key -> files.add(new Key(key).file()));
    return files;
  }
}
