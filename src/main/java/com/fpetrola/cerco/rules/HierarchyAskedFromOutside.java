package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A hierarchy of the project whose types are tested with instanceof or casts from two or more members outside it:
 * the same family asked in several places is an operation the family lacks, and with two or more of them, a visitor.
 * One finding per testing site, under the largest family the tested type belongs to. Candidate born from the plan of model smells: in oozx it finds OpcodeReference tested
 * from nine files of the emulator, which already has a visitor for its instructions.
 */
public final class HierarchyAskedFromOutside extends ProjectRule<HierarchyAskedFromOutside.Gathered> {
  private static final int MEMBERS = 2;
  private static final int SUBTYPES = 2;

  /** A type of the project: its simple name, its direct supertypes, and the visitor it accepts, if any. */
  public record Declared(String name, List<String> supertypes, Optional<String> visitor) {
  }

  /** A test of a type: where, in which member of which type, and how. */
  public record Test(String file, int line, String member, String enclosingType, String tested, String how) {
  }

  /** What the files said: the types declared and the tests made. */
  public record Gathered(Map<String, Declared> types, List<Test> tests) {
  }

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
        + "tests moved into one helper: the family is still asked from outside",
        new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
            "the places do the same one thing per subtype",
            "a method on the common type, implemented by each subtype"),
        new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
            "the places do different things, or the family cannot depend on their module",
            "a visitor the family accepts; each place becomes one visitor (reuse it if the family has one)"),
        new Hypothesis(MissingModel.VARIATION_AS_DATA,
            "the tested type is a role (an interface some members play)",
            "a method every member answers, with the empty answer in the common type"));
  }

  @Override
  protected Gathered start() {
    return new Gathered(new HashMap<>(), new ArrayList<>());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Gathered gathered) {
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      gathered.types().putIfAbsent(type.getNameAsString(),
          new Declared(type.getNameAsString(), supertypes(type), visitor(type)));
    }
    unit.findAll(InstanceOfExpr.class).forEach(test -> keep(file, test, test.getType(), "instanceof", gathered));
    unit.findAll(CastExpr.class).forEach(test -> keep(file, test, test.getType(), "cast", gathered));
  }

  private void keep(ParsedFile file, Node test, Type type, String how, Gathered gathered) {
    boolean inEquals = test.findAncestor(MethodDeclaration.class)
        .map(method -> method.getNameAsString().equals("equals")).orElse(false);
    simpleName(type).filter(tested -> !inEquals).ifPresent(tested -> gathered.tests().add(new Test(file.name(),
        line(test), enclosing.of(test), test.findAncestor(TypeDeclaration.class)
        .map(TypeDeclaration::getNameAsString).orElse(""), tested, how)));
  }

  @Override
  protected List<Finding> conclude(Gathered gathered) {
    Map<String, Set<String>> children = new HashMap<>();
    gathered.types().values().forEach(type -> type.supertypes().forEach(parent ->
        children.computeIfAbsent(parent, k -> new TreeSet<>()).add(type.name())));
    Map<String, List<Test>> byFamily = new LinkedHashMap<>();
    for (Test test : gathered.tests()) {
      if (!gathered.types().containsKey(test.tested())) {
        continue;
      }
      roots(test.tested(), gathered.types()).stream()
          .max(Comparator.comparingInt((String root) -> descendants(root, children).size())
              .thenComparing(Comparator.reverseOrder()))
          .filter(root -> descendants(root, children).size() > SUBTYPES)
          .filter(root -> !descendants(root, children).contains(test.enclosingType()))
          .ifPresent(root -> byFamily.computeIfAbsent(root, k -> new ArrayList<>()).add(test));
    }
    List<Finding> findings = new ArrayList<>();
    byFamily.forEach((root, tests) -> {
      Set<String> members = tests.stream().map(Test::member).collect(Collectors.toCollection(TreeSet::new));
      if (members.size() >= MEMBERS) {
        String evidence = evidence(root, tests, descendants(root, children), gathered.types());
        tests.forEach(test -> findings.add(new Finding(id(), test.file(), test.line(),
            test.file() + "|" + test.member() + "|" + root + "|" + test.how() + " " + test.tested(),
            "`" + test.how() + " " + test.tested() + "` in " + test.member() + "; " + evidence)));
      }
    });
    return findings;
  }

  private String evidence(String root, List<Test> tests, Set<String> family, Map<String, Declared> types) {
    long members = tests.stream().map(Test::member).distinct().count();
    long outside = tests.stream().map(Test::enclosingType).distinct().count();
    Set<String> visitors = family.stream().map(types::get).filter(Objects::nonNull)
        .map(Declared::visitor).flatMap(Optional::stream).collect(Collectors.toCollection(TreeSet::new));
    return root + " (" + (family.size() - 1) + " subtypes) is tested in " + members + " members of " + outside
        + " types outside it"
        + (visitors.isEmpty() ? "" : "; the family already accepts " + String.join(", ", visitors));
  }

  /** The topmost project types above a type (the type itself when nothing of the project is above it). */
  private Set<String> roots(String name, Map<String, Declared> types) {
    Set<String> roots = new TreeSet<>();
    Set<String> seen = new TreeSet<>();
    Deque<String> pending = new ArrayDeque<>(List.of(name));
    while (!pending.isEmpty()) {
      String current = pending.pop();
      if (!seen.add(current)) {
        continue;
      }
      List<String> above = Optional.ofNullable(types.get(current)).map(Declared::supertypes).orElse(List.of())
          .stream().filter(types::containsKey).toList();
      if (above.isEmpty()) {
        roots.add(current);
      }
      pending.addAll(above);
    }
    return roots;
  }

  /** A type and everything below it in the project. */
  private Set<String> descendants(String root, Map<String, Set<String>> children) {
    Set<String> family = new TreeSet<>();
    Deque<String> pending = new ArrayDeque<>(List.of(root));
    while (!pending.isEmpty()) {
      String current = pending.pop();
      if (family.add(current)) {
        pending.addAll(children.getOrDefault(current, Set.of()));
      }
    }
    return family;
  }

  private List<String> supertypes(TypeDeclaration<?> type) {
    Stream<ClassOrInterfaceType> extended = type.isClassOrInterfaceDeclaration()
        ? type.asClassOrInterfaceDeclaration().getExtendedTypes().stream() : Stream.empty();
    Stream<ClassOrInterfaceType> implemented = type.toClassOrInterfaceDeclaration()
        .map(NodeWithImplements::getImplementedTypes).map(List::stream)
        .or(() -> type.toEnumDeclaration().map(NodeWithImplements::getImplementedTypes).map(List::stream))
        .or(() -> type.toRecordDeclaration().map(NodeWithImplements::getImplementedTypes).map(List::stream))
        .orElse(Stream.empty());
    return Stream.concat(extended, implemented).map(ClassOrInterfaceType::getNameAsString).toList();
  }

  private Optional<String> visitor(TypeDeclaration<?> type) {
    return type.getMethodsByName("accept").stream().filter(method -> method.getParameters().size() == 1)
        .map(method -> method.getParameter(0).getType()).map(this::simpleName).flatMap(Optional::stream)
        .filter(name -> name.endsWith("Visitor")).findFirst();
  }

  private Optional<String> simpleName(Type type) {
    return type.toClassOrInterfaceType().map(ClassOrInterfaceType::getNameAsString);
  }

  private int line(Node node) {
    return node.getBegin().map(position -> position.line).orElse(1);
  }
}
