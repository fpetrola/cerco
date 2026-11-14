package com.fpetrola.cerco.hooks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The red report the Stop hook last blocked with, kept under target/cerco. When the agent comes back from that block
 * with the very same red, it had nothing left to do there: blocking again would only loop, so the session ends and
 * a person reads the report. A red that changed is progress, and blocks again.
 */
public record LastStop(Path file) {

  /** Whether this report is the one the last block showed, timings aside. */
  public boolean repeats(String report) {
    try {
      return Files.isRegularFile(file) && same(Files.readString(file), report);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + file, e);
    }
  }

  public void blockedWith(String report) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, report, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write " + file, e);
    }
  }

  public void clear() {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      throw new UncheckedIOException("could not delete " + file, e);
    }
  }

  private boolean same(String before, String now) {
    return before.replaceAll("\\d+ ms", "").equals(now.replaceAll("\\d+ ms", ""));
  }
}
