package com.fpetrola.cerco.project;

import com.fpetrola.cerco.model.ParsedFile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What the gated project declares, learnt file by file: its types by simple name, with their supertypes, members and
 * module. A rule asks it when it concludes, which is when a run has fed every file; before that the project knows
 * only the files fed so far, and outside a full run only the files of that run, so it says whether it is whole.
 * Two types with one simple name are one to it: the first declared answers.
 */
public final class Project {
  private final Map<String, Declared> types = new LinkedHashMap<>();
  private final List<Declared> everyone = new ArrayList<>();
  private final Map<String, Set<String>> under = new HashMap<>();
  private boolean whole;

  public void learn(ParsedFile file) {
    file.unit().ifPresent(unit -> new Declarations(file.name()).visit(unit, this::declare));
  }

  private void declare(Declared type) {
    everyone.add(type);
    if (types.putIfAbsent(type.name(), type) == null) {
      type.supertypes().forEach(parent -> under.computeIfAbsent(parent, k -> new TreeSet<>()).add(type.name()));
    }
  }

  /** Every file of the project was learnt: what it does not declare is somebody else's. */
  public void markWhole() {
    whole = true;
  }

  public boolean isWhole() {
    return whole;
  }

  public boolean declares(String type) {
    return types.containsKey(type);
  }

  public Optional<Declared> declared(String type) {
    return Optional.ofNullable(types.get(type));
  }

  /** A type and everything below it in the project. */
  public Set<String> below(String type) {
    Set<String> found = new TreeSet<>();
    Deque<String> pending = new ArrayDeque<>(List.of(type));
    while (!pending.isEmpty()) {
      String current = pending.pop();
      if (found.add(current)) {
        pending.addAll(under.getOrDefault(current, Set.of()));
      }
    }
    return found;
  }

  /**
   * The largest hierarchy of the project a type belongs to: of the topmost project types above it, the one with the
   * most under it. A type nothing of the project is above or below is a family of one.
   */
  public Family family(String type) {
    return tops(type).stream().map(top -> new Family(top, below(top)))
        .max(Comparator.comparingInt((Family family) -> family.members().size())
            .thenComparing(Family::root, Comparator.reverseOrder()))
        .orElseGet(() -> new Family(type, Set.of(type)));
  }

  /** The visitor a family accepts: the one its top, or failing that any of its members, declares `accept` for. */
  public Optional<String> visitorOf(Family family) {
    return family.members().stream().sorted(Comparator.comparing((String member) -> !member.equals(family.root())))
        .map(this::declared).flatMap(Optional::stream).map(Declared::visitor).flatMap(Optional::stream).findFirst();
  }

  /** The top-level types of the project that declare a method by that name taking that many arguments. */
  public Set<String> declaring(String operation, int arguments) {
    return everyone.stream().filter(type -> type.operations().stream()
            .anyMatch(declared -> declared.name().equals(operation) && declared.parameters().size() == arguments))
        .map(Declared::top).collect(Collectors.toCollection(TreeSet::new));
  }

  /** The enum of the project that has all those constants, the first by name when several do. */
  public Optional<Declared> enumWith(Set<String> constants) {
    return types.values().stream().filter(Declared::isEnum).filter(type -> type.constants().containsAll(constants))
        .min(Comparator.comparing(Declared::name));
  }

  /** The topmost project types above a type; the type itself when nothing of the project is above it. */
  private Set<String> tops(String type) {
    Set<String> tops = new TreeSet<>();
    Set<String> seen = new TreeSet<>();
    Deque<String> pending = new ArrayDeque<>(List.of(type));
    while (!pending.isEmpty()) {
      String current = pending.pop();
      if (seen.add(current)) {
        List<String> above = declared(current).map(Declared::supertypes).orElse(List.of()).stream()
            .filter(this::declares).toList();
        if (above.isEmpty()) {
          tops.add(current);
        }
        pending.addAll(above);
      }
    }
    return tops;
  }
}
