package com.fpetrola.cerco.surface;

import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The surface of every module of a project, fed one file at a time like a rule: each file of main adds what it
 * offers to its module, and every file, tests included, adds the names it uses.
 */
public final class Surfaces {
  private final Map<String, List<Member>> offered = new HashMap<>();
  private final Set<String> named = new HashSet<>();

  public void visit(String module, ParsedFile file) {
    offered.computeIfAbsent(module, k -> new ArrayList<>()).addAll(new Offered(file).members());
    hear(file);
  }

  public void hear(ParsedFile file) {
    file.unit().ifPresent(this::names);
  }

  public Surface of(String module) {
    return new Surface(offered.getOrDefault(module, List.of()), named);
  }

  private void names(CompilationUnit unit) {
    unit.findAll(MethodCallExpr.class).forEach(call -> named.add(call.getNameAsString()));
    unit.findAll(MethodReferenceExpr.class).forEach(reference -> named.add(reference.getIdentifier()));
    unit.findAll(ObjectCreationExpr.class).forEach(creation -> named.add(creation.getType().getNameAsString()));
    unit.findAll(NameExpr.class).forEach(name -> named.add(name.getNameAsString()));
    unit.findAll(FieldAccessExpr.class).forEach(access -> named.add(access.getNameAsString()));
  }
}
