package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.body.CallableDeclaration;
import java.util.ArrayList;
import java.util.List;

/** How long a method or constructor is, signature to closing brace. */
public final class MethodLength implements BudgetRule {
  private final int ceiling;
  private final Enclosing enclosing = new Enclosing();

  public MethodLength(int ceiling) {
    this.ceiling = ceiling;
  }

  @Override
  public String id() {
    return "method-length";
  }

  @Override
  public String principle() {
    return "cohesion: a method does one thing, and says which in its name";
  }

  @Override
  public String description() {
    return "a method over " + ceiling + " lines is several steps that each deserve a name; methods the baseline "
        + "froze longer may only shrink";
  }

  @Override
  public String remedy() {
    return "extract each step into a method named for what it does; if the steps share many locals, they are an "
        + "object";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("part1() and part2() sharing every local, or the body moved to a helper class",
        new Hypothesis(MissingModel.PROCEDURE_INSTEAD_OF_MODEL,
            "the method narrates steps (comments, paragraphs, many locals)",
            "a method per step named by what it does; an object named by the concept when the steps share three "
                + "or more locals"),
        new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
            "it asks one other object many things",
            "move that part to the object it asks"),
        new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
            "it fills an object through setters, or unpacks one into many arguments",
            "a constructor or a method of that object"));
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
        file.unit().ifPresent(unit -> {
          for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            measures.add(measure(file, callable));
          }
        });
      }

      @Override
      public List<Measure> measures() {
        return List.copyOf(measures);
      }
    };
  }

  private Measure measure(ParsedFile file, CallableDeclaration<?> callable) {
    int begin = callable.getBegin().map(position -> position.line).orElse(1);
    int end = callable.getEnd().map(position -> position.line).orElse(begin);
    String name = enclosing.signatureOf(callable);
    return new Measure(file.name() + "|" + name, file.name(), begin, end - begin + 1,
        (end - begin + 1) + " lines in " + name);
  }
}
