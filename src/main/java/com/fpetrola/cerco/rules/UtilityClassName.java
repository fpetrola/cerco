package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.List;

/** A type whose name confesses it is a bag of functions: Util, Helper, Manager, Impl. */
public final class UtilityClassName extends FileRule {
  private final List<String> suffixes;

  public UtilityClassName(List<String> suffixes) {
    this.suffixes = suffixes;
  }

  @Override
  public String id() {
    return "no-utility-class";
  }

  @Override
  public String principle() {
    return "cohesion: a class is a concept with one responsibility, and its name says which";
  }

  @Override
  public String description() {
    return "a name ending in " + String.join(", ", suffixes) + " names a place to put functions, not a thing the "
        + "program is about; such classes attract whatever has no home and never cohere";
  }

  @Override
  public String remedy() {
    return "find the concept the functions act on and move them onto it, or name the object by what it is (a Parser, "
        + "a Ledger, a Route) and give it the state those functions kept passing around";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(TypeDeclaration.class).stream()
        .filter(type -> suffixes.stream().anyMatch(type.getNameAsString()::endsWith))
        .map(type -> finding(file, type, enclosing.nameOf(type),
            "type name `" + type.getNameAsString() + "` ends in a utility suffix"))
        .toList();
  }
}
