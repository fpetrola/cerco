package com.fpetrola.cerco.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict recursive-descent JSON reader with no dependencies, so the hook entry points run from the bare plugin
 * jar in a fresh JVM without Maven.
 */
public final class JsonParser {
  private final String text;
  private int at;

  private JsonParser(String text) {
    this.text = text;
  }

  public static JsonValue parse(String text) {
    JsonParser parser = new JsonParser(text);
    parser.skipWhitespace();
    JsonValue value = parser.value();
    parser.skipWhitespace();
    if (parser.at != text.length()) {
      throw parser.error("trailing characters");
    }
    return value;
  }

  private JsonValue value() {
    char c = peek();
    if (c == '{') {
      return object();
    }
    if (c == '[') {
      return array();
    }
    if (c == '"') {
      return new JsonValue.JsonString(string());
    }
    if (c == '-' || Character.isDigit(c)) {
      return number();
    }
    return literal();
  }

  private JsonValue object() {
    Map<String, JsonValue> members = new LinkedHashMap<>();
    at++;
    skipWhitespace();
    if (peek() == '}') {
      at++;
      return new JsonValue.JsonObject(members);
    }
    while (true) {
      skipWhitespace();
      if (peek() != '"') {
        throw error("expected a member name");
      }
      String name = string();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      members.put(name, value());
      skipWhitespace();
      if (peek() == ',') {
        at++;
        continue;
      }
      expect('}');
      return new JsonValue.JsonObject(members);
    }
  }

  private JsonValue array() {
    List<JsonValue> elements = new ArrayList<>();
    at++;
    skipWhitespace();
    if (peek() == ']') {
      at++;
      return new JsonValue.JsonArray(elements);
    }
    while (true) {
      skipWhitespace();
      elements.add(value());
      skipWhitespace();
      if (peek() == ',') {
        at++;
        continue;
      }
      expect(']');
      return new JsonValue.JsonArray(elements);
    }
  }

  private String string() {
    expect('"');
    StringBuilder out = new StringBuilder();
    while (true) {
      char c = next();
      if (c == '"') {
        return out.toString();
      }
      if (c == '\\') {
        out.append(escape());
        continue;
      }
      out.append(c);
    }
  }

  private char escape() {
    char c = next();
    if (c == 'u') {
      return unicode();
    }
    int index = "\"\\/bfnrt".indexOf(c);
    if (index < 0) {
      throw error("bad escape \\" + c);
    }
    return "\"\\/\b\f\n\r\t".charAt(index);
  }

  private char unicode() {
    if (at + 4 > text.length()) {
      throw error("truncated \\u escape");
    }
    String hex = text.substring(at, at + 4);
    at += 4;
    try {
      return (char) Integer.parseInt(hex, 16);
    } catch (NumberFormatException e) {
      throw error("bad \\u escape " + hex + ": " + e.getMessage());
    }
  }

  private JsonValue number() {
    int start = at;
    while (at < text.length() && "+-.eE0123456789".indexOf(text.charAt(at)) >= 0) {
      at++;
    }
    String digits = text.substring(start, at);
    try {
      return new JsonValue.JsonNumber(Double.parseDouble(digits));
    } catch (NumberFormatException e) {
      throw error("bad number " + digits + ": " + e.getMessage());
    }
  }

  private JsonValue literal() {
    if (text.startsWith("true", at)) {
      at += 4;
      return new JsonValue.JsonBoolean(true);
    }
    if (text.startsWith("false", at)) {
      at += 5;
      return new JsonValue.JsonBoolean(false);
    }
    if (text.startsWith("null", at)) {
      at += 4;
      return new JsonValue.JsonNull();
    }
    throw error("unexpected character '" + peek() + "'");
  }

  private void skipWhitespace() {
    while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
      at++;
    }
  }

  private char peek() {
    if (at >= text.length()) {
      throw error("unexpected end of input");
    }
    return text.charAt(at);
  }

  private char next() {
    char c = peek();
    at++;
    return c;
  }

  private void expect(char c) {
    if (next() != c) {
      throw error("expected '" + c + "'");
    }
  }

  private JsonException error(String what) {
    return new JsonException(what + " at offset " + at);
  }
}
