package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Family;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A hierarchy of the project whose types are tested with instanceof or casts from two or more members outside it:
 * the same family asked in several places is an operation the family lacks, and with two or more of them, a visitor.
 * One finding per testing site, under the largest family the tested type belongs to. Candidate born from the plan of
 * model smells: in oozx it finds OpcodeReference tested
 * from nine files of the emulator, which already has a visitor for its instructions.
 */
public final class HierarchyAskedFromOutside extends ProjectRule<List<HierarchyAskedFromOutside.Test>> {
  private static final int MEMBERS = 2;
  private static final int SUBTYPES = 2;

  /** A test of a type: where, in which member of which type, and how. */
  public record Test(String file, int line, String member, String enclosingType, String tested, String how) {
  }

  private final Hypothesis method = new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
      "the places do the same one thing per subtype",
      "a method on the common type, implemented by each subtype");
  private final Hypothesis visitor = new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
      "the places do different things, or the family cannot depend on their module",
      "a visitor the family accepts; each place becomes one visitor (reuse it if the family has one)");
  private final Hypothesis role = new Hypothesis(MissingModel.VARIATION_AS_DATA,
      "the tested type is a role (an interface some members play)",
      "a method every member answers, with the empty answer in the common type");

  @Override
  public String id() {
    return "hierarchy-asked-from-outside";
  }

  @Override
  public String principle() {
    return "polymorphism: a family that is asked about its members from outside lacks an operation";
  }

  @Override
  public String description() {
    return "the subtypes of one hierarchy of the project are tested (instanceof, casts) in " + MEMBERS + " or more "
        + "members outside the hierarchy: each place keeps its own copy of what every member means";
  }

  @Override
  public String remedy() {
    return "give the family the operation (a method on the common type) when the places do one thing; when they do "
        + "several, or the hierarchy cannot see their module, a visitor the family accepts, one per place";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a kind() enum, an isX() per subtype, a map keyed by Class, a sealed switch, or the "
        + "tests moved into one helper: the family is still asked from outside", method, visitor, role);
  }

  @Override
  protected List<Test> start() {
    return new ArrayList<>();
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Test> tests) {
    unit.findAll(InstanceOfExpr.class).forEach(test -> keep(file, test, test.getType(), "instanceof", tests));
    unit.findAll(CastExpr.class).forEach(test -> keep(file, test, test.getType(), "cast", tests));
  }

  private void keep(ParsedFile file, Node test, Type type, String how, List<Test> tests) {
    boolean inEquals = test.findAncestor(MethodDeclaration.class)
        .map(method -> method.getNameAsString().equals("equals")).orElse(false);
    simpleName(type).filter(tested -> !inEquals).ifPresent(tested -> tests.add(new Test(file.name(),
        line(test), enclosing.of(test), test.findAncestor(TypeDeclaration.class)
        .map(TypeDeclaration::getNameAsString).orElse(""), tested, how)));
  }

  @Override
  protected List<Finding> conclude(List<Test> gathered, Project project) {
    Map<String, List<Test>> byFamily = new LinkedHashMap<>();
    Map<String, Family> families = new HashMap<>();
    for (Test test : gathered) {
      Optional.of(test.tested()).filter(project::declares).map(project::family)
          .filter(family -> family.members().size() > SUBTYPES && !family.holds(test.enclosingType()))
          .ifPresent(family -> {
            families.put(family.root(), family);
            byFamily.computeIfAbsent(family.root(), k -> new ArrayList<>()).add(test);
          });
    }
    List<Finding> findings = new ArrayList<>();
    byFamily.forEach((root, tests) -> {
      Set<String> members = tests.stream().map(Test::member).collect(Collectors.toCollection(TreeSet::new));
      if (members.size() >= MEMBERS) {
        Family family = families.get(root);
        String evidence = evidence(family, tests, project);
        tests.forEach(test -> findings.add(new Finding(id(), test.file(), test.line(),
            test.file() + "|" + test.member() + "|" + root + "|" + test.how() + " " + test.tested(),
            "`" + test.how() + " " + test.tested() + "` in " + test.member() + "; " + evidence)
            .read(read(test, family, members.size(), project))));
      }
    });
    return findings;
  }

  private String evidence(Family family, List<Test> tests, Project project) {
    long members = tests.stream().map(Test::member).distinct().count();
    long outside = tests.stream().map(Test::enclosingType).distinct().count();
    Set<String> visitors = project.visitorsOf(family);
    return family.root() + " (" + family.subtypes() + " subtypes) is tested in " + members + " members of " + outside
        + " types outside it"
        + (visitors.isEmpty() ? "" : "; the family already accepts " + String.join(", ", visitors));
  }

  /** A family asked in several members lacks a visitor; the one it already accepts, when it has one. */
  private Reading read(Test test, Family family, int members, Project project) {
    String kind = "`" + test.tested() + "` is one of `" + family.root() + "`, which " + members + " members ask";
    return project.visitorsOf(family).stream().findFirst()
        .map(accepted -> visitor.because(kind + " though it already accepts `" + accepted + "`"))
        .orElseGet(() -> visitor.because(kind));
  }

  private Optional<String> simpleName(Type type) {
    return type.toClassOrInterfaceType().map(ClassOrInterfaceType::getNameAsString);
  }

}
