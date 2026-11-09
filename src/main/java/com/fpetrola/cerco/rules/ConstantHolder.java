package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import java.util.List;

/** A type that is only a bag of constants: a utility class whose name does not confess it. */
public final class ConstantHolder extends FileRule {

  @Override
  public String id() {
    return "no-constant-holder";
  }

  @Override
  public String principle() {
    return "cohesion: a type is behaviour around state, not a namespace for values";
  }

  @Override
  public String description() {
    return "a class or interface with fields and no methods, every field static final, is a utility class by another "
        + "name: the behaviour that gives those constants meaning lives somewhere else and reads them from outside";
  }

  @Override
  public String remedy() {
    return "move each constant next to the behaviour that uses it, or make the constants objects (an enum, a value "
        + "type) that carry that behaviour; a marker string belongs to the class that reads and writes the mark";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(ClassOrInterfaceDeclaration.class).stream()
        .filter(this::isConstantHolder)
        .map(type -> finding(file, type, enclosing.nameOf(type),
            "type `" + type.getNameAsString() + "` holds " + type.getFields().size() + " constant(s) and no behaviour"))
        .toList();
  }

  private boolean isConstantHolder(ClassOrInterfaceDeclaration type) {
    List<FieldDeclaration> fields = type.getFields();
    if (fields.isEmpty() || !type.getMethods().isEmpty()) {
      return false;
    }
    boolean onlyConstants = fields.stream().allMatch(field -> field.isStatic() && field.isFinal())
        || type.isInterface();
    boolean onlyFieldsAndConstructors = type.getMembers().stream()
        .allMatch(member -> member.isFieldDeclaration() || isPrivateConstructor(member));
    return onlyConstants && onlyFieldsAndConstructors;
  }

  private boolean isPrivateConstructor(BodyDeclaration<?> member) {
    return member.toConstructorDeclaration().map(constructor -> constructor.isPrivate()).orElse(false);
  }
}
