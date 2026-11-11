package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Declared;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Three or more types of different families that each keep the same three or more fields, by name and type: things
 * that travel together and have no type of their own, so every holder is handed them one by one. Candidate from the
 * plan of model smells (the data clump, among fields); it replaces parallel-model, which looked at pairs.
 */
public final class KeptTogether extends PlacesRule<KeptTogether.Site> {
  private static final int TYPES = 3;
  private static final int FIELDS = 3;

  /** A type with enough fields to be compared: where it is declared, and its name. */
  public record Site(String file, int line, String member, String type) implements Place {
  }

  @Override
  public String id() {
    return "kept-together";
  }

  @Override
  public String principle() {
    return "things that travel together are one thing, with a type and a name";
  }

  @Override
  public String description() {
    return TYPES + " or more types of different families each keep the same " + FIELDS + " or more fields: a group "
        + "that travels together without a type, handed to every holder piece by piece";
  }

  @Override
  public String remedy() {
    return "a type for the group, named for what it is, that the holders keep whole and ask; what they did with "
        + "the pieces becomes its behaviour";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a record of the same fields with no behaviour, unpacked again by every holder",
        new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
            "several types keep the same group of fields",
            "a type for the group, kept whole by each holder"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    unit.findAll(TypeDeclaration.class).stream().filter(type -> type.getFields().size() >= FIELDS)
        .forEach(type -> sites.add(new Site(file.name(), line(type), enclosing.nameOf(type),
            type.getNameAsString())));
  }

  @Override
  protected List<Finding> conclude(List<Site> sites, Project project) {
    Map<String, Site> byType = new HashMap<>();
    sites.forEach(site -> byType.putIfAbsent(site.type(), site));
    Map<String, Set<String>> alike = new HashMap<>();
    for (String one : byType.keySet()) {
      for (String two : byType.keySet()) {
        if (one.compareTo(two) < 0 && strangers(one, two, project) && shared(List.of(one, two), project).size()
            >= FIELDS) {
          alike.computeIfAbsent(one, k -> new TreeSet<>()).add(two);
          alike.computeIfAbsent(two, k -> new TreeSet<>()).add(one);
        }
      }
    }
    List<Finding> findings = new ArrayList<>();
    alike.forEach((type, others) -> largest(type, others, project).ifPresent(together -> {
      Site site = byType.get(type);
      List<String> holders = others.stream().filter(other -> shared(List.of(type, other), project)
          .containsAll(together)).toList();
      findings.add(new Finding(id(), site.file(), site.line(), site.file() + "|" + site.member() + "|"
          + String.join(",", together), site.member() + " keeps `" + clipped(String.join(", ", together)) + "`, as "
          + holders.size() + " other types do: " + clipped(String.join(", ", holders))));
    }));
    return findings;
  }

  /** The largest group of fields a type keeps in common with two of the types alike to it, when it is enough. */
  private Optional<Set<String>> largest(String type, Set<String> others, Project project) {
    List<Set<String>> groups = new ArrayList<>();
    for (String one : others) {
      for (String two : others) {
        if (one.compareTo(two) < 0) {
          groups.add(shared(List.of(type, one, two), project));
        }
      }
    }
    return groups.stream().filter(group -> group.size() >= FIELDS)
        .max((first, second) -> Integer.compare(first.size(), second.size()));
  }

  private boolean strangers(String one, String two, Project project) {
    return !project.family(one).root().equals(project.family(two).root());
  }

  /** The fields every one of those types declares with the same name and the same type. */
  private Set<String> shared(List<String> types, Project project) {
    List<Map<String, String>> fields = types.stream().map(project::declared).flatMap(Optional::stream)
        .map(Declared::fields).toList();
    return fields.stream().findFirst().map(first -> first.entrySet().stream()
        .filter(field -> fields.stream().allMatch(other -> field.getValue().equals(other.get(field.getKey()))))
        .map(Map.Entry::getKey).collect(Collectors.toCollection(TreeSet::new))).orElseGet(TreeSet::new);
  }
}
