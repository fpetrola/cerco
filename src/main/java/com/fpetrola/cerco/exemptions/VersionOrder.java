package com.fpetrola.cerco.exemptions;

import java.util.Comparator;

/** Orders versions numerically segment by segment, ignoring qualifiers such as -SNAPSHOT. */
public final class VersionOrder implements Comparator<String> {

  @Override
  public int compare(String left, String right) {
    String[] a = numeric(left);
    String[] b = numeric(right);
    for (int i = 0; i < Math.max(a.length, b.length); i++) {
      int difference = Integer.compare(segment(a, i), segment(b, i));
      if (difference != 0) {
        return difference;
      }
    }
    return 0;
  }

  private String[] numeric(String version) {
    return version.replaceAll("[^0-9.].*", "").split("\\.");
  }

  private int segment(String[] parts, int index) {
    if (index >= parts.length || parts[index].isEmpty()) {
      return 0;
    }
    return Integer.parseInt(parts[index]);
  }
}
