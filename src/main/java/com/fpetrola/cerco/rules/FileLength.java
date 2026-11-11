package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Reading;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.ArrayList;
import java.util.List;

/** How long a file is. A long file is several concepts sharing a name. */
public final class FileLength implements BudgetRule {
  private final int ceiling;
  private final Hypothesis apart = new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
      "some fields and the methods using them go together", "extract the class each group forms");
  private final Hypothesis elsewhere = new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
      "many methods work on another object's data", "move them to the object they are handed");

  public FileLength(int ceiling) {
    this.ceiling = ceiling;
  }

  @Override
  public String id() {
    return "file-length";
  }

  @Override
  public String principle() {
    return "cohesion: one file, one concept";
  }

  @Override
  public String description() {
    return "a file over " + ceiling + " lines holds more than one responsibility; files the baseline froze longer "
        + "may only shrink";
  }

  @Override
  public String remedy() {
    return "split it along the responsibilities it already has: the groups of methods that share fields are the "
        + "classes waiting to be named";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a second file with the leftovers, named Helper or Part2", apart, elsewhere);
  }

  private Measure read(ParsedFile file, Measure measure) {
    return file.unit().flatMap(unit -> unit.findAll(TypeDeclaration.class).stream()
            .max(Comparator.comparingInt(type -> type.getMembers().size())))
        .flatMap(type -> reading(type.getNameAsString(), new Cohesion(type))).map(measure::read).orElse(measure);
  }

  /** What the largest type of a long file is made of; nothing when all of it hangs together. */
  private Optional<Reading> reading(String type, Cohesion cohesion) {
    List<Cohesion.Group> groups = cohesion.groups();
    int loose = cohesion.loose().size();
    if (groups.size() >= 2) {
      return Optional.of(apart.because("`" + type + "` has " + groups.size() + " groups of methods that share no "
          + "field and never call each other: " + groups.stream().limit(3).map(Cohesion.Group::said)
              .collect(Collectors.joining("; "))));
    }
    return Optional.of(elsewhere.because(loose + " of the " + cohesion.methods() + " methods of `" + type
        + "` reach none of its fields")).filter(reading -> loose >= 3 && loose * 2 >= cohesion.methods());
  }

  @Override
  public int ceiling() {
    return ceiling;
  }

  @Override
  public Measuring measuring() {
    List<Measure> measures = new ArrayList<>();
    return new Measuring() {
      @Override
      public void visit(ParsedFile file) {
        Measure measure = new Measure(file.name() + "|file", file.name(), 1, file.lines(), file.lines() + " lines");
        measures.add(file.lines() > ceiling ? read(file, measure) : measure);
      }

      @Override
      public List<Measure> measures() {
        return List.copyOf(measures);
      }
    };
  }
}
