package com.fpetrola.cerco.baseline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The frozen state of the project on the day the gate arrived, one text file per rule under .cerco/baseline, written
 * to be read in a diff: a count, a tab, the key. The store only ever loses entries.
 */
public final class BaselineStore {
  private final Path dir;

  public BaselineStore(Path dir) {
    this.dir = dir;
  }

  public Path dir() {
    return dir;
  }

  public boolean exists() {
    return Files.exists(dir.resolve("frozen"));
  }

  public Optional<String> frozenOn() {
    return exists() ? Optional.of(frozenLines().get(0)) : Optional.empty();
  }

  /** The rules the freeze covered. A rule missing here arrived after the freeze and has no baseline yet. */
  public Set<String> frozenRules() {
    return exists() ? new TreeSet<>(frozenLines().subList(1, frozenLines().size())) : Set.of();
  }

  public void markFrozen(LocalDate today, Collection<String> ruleIds) {
    StringBuilder out = new StringBuilder(today.toString()).append('\n');
    new TreeSet<>(ruleIds).forEach(id -> out.append(id).append('\n'));
    write(dir.resolve("frozen"), out.toString());
  }

  /** Adds rules to the freeze, keeping its date: how a rule that arrived later gets its baseline. */
  public void extendFrozen(Collection<String> ruleIds) {
    Set<String> all = new TreeSet<>(frozenRules());
    all.addAll(ruleIds);
    markFrozen(LocalDate.parse(frozenLines().get(0)), all);
  }

  private List<String> frozenLines() {
    return Stream.of(read(dir.resolve("frozen")).split("\n")).map(String::strip).filter(line -> !line.isEmpty())
        .toList();
  }

  public Multiset read(String ruleId) {
    Multiset set = new Multiset();
    for (String[] fields : rows(dir.resolve(ruleId + ".txt"))) {
      set.add(fields[1], Integer.parseInt(fields[0]));
    }
    return set;
  }

  public void write(String ruleId, Multiset set) {
    StringBuilder out = new StringBuilder("# cerco baseline v1 " + ruleId + ": count, key\n");
    set.view().forEach((key, times) -> out.append(times).append('\t').append(key).append('\n'));
    writeOrRemove(dir.resolve(ruleId + ".txt"), out.toString(), set.view().isEmpty());
  }

  public Map<String, Integer> readCeilings(String ruleId) {
    Map<String, Integer> ceilings = new TreeMap<>();
    for (String[] fields : rows(dir.resolve(ruleId + ".ceilings"))) {
      ceilings.put(fields[1], Integer.parseInt(fields[0]));
    }
    return ceilings;
  }

  public void writeCeilings(String ruleId, Map<String, Integer> ceilings) {
    StringBuilder out = new StringBuilder("# cerco baseline v1 " + ruleId + ": ceiling, key\n");
    new TreeMap<>(ceilings).forEach((key, ceiling) -> out.append(ceiling).append('\t').append(key).append('\n'));
    writeOrRemove(dir.resolve(ruleId + ".ceilings"), out.toString(), ceilings.isEmpty());
  }

  private List<String[]> rows(Path file) {
    List<String[]> rows = new ArrayList<>();
    if (!Files.exists(file)) {
      return rows;
    }
    for (String line : read(file).split("\n")) {
      if (!line.isBlank() && !line.startsWith("#")) {
        rows.add(line.split("\t", 2));
      }
    }
    return rows;
  }

  private String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the baseline file " + file, e);
    }
  }

  private void writeOrRemove(Path file, String content, boolean empty) {
    try {
      if (empty) {
        Files.deleteIfExists(file);
        return;
      }
      write(file, content);
    } catch (IOException e) {
      throw new UncheckedIOException("could not remove the baseline file " + file, e);
    }
  }

  private void write(Path file, String content) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write the baseline file " + file, e);
    }
  }
}
