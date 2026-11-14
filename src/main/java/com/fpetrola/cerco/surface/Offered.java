package com.fpetrola.cerco.surface;

import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithAccessModifiers;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What one file offers to others: its types, top-level and nested, with the members each declares itself. A method
 * marked as overriding is not offered here, its supertype offers it; neither is the accessor a record writes for one
 * of its components, nor anything in a local or anonymous class.
 */
public final class Offered {
  private static final Pattern FUNCTION = Pattern.compile(
      "([\\w.]*\\.)?(Runnable|Callable|\\w*(Function|Supplier|Consumer|Predicate|Operator))(<.*)?");
  private final ParsedFile file;
  private final List<Member> members = new ArrayList<>();

  public Offered(ParsedFile file) {
    this.file = file;
    file.unit().ifPresent(unit -> unit.getTypes().forEach(type -> walk(type, Openness.PUBLIC, false)));
  }

  public List<Member> members() {
    return List.copyOf(members);
  }

  private void walk(TypeDeclaration<?> type, Openness enclosing, boolean insideAPromise) {
    Openness open = openness(type, insideAPromise).within(enclosing);
    boolean promises = type.isClassOrInterfaceDeclaration() && type.asClassOrInterfaceDeclaration().isInterface();
    boolean onlyPromises = promises || type.isAnnotationDeclaration();
    String named = type.getFullyQualifiedName().orElse(type.getNameAsString());
    members.add(new Member(named + " " + kindOf(type), type.getNameAsString(), true, promises, false, open,
        Set.of(), file.name(), line(type)));
    type.getMembers().forEach(member -> offer(named, member, open, onlyPromises, promises));
    type.toRecordDeclaration().ifPresent(record -> components(named, record, open));
    type.toEnumDeclaration().ifPresent(enumeration -> constants(named, enumeration, open));
  }

  private void offer(String type, BodyDeclaration<?> member, Openness open, boolean onlyPromises, boolean promises) {
    member.toTypeDeclaration().ifPresent(nested -> walk(nested, open, onlyPromises));
    member.toMethodDeclaration().filter(method -> !method.isAnnotationPresent("Override") && !accessor(method))
        .ifPresent(method -> callable(type + "#" + method.getNameAsString(), method, method.getType(), open,
            onlyPromises, promises));
    member.toConstructorDeclaration().filter(constructor -> !constructor.findAncestor(TypeDeclaration.class)
        .map(TypeDeclaration::isEnumDeclaration).orElse(false)).ifPresent(constructor -> callable(type + "#<init>", constructor, null, open,
        onlyPromises, false));
    member.toFieldDeclaration().ifPresent(field -> fields(type, field, open, onlyPromises));
  }

  private void callable(String named, CallableDeclaration<?> callable, Type returns, Openness open,
      boolean onlyPromises, boolean promises) {
    String parameters = callable.getParameters().stream().map(this::written).collect(Collectors.joining(", "));
    String answer = returns == null ? "" : ": " + returns.asString();
    String generics = callable.getTypeParameters().isEmpty() ? "" : "<" + callable.getTypeParameters().stream()
        .map(Node::toString).collect(Collectors.joining(", ")) + "> ";
    Set<Hint> hints = hints(callable.getParameters(), returns);
    members.add(new Member(generics + named + "(" + parameters + ")" + answer, callable.getNameAsString(), false,
        promises, entrance(callable), openness(callable, onlyPromises).within(open), hints, file.name(), line(callable)));
  }

  /** A way into the module that needs no instance: a constructor, or a static method. */
  private boolean entrance(CallableDeclaration<?> callable) {
    return callable.isConstructorDeclaration() || callable.isStatic();
  }

  private void components(String type, RecordDeclaration record, Openness open) {
    record.getParameters().forEach(component -> members.add(new Member(type + "#" + component.getNameAsString()
        + ": " + component.getType().asString(), component.getNameAsString(), false, false, false, open,
        hints(new NodeList<>(), component.getType()), file.name(), line(component))));
  }

  private void constants(String type, EnumDeclaration enumeration, Openness open) {
    enumeration.getEntries().forEach(constant -> members.add(new Member(type + "#" + constant.getNameAsString(),
        constant.getNameAsString(), false, false, false, open, Set.of(), file.name(), line(constant))));
  }

  private void fields(String type, FieldDeclaration field, Openness open, boolean onlyPromises) {
    Openness own = openness(field, onlyPromises).within(open);
    field.getVariables().forEach(variable -> members.add(new Member(type + "#" + variable.getNameAsString() + ": "
        + variable.getType().asString(), variable.getNameAsString(), false, false, false, own,
        hints(new NodeList<>(), variable.getType()), file.name(), line(variable))));
  }

  /** A method a record would write anyway: no parameters and the name of one of its components. */
  private boolean accessor(MethodDeclaration method) {
    return method.getParameters().isEmpty() && method.findAncestor(RecordDeclaration.class)
        .filter(record -> record.getParameters().stream()
            .anyMatch(component -> component.getNameAsString().equals(method.getNameAsString())))
        .isPresent();
  }

  /** What the modifiers say, where an interface or an annotation makes everything public that is not private. */
  private Openness openness(NodeWithAccessModifiers<?> declared, boolean insideAPromise) {
    if (declared.isPrivate()) {
      return Openness.PRIVATE;
    }
    return declared.isPublic() || insideAPromise ? Openness.PUBLIC : Openness.PACKAGE;
  }

  private Set<Hint> hints(NodeList<Parameter> parameters, Type returns) {
    Set<Hint> hints = EnumSet.noneOf(Hint.class);
    Stream.concat(parameters.stream().map(Parameter::getType), Stream.ofNullable(returns)).map(Type::asString)
        .forEach(written -> {
          if (written.matches(".*\\bString\\b.*")) {
            hints.add(Hint.TEXT);
          }
          if (written.matches(".*\\bObject\\b.*")) {
            hints.add(Hint.OBJECT);
          }
          if (FUNCTION.matcher(written).matches()) {
            hints.add(Hint.FUNCTION);
          }
        });
    if (parameters.stream().map(parameter -> parameter.getType().asString()).anyMatch(Set.of("boolean", "Boolean")::contains)) {
      hints.add(Hint.FLAG);
    }
    return hints;
  }

  private String written(Parameter parameter) {
    return parameter.getType().asString() + (parameter.isVarArgs() ? "..." : "");
  }

  private String kindOf(TypeDeclaration<?> type) {
    if (type.isRecordDeclaration()) {
      return "record";
    }
    if (type.isEnumDeclaration()) {
      return "enum";
    }
    if (type.isAnnotationDeclaration()) {
      return "annotation";
    }
    return type.asClassOrInterfaceDeclaration().isInterface() ? "interface" : "class";
  }

  private int line(Node node) {
    return node.getBegin().map(position -> position.line).orElse(0);
  }
}
