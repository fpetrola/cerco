package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.List;

/** A map from classes to behaviour: a switch on the type, kept in a data structure. */
public final class TypeKeyedMap extends FileRule {

  @Override
  public String id() {
    return "no-type-keyed-map";
  }

  @Override
  public String principle() {
    return "polymorphism: behaviour lives in the type, not in a table that looks the type up";
  }

  @Override
  public String description() {
    return "a Map whose key is a Class (a registry of handlers by type, a service locator by interface) is a switch "
        + "on the type with the cases moved into a data structure; the lookup still needs a cast to use what it finds";
  }

  @Override
  public String remedy() {
    return "put the behaviour on the type itself, or on an adapter you construct at the boundary where the object "
        + "enters; if the set of types is open, a visitor the types accept keeps the dispatch inside them";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(ClassOrInterfaceType.class).stream()
        .filter(this::isMapKeyedByClass)
        .map(type -> finding(file, type, enclosing.of(type) + "|" + clipped(type.toString()),
            "`" + clipped(type.toString()) + "` in " + enclosing.of(type)))
        .toList();
  }

  private boolean isMapKeyedByClass(ClassOrInterfaceType type) {
    if (!type.getNameAsString().endsWith("Map")) {
      return false;
    }
    return type.getTypeArguments()
        .filter(arguments -> !arguments.isEmpty())
        .map(arguments -> arguments.get(0).asString().matches("Class(<.*>)?"))
        .orElse(false);
  }
}
