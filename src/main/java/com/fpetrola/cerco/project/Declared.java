package com.fpetrola.cerco.project;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A type of the project as its source declares it: its simple name and its full one, what it extends or implements,
 * its fields with their declared types, its methods, its constants when it is an enum, the top-level type it sits in
 * and its file.
 */
public record Declared(String name, String qualified, Kind kind, List<String> supertypes, Map<String, String> fields,
    List<Operation> operations, List<String> constants, String top, String file) {

  /** The package the type is declared in: its full name up to the first type in it. */
  public String home() {
    int own = qualified.lastIndexOf("." + top + (nested() ? "." : ""));
    return own < 0 ? "" : qualified.substring(0, own);
  }

  /** Whether the type sits inside another, which can name it bare from anywhere it reaches. */
  public boolean nested() {
    return !top.equals(name);
  }

  /** The Maven module the type lives in: what comes before the source root in its path. */
  public String module() {
    return new Location(file).module();
  }

  public boolean isInterface() {
    return kind.onlyPromises();
  }

  public boolean isEnum() {
    return kind.isClosed();
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
