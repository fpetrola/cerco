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
  private final Map<String, List<Declared>> homonyms = new HashMap<>();
  private final Map<String, Set<String>> under = new HashMap<>();
  private boolean whole;
  private boolean recalled;

  /** What a file declares; when the project came from an earlier run, in place of what that file declared then. */
  public void learn(ParsedFile file) {
    if (recalled) {
      forget(file.name());
    }
    file.unit().ifPresent(unit -> new Declarations(file.name()).visit(unit, this::declare));
  }

  /** What an earlier whole run learnt, taken as the whole project except for the files learnt again after it. */
  public void recall(List<Declared> remembered) {
    remembered.forEach(this::declare);
    recalled = true;
    whole = true;
  }

  /** Everything the project declares, for a later run to recall. */
  public List<Declared> everything() {
    return List.copyOf(everyone);
  }

  private void forget(String file) {
    List<Declared> kept = everyone.stream().filter(type -> !type.file().equals(file)).toList();
    everyone.clear();
    homonyms.clear();
    types.clear();
    under.clear();
    kept.forEach(this::declare);
  }

  private void declare(Declared type) {
    everyone.add(type);
    homonyms.computeIfAbsent(type.name(), k -> new ArrayList<>()).add(type);
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

  /** Whether the type a file names is one of the project's, going by its full name when the file says it. */
  public boolean declares(TypeName type) {
    return homonyms.getOrDefault(type.simple(), List.of()).stream().anyMatch(type::means);
  }

  public Optional<Declared> declared(String type) {
    return Optional.ofNullable(types.get(type));
  }

  /** The project types that extend or implement a type directly. */
  public Set<String> directlyBelow(String type) {
    return under.getOrDefault(type, Set.of());
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

  /** The visitors a family accepts: those any of its members declares `accept` for, by name. */
  public Set<String> visitorsOf(Family family) {
    return family.members().stream().map(this::declared).flatMap(Optional::stream).map(Declared::visitor)
        .flatMap(Optional::stream).collect(Collectors.toCollection(TreeSet::new));
  }

  /** The top-level types of the project that declare a method by that name taking that many arguments. */
  public Set<String> declaring(String operation, int arguments) {
    return everyone.stream().filter(type -> type.operations().stream()
            .anyMatch(declared -> declared.name().equals(operation) && declared.parameters().size() == arguments))
        .map(Declared::top).collect(Collectors.toCollection(TreeSet::new));
  }

  /** The enums of the project that have all those constants, by name. */
  public List<Declared> enumsWith(Set<String> constants) {
    return types.values().stream().filter(Declared::isEnum).filter(type -> type.constants().containsAll(constants))
        .sorted(Comparator.comparing(Declared::name)).toList();
  }

  /** Whether some type of the project has a field by that name. */
  public boolean declaresField(String name) {
    return everyone.stream().anyMatch(type -> type.fields().containsKey(name));
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
