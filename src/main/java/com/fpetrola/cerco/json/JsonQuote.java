package com.fpetrola.cerco.json;

/** A string as JSON writes it: quoted, with the characters JSON cannot hold raw escaped. */
public record JsonQuote(String raw) {

  public String quoted() {
    StringBuilder out = new StringBuilder("\"");
    for (char c : raw.toCharArray()) {
      out.append(escaped(c));
    }
    return out.append('"').toString();
  }

  private String escaped(char c) {
    if (c == '"') {
      return "\\\"";
    }
    if (c == '\\') {
      return "\\\\";
    }
    if (c == '\n') {
      return "\\n";
    }
    if (c == '\r') {
      return "\\r";
    }
    if (c == '\t') {
      return "\\t";
    }
    if (c < 0x20) {
      return String.format("\\u%04x", (int) c);
    }
    return String.valueOf(c);
  }
}
