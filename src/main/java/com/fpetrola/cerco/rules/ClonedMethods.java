package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The same method, body and name, written out in several types: boilerplate an interface should have defaulted or
 * a superclass should hold. Below the window of the repeated-statements rule, because one line is enough.
 */
public final class ClonedMethods extends ProjectRule<Map<String, List<ClonedMethods.Site>>> {
  private static final int TYPES = 3;
  private static final Set<String> OBJECT_METHODS = Set.of("equals", "hashCode", "toString");

  /** One copy of a method, and the type it sits in. */
  public record Site(String file, int line, String member, String type) {
  }

  private final Hypothesis renamed = new Hypothesis(MissingModel.SHAPE_REPEATED,
      "the copies differ only in literals or names",
      "one method, the difference a parameter");
  private final Hypothesis varied = new Hypothesis(MissingModel.SHAPE_REPEATED,
      "they differ in a call",
      "a strategy, a lambda, or a template method in a common supertype");
  private final Hypothesis siblings = new Hypothesis(MissingModel.SHAPE_REPEATED,
      "they sit in sibling types",
      "pull it up into the supertype, or a default method");
  private final Hypothesis strangers = new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
      "they sit in unrelated types",
      "an object both are built from");

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
  public Diagnosis diagnosis() {
    return new Diagnosis("a static helper or a utility class both call: the shape still has no owner", renamed,
        varied, siblings, strangers);
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
          enclosing.signatureOf(method), method.findAncestor(TypeDeclaration.class)
          .map(TypeDeclaration::getNameAsString).orElse(""));
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
  protected List<Finding> conclude(Map<String, List<Site>> sites, Project project) {
    List<Finding> findings = new ArrayList<>();
    sites.values().stream().filter(copies -> copies.size() >= TYPES).forEach(copies -> {
      Reading where = where(copies, project);
      copies.forEach(site -> findings.add(new Finding(id(), site.file(), site.line(), site.file() + "|"
          + site.member(), site.member() + " is the same method as in " + (copies.size() - 1)
          + " other type(s), e.g. " + other(copies, site)).read(where)));
    });
    return findings;
  }

  /** Copies in types of one family of the project belong in what they share; in strangers, in an object to share. */
  private Reading where(List<Site> copies, Project project) {
    Set<String> families = copies.stream().map(site -> project.family(site.type()).root())
        .collect(Collectors.toCollection(TreeSet::new));
    String first = families.iterator().next();
    boolean related = families.size() == 1 && project.family(first).subtypes() > 0;
    return related ? siblings.because("the " + copies.size() + " copies sit in types of one family, `" + first + "`")
        : strangers.because("the " + copies.size() + " copies sit in types with nothing of the project in common");
  }

  private String other(List<Site> copies, Site site) {
    return copies.stream().filter(copy -> copy != site).findFirst()
        .map(copy -> copy.file() + ":" + copy.line()).orElse("");
  }
}
