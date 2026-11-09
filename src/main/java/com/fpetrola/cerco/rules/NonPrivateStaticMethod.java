package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.List;

/** A static method callers reach from outside: a procedure, unless it is the factory of its own type. */
public final class NonPrivateStaticMethod extends FileRule {
  private final List<String> factoryPrefixes;

  public NonPrivateStaticMethod(List<String> factoryPrefixes) {
    this.factoryPrefixes = factoryPrefixes;
  }

  @Override
  public String id() {
    return "no-static-method";
  }

  @Override
  public String principle() {
    return "objects over procedures: behaviour lives with the state it works on";
  }

  @Override
  public String description() {
    return "a non-private static method is a procedure: it has no object, so its inputs travel as parameters and "
        + "its callers cannot substitute it; factories of the enclosing type and main are the exceptions";
  }

  @Override
  public String remedy() {
    return "make it an instance method of the type whose data it uses, or turn the parameters it keeps receiving "
        + "into an object with this as its method; a factory keeps a factory name (" + String.join(", ", factoryPrefixes)
        + ") or returns its own type";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(MethodDeclaration.class).stream()
        .filter(method -> method.isStatic() && !method.isPrivate() && !allowed(method))
        .map(method -> finding(file, method, enclosing.signatureOf(method),
            "non-private static method " + enclosing.signatureOf(method)))
        .toList();
  }

  private boolean allowed(MethodDeclaration method) {
    String name = method.getNameAsString();
    if (name.equals("main")) {
      return true;
    }
    boolean factoryName = factoryPrefixes.stream().anyMatch(prefix -> name.equals(prefix)
        || (name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length()))));
    String returned = method.getType().asString().replaceAll("<.*", "");
    boolean returnsOwnType = method.findAncestor(TypeDeclaration.class)
        .map(type -> type.getNameAsString().equals(returned)).orElse(false);
    return factoryName || returnsOwnType;
  }
}
