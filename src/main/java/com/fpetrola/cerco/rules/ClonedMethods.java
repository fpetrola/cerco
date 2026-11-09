package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The same method, body and name, written out in several types: boilerplate an interface should have defaulted or
 * a superclass should hold. Below the window of the repeated-statements rule, because one line is enough.
 */
public final class ClonedMethods extends ProjectRule<Map<String, List<ClonedMethods.Site>>> {
  private static final int TYPES = 3;
  private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");

  /** One copy of a method. */
  public record Site(String file, int line, String member) {
  }

  @Override
  public String id() {
    return "no-cloned-methods";
  }

  @Override
  public String principle() {
    return "abstraction: a thing done twice has a name, even when it is one line long";
  }

  @Override
  public String description() {
    return "a method with the same name and the same body in " + TYPES + " or more types is the same decision taken "
        + "separately each time; when it changes, some copies will be forgotten";
  }

  @Override
  public String remedy() {
    return "make it a default method of the interface they share, or pull the common part into a type they are "
        + "built from; if the copies are truly independent decisions, their bodies should say so by differing";
  }

  @Override
  protected Map<String, List<Site>> start() {
    return new HashMap<>();
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Map<String, List<Site>> sites) {
    for (MethodDeclaration method : unit.findAll(MethodDeclaration.class, this::hasABody)) {
      String key = method.getNameAsString() + "/" + method.getParameters().size() + " "
          + method.getBody().map(body -> body.toString().replaceAll("\\s+", " ")).orElse("");
      Site site = new Site(file.name(), method.getBegin().map(position -> position.line).orElse(1),
          enclosing.signatureOf(method));
      List<Site> copies = sites.computeIfAbsent(key, k -> new ArrayList<>());
      if (copies.stream().noneMatch(seen -> seen.member().equals(site.member()))) {
        copies.add(site);
      }
    }
  }

  private boolean hasABody(MethodDeclaration method) {
    return !OBJECT_METHODS.contains(method.getNameAsString())
        && method.getBody().filter(body -> !body.getStatements().isEmpty()).isPresent();
  }

  @Override
  protected List<Finding> conclude(Map<String, List<Site>> sites) {
    List<Finding> findings = new ArrayList<>();
    sites.values().stream().filter(copies -> copies.size() >= TYPES).forEach(copies -> copies.forEach(site ->
        findings.add(new Finding(id(), site.file(), site.line(), site.file() + "|" + site.member(),
            site.member() + " is the same method as in " + (copies.size() - 1) + " other type(s), e.g. "
                + other(copies, site)))));
    return findings;
  }

  private String other(List<Site> copies, Site site) {
    return copies.stream().filter(copy -> copy != site).findFirst()
        .map(copy -> copy.file() + ":" + copy.line()).orElse("");
  }
}
