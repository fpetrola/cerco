package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A type that keeps two or more maps with the same kind of key: what it knows about one key is spread over them,
 * and each has to be kept in step with the others. Candidate from the plan of model smells.
 */
public final class MapsKeyedAlike extends FileRule {
  private static final int MAPS = 2;

  @Override
  public String id() {
    return "maps-keyed-alike";
  }

  @Override
  public String principle() {
    return "what is known about one thing is kept in one place, by an object of its own";
  }

  @Override
  public String description() {
    return "a type holds " + MAPS + " or more maps with the same kind of key: what belongs to one key is spread over "
        + "them, and every change has to keep them in step";
  }

  @Override
  public String remedy() {
    return "a class for what the maps hold about one key, kept in a single map; or, when the key is a type of the "
        + "project, fields of the key itself";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a map of maps, or a record used only as the value with no behaviour",
        new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
            "the maps hold, each, one aspect of the same thing",
            "a class for that thing, in one map; or fields of the key when the key is the project's"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      Map<String, List<String>> byKey = new TreeMap<>();
      type.getFields().forEach(field -> field.getVariables().forEach(variable -> keyOf(variable)
          .ifPresent(key -> byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(variable.getNameAsString()))));
      byKey.forEach((key, maps) -> {
        if (maps.size() >= MAPS) {
          findings.add(finding(file, type, enclosing.nameOf(type) + "|maps keyed by " + key, enclosing.nameOf(type)
              + " keeps " + maps.size() + " maps keyed by `" + key + "`: " + String.join(", ", maps)));
        }
      });
    }
    return findings;
  }

  /** The type of the key, when the field is a map that says it. */
  private Optional<String> keyOf(VariableDeclarator field) {
    return field.getType().toClassOrInterfaceType().filter(type -> type.getNameAsString().endsWith("Map"))
        .flatMap(ClassOrInterfaceType::getTypeArguments).filter(arguments -> arguments.size() == 2)
        .map(arguments -> arguments.get(0).asString());
  }
}
