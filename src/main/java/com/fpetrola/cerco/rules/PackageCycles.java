package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Packages that depend on each other: no layer, no direction, nothing that can be understood alone. */
public final class PackageCycles extends ProjectRule<PackageCycles.Graph> {

  @Override
  public String id() {
    return "no-package-cycles";
  }

  @Override
  public String principle() {
    return "low coupling: dependencies point one way";
  }

  @Override
  public String description() {
    return "two or more packages that import each other cannot be built, tested or understood separately; the cycle "
        + "is one tangled module with several names";
  }

  @Override
  public String remedy() {
    return "find the dependency that points upward, and move the thing it needs down into the lower package, or "
        + "invert it behind an interface the lower package declares";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("merging the packages",
        new Hypothesis(MissingModel.KNOWLEDGE_WITHOUT_ONE_OWNER,
            "both packages need one concept",
            "move it to one of them, or to a third package both depend on"));
  }

  @Override
  protected Graph start() {
    return new Graph();
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Graph graph) {
    graph.visit(file);
  }

  @Override
  protected List<Finding> conclude(Graph graph) {
    return graph.findings();
  }

  /** Remembers each package's first file and raw imports; resolves them against the package set at the end. */
  final class Graph implements Inspection {
    private final Map<String, String> firstFileOf = new TreeMap<>();
    private final Map<String, List<String>> imports = new TreeMap<>();

    @Override
    public void visit(ParsedFile file) {
      file.unit().ifPresent(unit -> {
        String name = packageOf(unit);
        firstFileOf.putIfAbsent(name, file.name());
        List<String> mine = imports.computeIfAbsent(name, key -> new ArrayList<>());
        for (ImportDeclaration imported : unit.getImports()) {
          mine.add(imported.isAsterisk() && !imported.isStatic() ? imported.getNameAsString() + ".*"
              : imported.getNameAsString());
        }
      });
    }

    @Override
    public List<Finding> findings() {
      Map<String, Set<String>> edges = new TreeMap<>();
      imports.forEach((from, names) -> {
        Set<String> targets = new TreeSet<>();
        names.forEach(name -> target(name).filter(to -> !to.equals(from)).ifPresent(targets::add));
        edges.put(from, targets);
      });
      return new PackageGraph(edges).cycles().stream().map(this::finding).toList();
    }

    private Optional<String> target(String imported) {
      String candidate = parentOf(imported);
      while (!candidate.isEmpty()) {
        if (imports.containsKey(candidate)) {
          return Optional.of(candidate);
        }
        candidate = parentOf(candidate);
      }
      return Optional.empty();
    }

    private Finding finding(Set<String> cycle) {
      String file = firstFileOf.get(cycle.iterator().next());
      return new Finding(id(), file, 1, file + "|cycle:" + String.join(",", cycle),
          "packages depend on each other in a cycle: " + String.join(" <-> ", cycle));
    }
  }

  private String parentOf(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(0, dot);
  }

  private String packageOf(CompilationUnit unit) {
    return unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse("");
  }
}
