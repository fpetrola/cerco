package com.fpetrola.cerco.json;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A parsed JSON value. Every kind answers the same questions, so a caller never has to ask what it is:
 * a string answers {@link #asString()}, everything else answers empty.
 */
public interface JsonValue {

  default Optional<String> asString() {
    return Optional.empty();
  }

  default Optional<Boolean> asBoolean() {
    return Optional.empty();
  }

  default Optional<Double> asNumber() {
    return Optional.empty();
  }

  default Optional<JsonValue> member(String name) {
    return Optional.empty();
  }

  default List<JsonValue> elements() {
    return List.of();
  }

  default boolean isNull() {
    return false;
  }

  /** The string reached by following member names, if every step exists and the leaf is a string. */
  default Optional<String> string(String... path) {
    Optional<JsonValue> cursor = Optional.of(this);
    for (String step : path) {
      cursor = cursor.flatMap(value -> value.member(step));
    }
    return cursor.flatMap(JsonValue::asString);
  }

  /** The boolean reached by following member names, false when absent. */
  default boolean flag(String... path) {
    Optional<JsonValue> cursor = Optional.of(this);
    for (String step : path) {
      cursor = cursor.flatMap(value -> value.member(step));
    }
    return cursor.flatMap(JsonValue::asBoolean).orElse(false);
  }

  record JsonObject(Map<String, JsonValue> members) implements JsonValue {
    @Override
    public Optional<JsonValue> member(String name) {
      return Optional.ofNullable(members.get(name));
    }
  }

  record JsonArray(List<JsonValue> elements) implements JsonValue {
  }

  record JsonString(String value) implements JsonValue {
    @Override
    public Optional<String> asString() {
      return Optional.of(value);
    }
  }

  record JsonNumber(double value) implements JsonValue {
    @Override
    public Optional<Double> asNumber() {
      return Optional.of(value);
    }
  }

  record JsonBoolean(boolean value) implements JsonValue {
    @Override
    public Optional<Boolean> asBoolean() {
      return Optional.of(value);
    }
  }

  record JsonNull() implements JsonValue {
    @Override
    public boolean isNull() {
      return true;
    }
  }
}
