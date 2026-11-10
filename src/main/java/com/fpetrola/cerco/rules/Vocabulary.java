package com.fpetrola.cerco.rules;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The names the project itself declares, methods and fields: without types, what tells `z80.setRegA()` (ours, a
 * model asked from outside) from `button.setText()` (Swing's, not ours to change). Homonyms of libraries slip in.
 */
public final class Vocabulary {
  private final Set<String> methods = new HashSet<>();
  private final Map<String, Set<String>> fields = new HashMap<>();

  public void learn(CompilationUnit unit) {
    unit.findAll(MethodDeclaration.class).forEach(method -> methods.add(method.getNameAsString()));
    for (TypeDeclaration<?> top : unit.getTypes()) {
      top.findAll(FieldDeclaration.class).forEach(declaration -> declaration.getVariables().forEach(field ->
          fields.computeIfAbsent(field.getNameAsString(), k -> new HashSet<>()).add(top.getNameAsString())));
    }
  }

  /** Whether a field by that name is declared only inside this top-level type (its own nested parts included). */
  public boolean onlyIn(String field, String topLevelType) {
    return fields.getOrDefault(field, Set.of()).equals(Set.of(topLevelType));
  }

  public boolean declaresMethod(String name) {
    return methods.contains(name);
  }

  public boolean declaresField(String name) {
    return fields.containsKey(name);
  }
}
