package com.fpetrola.cerco.rules;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How the methods of a type hang together: two that use a common field of the type, or call one another, are of one
 * group. Several groups are several concepts under one name; methods that touch no field work on someone else's data.
 */
final class Cohesion {
  private final Map<String, String> above = new HashMap<>();
  private final Set<String> fields;
  private final Set<String> methods;

  /** Methods joined through the fields they use: what a class extracted from the type would be made of. */
  record Group(Set<String> fields, Set<String> methods) {

    String said() {
      return "`" + fields.stream().limit(3).collect(Collectors.joining("`, `")) + "` with " + methods.size()
          + " methods";
    }
  }

  Cohesion(TypeDeclaration<?> type) {
    fields = type.getFields().stream().filter(field -> !(field.isStatic() && field.isFinal()))
        .flatMap(field -> field.getVariables().stream()).map(variable -> variable.getNameAsString())
        .collect(Collectors.toCollection(TreeSet::new));
    methods = type.getMethods().stream().map(this::nameOf).collect(Collectors.toCollection(TreeSet::new));
    type.getMethods().forEach(method -> touchedBy(method).forEach(touched -> join(nameOf(method), touched)));
  }

  int methods() {
    return methods.size();
  }

  /** The groups of two or more methods around at least one field, the largest first. */
  List<Group> groups() {
    return parts().stream().filter(group -> !group.fields().isEmpty() && group.methods().size() >= 2)
        .sorted(Comparator.comparingInt((Group group) -> group.methods().size()).reversed()).toList();
  }

  /**
   * The methods that reach no field of the type, not even through the methods they call. None when the type declares
   * no field: an interface, or a type whose state is inherited, has nothing of its own to reach.
   */
  Set<String> loose() {
    return parts().stream().filter(group -> group.fields().isEmpty() && !fields.isEmpty())
        .flatMap(group -> group.methods().stream())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private List<Group> parts() {
    Map<String, List<String>> byTop = Stream.concat(fields.stream(), methods.stream())
        .collect(Collectors.groupingBy(this::top));
    return byTop.values().stream().map(members -> new Group(only(members, fields), only(members, methods))).toList();
  }

  private Set<String> only(List<String> members, Set<String> kind) {
    return members.stream().filter(kind::contains).collect(Collectors.toCollection(TreeSet::new));
  }

  private String nameOf(MethodDeclaration method) {
    return method.getNameAsString() + "()";
  }

  private Stream<String> touchedBy(MethodDeclaration method) {
    Stream<String> named = method.findAll(NameExpr.class).stream().map(NameExpr::getNameAsString);
    Stream<String> ofThis = method.findAll(FieldAccessExpr.class).stream()
        .filter(access -> access.getScope().isThisExpr()).map(FieldAccessExpr::getNameAsString);
    Stream<String> called = method.findAll(MethodCallExpr.class).stream()
        .filter(call -> call.getScope().map(Expression::isThisExpr).orElse(true))
        .map(call -> call.getNameAsString() + "()");
    return Stream.concat(Stream.concat(named, ofThis).filter(fields::contains), called.filter(methods::contains));
  }

  private void join(String one, String other) {
    above.put(top(one), top(other));
  }

  private String top(String member) {
    String current = member;
    while (above.containsKey(current) && !above.get(current).equals(current)) {
      current = above.get(current);
    }
    return current;
  }
}
