package com.fpetrola.cerco.project;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Walks one file and hands over every type it declares, nested ones included, each kind in its own way. */
final class Declarations extends VoidVisitorAdapter<Consumer<Declared>> {
  private final String file;

  Declarations(String file) {
    this.file = file;
  }

  @Override
  public void visit(ClassOrInterfaceDeclaration type, Consumer<Declared> learnt) {
    learnt.accept(declared(type, type.isInterface() ? Kind.INTERFACE : Kind.CLASS,
        Stream.concat(type.getExtendedTypes().stream(), type.getImplementedTypes().stream()),
        new Members(fields(type), operations(type), List.of())));
    super.visit(type, learnt);
  }

  @Override
  public void visit(EnumDeclaration type, Consumer<Declared> learnt) {
    learnt.accept(declared(type, Kind.ENUM, type.getImplementedTypes().stream(), new Members(fields(type),
        operations(type), type.getEntries().stream().map(EnumConstantDeclaration::getNameAsString).toList())));
    super.visit(type, learnt);
  }

  /** A record's parts are its fields, and each is answered by a method of the same name. */
  @Override
  public void visit(RecordDeclaration type, Consumer<Declared> learnt) {
    Map<String, String> fields = fields(type);
    List<Operation> operations = operations(type);
    type.getParameters().forEach(part -> {
      fields.put(part.getNameAsString(), simple(part.getType()));
      operations.add(new Operation(part.getNameAsString(), List.of(), simple(part.getType())));
    });
    learnt.accept(declared(type, Kind.RECORD, type.getImplementedTypes().stream(),
        new Members(fields, operations, List.of())));
    super.visit(type, learnt);
  }

  @Override
  public void visit(AnnotationDeclaration type, Consumer<Declared> learnt) {
    learnt.accept(declared(type, Kind.ANNOTATION, Stream.empty(),
        new Members(fields(type), operations(type), List.of())));
    super.visit(type, learnt);
  }

  /** What a type holds, however its kind declares it. */
  private record Members(Map<String, String> fields, List<Operation> operations, List<String> constants) {

    /** The declaration these are the members of. */
    Declared of(TypeDeclaration<?> type, Kind kind, List<String> supertypes, String top, String file) {
      return new Declared(type.getNameAsString(), type.getFullyQualifiedName().orElse(type.getNameAsString()), kind,
          supertypes, fields, operations, constants, top, file);
    }
  }

  private Declared declared(TypeDeclaration<?> type, Kind kind, Stream<ClassOrInterfaceType> supertypes,
      Members members) {
    return members.of(type, kind, supertypes.map(ClassOrInterfaceType::getNameAsString).toList(), top(type), file);
  }

  private List<Operation> operations(TypeDeclaration<?> type) {
    return new ArrayList<>(type.getMethods().stream().map(method -> new Operation(method.getNameAsString(),
        method.getParameters().stream().map(Parameter::getType).map(this::simple).toList(),
        simple(method.getType()))).toList());
  }

  private Map<String, String> fields(TypeDeclaration<?> type) {
    Map<String, String> fields = new LinkedHashMap<>();
    type.getFields().forEach(declaration -> declaration.getVariables()
        .forEach(field -> fields.put(field.getNameAsString(), simple(field.getType()))));
    return fields;
  }

  /** The outermost type a declaration sits in; itself when it is not nested. */
  private String top(TypeDeclaration<?> type) {
    TypeDeclaration<?> outer = type;
    Optional<TypeDeclaration> above = type.findAncestor(TypeDeclaration.class);
    while (above.isPresent()) {
      outer = above.get();
      above = outer.findAncestor(TypeDeclaration.class);
    }
    return outer.getNameAsString();
  }

  /** A type by its simple name, without type arguments; a primitive or an array as written. */
  private String simple(Type type) {
    return type.toClassOrInterfaceType().map(ClassOrInterfaceType::getNameAsString).orElseGet(type::asString);
  }
}
