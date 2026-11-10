package com.fpetrola.cerco.autopsy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The autopsies owed, under .cerco/autopsies where the agent cannot write: one file per autopsy, its rule on the
 * first line and the solved keys after it. The form the agent fills lives elsewhere; this is what says it is owed,
 * so deleting the form only brings it back.
 */
public final class Ledger {
  private static final String SUFFIX = ".owed";
  private final Path dir;

  public Ledger(Path dir) {
    this.dir = dir;
  }

  /** One owed autopsy: its id, the rule whose findings went away, and their keys. */
  public record Entry(String id, String ruleId, List<String> keys) {

    Solved solved() {
      return new Solved(ruleId, keys);
    }
  }

  public List<Entry> open() {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(file -> file.getFileName().toString().endsWith(SUFFIX)).sorted().map(this::entry).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("could not list the owed autopsies in " + dir, e);
    }
  }

  public Optional<Entry> openFor(String ruleId) {
    return open().stream().filter(entry -> entry.ruleId().equals(ruleId)).findFirst();
  }

  public boolean holds(String id) {
    return Files.exists(file(id));
  }

  /** Owes an autopsy for these keys, adding them to the one already open for the rule when there is one. */
  public Entry owe(String id, Solved solved) {
    Entry existing = openFor(solved.ruleId()).orElse(new Entry(id, solved.ruleId(), List.of()));
    TreeSet<String> keys = new TreeSet<>(existing.keys());
    keys.addAll(solved.keys());
    Entry entry = new Entry(existing.id(), solved.ruleId(), List.copyOf(keys));
    StringBuilder out = new StringBuilder(entry.ruleId()).append('\n');
    entry.keys().forEach(key -> out.append(key).append('\n'));
    try {
      Files.createDirectories(dir);
      Files.writeString(file(entry.id()), out.toString(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write the owed autopsy " + entry.id(), e);
    }
    return entry;
  }

  public void settle(String id) {
    try {
      Files.deleteIfExists(file(id));
    } catch (IOException e) {
      throw new UncheckedIOException("could not settle the autopsy " + id, e);
    }
  }

  private Entry entry(Path file) {
    String name = file.getFileName().toString();
    try {
      List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8).stream()
          .map(String::strip).filter(line -> !line.isEmpty()).toList());
      String ruleId = lines.isEmpty() ? "" : lines.remove(0);
      return new Entry(name.substring(0, name.length() - SUFFIX.length()), ruleId, List.copyOf(lines));
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the owed autopsy " + file, e);
    }
  }

  private Path file(String id) {
    return dir.resolve(id + SUFFIX);
  }
}
