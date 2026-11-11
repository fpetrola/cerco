package com.fpetrola.cerco.autopsy;

import java.util.List;

/** An autopsy the gate is still waiting for: where its form is, and what the form still lacks. */
public record Owed(String id, String ruleId, String form, List<String> missing) {

  /** What the report says of it: its form, its rule, and under them each thing the form still lacks. */
  public String told() {
    StringBuilder out = new StringBuilder("  ").append(form).append("  (").append(ruleId).append(")\n");
    missing.forEach(lacking -> out.append("    - ").append(lacking).append('\n'));
    return out.toString();
  }
}
