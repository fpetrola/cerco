package com.fpetrola.cerco.hooks;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Everything a pipe has to say, read to its end. */
public record StdinText(InputStream in) {

  public String text() {
    try {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the hook input", e);
    }
  }
}
