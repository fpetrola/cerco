package com.fpetrola.cerco.exemptions;

/** Whether an exemption still holds, and the one line that says how much room it has left. */
public record Verdict(boolean expired, String note) {
}
