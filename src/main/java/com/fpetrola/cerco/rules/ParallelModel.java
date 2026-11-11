package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Declared;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Two types of the project, of different families, that hold four or more fields of the same name and type: the
 * same thing modelled twice, with code somewhere that copies one into the other. Candidate from the plan of model
 * smells (duplicate abstraction).
 */
public final class ParallelModel extends PlacesRule<ParallelModel.Site> {
  private static final int FIELDS = 4;

  /** A type with enough fields to be compared: where it is declared, and its name. */
  public record Site(String file, int line, String member, String type) implements Place {
  }

  @Override
  public String id() {
    return "parallel-model";
  }

  @Override
  public String principle() {
    return "single owner of a concept: what is known about a thing is held by one type";
  }

  @Override
  public String description() {
    return "two types of different families hold " + FIELDS + " or more fields of the same name and type: one "
        + "thing modelled twice, kept in step by code that copies between them";
  }

  @Override
  public String remedy() {
    return "one of them holds the thing and the other asks it, or both are built from a type for what they share; "
        + "the copying goes away";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a mapper between the two, or a common interface of getters both implement",
        new Hypothesis(MissingModel.KNOWLEDGE_WITHOUT_ONE_OWNER,
            "two types hold the same fields",
            "one holds them and the other asks it; or a type for what they share, held by both"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    unit.findAll(TypeDeclaration.class).stream().filter(type -> type.getFields().size() >= FIELDS)
        .forEach(type -> sites.add(new Site(file.name(), line(type), enclosing.nameOf(type),
            type.getNameAsString())));
  }

  @Override
  protected List<Finding> conclude(List<Site> sites, Project project) {
    List<Finding> findings = new ArrayList<>();
    for (Site site : sites) {
      Optional<Declared> mine = project.declared(site.type());
      sites.stream().filter(other -> !other.type().equals(site.type()))
          .filter(other -> !project.family(other.type()).root().equals(project.family(site.type()).root()))
          .forEach(other -> mine.flatMap(one -> project.declared(other.type()).map(two -> shared(one, two)))
              .filter(same -> same.size() >= FIELDS)
              .ifPresent(same -> findings.add(new Finding(id(), site.file(), site.line(), site.file() + "|"
                  + site.member() + "|same fields as " + other.type(), site.member() + " and " + other.type()
                  + " hold the same " + same.size() + " fields: " + clipped(String.join(", ", same))))));
    }
    return findings;
  }

  /** The fields two types declare with the same name and the same type. */
  private Set<String> shared(Declared one, Declared two) {
    Set<String> same = new TreeSet<>();
    for (Map.Entry<String, String> field : one.fields().entrySet()) {
      two.field(field.getKey()).filter(field.getValue()::equals).ifPresent(type -> same.add(field.getKey()));
    }
    return same;
  }
}
