package com.fpetrola.cerco.project;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A type of the project as its source declares it: its simple name, what it extends or implements, its fields with
 * their declared types, its methods, its constants when it is an enum, the top-level type it sits in and its file.
 */
public record Declared(String name, Kind kind, List<String> supertypes, Map<String, String> fields,
    List<Operation> operations, List<String> constants, String top, String file) {

  /** The Maven module the type lives in: what comes before the source root in its path. */
  public String module() {
    int sources = file.indexOf("/src/");
    return sources < 0 ? "" : file.substring(0, sources);
  }

  public boolean isInterface() {
    return kind == Kind.INTERFACE;
  }

  public boolean isEnum() {
    return kind == Kind.ENUM;
  }

  /** The visitor this type accepts, when it declares `accept(SomethingVisitor)`. */
  public Optional<String> visitor() {
    return operations.stream().filter(operation -> operation.name().equals("accept"))
        .filter(operation -> operation.parameters().size() == 1).map(operation -> operation.parameters().get(0))
        .filter(parameter -> parameter.endsWith("Visitor")).findFirst();
  }

  public boolean declares(String operation) {
    return operations.stream().anyMatch(declared -> declared.name().equals(operation));
  }

  /** The declared type of a field of this type, by simple name. */
  public Optional<String> field(String name) {
    return Optional.ofNullable(fields.get(name));
  }

  /** What a method of this type answers with, when only one method has that name. */
  public Optional<String> answer(String operation) {
    List<String> answers = operations.stream().filter(declared -> declared.name().equals(operation))
        .map(Operation::returns).distinct().toList();
    return answers.size() == 1 ? Optional.of(answers.get(0)) : Optional.empty();
  }
}
