package com.fpetrola.cerco.autopsy;

import java.util.List;

/** An autopsy the gate is still waiting for: where its form is, and what the form still lacks. */
public record Owed(String id, String ruleId, String form, List<String> missing) {
}
