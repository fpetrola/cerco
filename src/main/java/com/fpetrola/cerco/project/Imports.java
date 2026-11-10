package com.fpetrola.cerco.project;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** How one file says whose a simple name is: the types it imports by name, and where it looks for the rest. */
public final class Imports {
  private final Map<String, String> byName = new HashMap<>();
  private final TypeName.Reach reach;

  public Imports(CompilationUnit unit) {
    unit.getImports().stream().filter(one -> !one.isAsterisk() && !one.isStatic())
        .forEach(one -> byName.put(one.getName().getIdentifier(), one.getNameAsString()));
    Set<String> onDemand = unit.getImports().stream().filter(one -> one.isAsterisk() && !one.isStatic())
        .map(NodeWithName::getNameAsString).collect(Collectors.toSet());
    this.reach = new TypeName.Reach(unit.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse(""),
        onDemand);
  }

  /** A type as this file names it: with the full name it writes out, or the one it imports. */
  public TypeName name(ClassOrInterfaceType type) {
    Optional<String> written = type.getScope().map(scope -> type.getNameWithScope());
    return new TypeName(type.getNameAsString(),
        written.or(() -> Optional.ofNullable(byName.get(type.getNameAsString()))), reach);
  }

  /** A type this file declares, which needs no import to be itself. */
  public TypeName name(TypeDeclaration<?> type) {
    return new TypeName(type.getNameAsString(), type.getFullyQualifiedName(), reach);
  }

  /** A type the language itself provides, as `Object`. */
  public TypeName ofTheLanguage(String simple) {
    return new TypeName(simple, Optional.of("java.lang." + simple), reach);
  }
}
