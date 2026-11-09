package com.fpetrola.cerco.model;

/** A size measured for a budget rule: so many lines in this file, or this method. */
public record Measure(String key, String file, int line, int value, String detail) {
}
