package com.fpetrola.cerco.exemptions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** The SHA-256 of some bytes, as the approval file records it. */
public record Digest(byte[] bytes) {

  public static Digest of(String text) {
    return new Digest(text.getBytes(StandardCharsets.UTF_8));
  }

  public String hex() {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is part of every JVM", e);
    }
  }
}
