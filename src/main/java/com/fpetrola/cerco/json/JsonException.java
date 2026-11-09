package com.fpetrola.cerco.json;

/** Malformed JSON: where, and what was expected. */
public final class JsonException extends RuntimeException {
  public JsonException(String message) {
    super(message);
  }
}
