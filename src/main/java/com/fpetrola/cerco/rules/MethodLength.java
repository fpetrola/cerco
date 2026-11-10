package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** How long a method or constructor is, signature to closing brace. */
public final class MethodLength implements BudgetRule {
  private static final int SETTERS = 3;
  private static final int ASKS = 8;
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

  private final Clues clues = new Clues();
  private final Hypothesis narrated = new Hypothesis(MissingModel.PROCEDURE_INSTEAD_OF_MODEL,
      "the method narrates steps (comments, paragraphs, many locals)",
      "a method per step named by what it does; an object named by the concept when the steps share three "
          + "or more locals");
  private final Hypothesis asking = new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
      "it asks one other object many things",
      "move that part to the object it asks");
  private final Hypothesis assembled = new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
      "it fills an object through setters, or unpacks one into many arguments",
      "a constructor or a method of that object");

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("part1() and part2() sharing every local, or the body moved to a helper class", narrated,
        asking, assembled);
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
    Measure measure = new Measure(file.name() + "|" + name, file.name(), begin, end - begin + 1,
        (end - begin + 1) + " lines in " + name);
    return measure.value() > ceiling ? measure.read(read(callable, measure.value())) : measure;
  }

  /** What a long method is long with: an object it fills, an object it keeps asking, or steps it narrates. */
  private Reading read(Node method, int lines) {
    List<MethodCallExpr> calls = method.findAll(MethodCallExpr.class, call -> call.getScope()
        .filter(scope -> scope.isNameExpr() && Character.isLowerCase(scope.toString().charAt(0))).isPresent());
    Optional<Map.Entry<String, Long>> filled = most(calls.stream().filter(call ->
        call.getNameAsString().matches("set[A-Z].*") && call.getArguments().size() == 1), SETTERS);
    Optional<Map.Entry<String, Long>> asked = most(calls.stream(), ASKS);
    return clues.first(
        () -> filled.map(one -> assembled.because("it calls " + one.getValue() + " setters on `" + one.getKey() + "`")),
        () -> asked.map(one -> asking.because("it makes " + one.getValue() + " calls on `" + one.getKey() + "`")))
        .orElseGet(() -> narrated.because(lines + " lines and " + method.findAll(VariableDeclarator.class).size()
            + " locals in one method"));
  }

  /** The receiver most of those calls are made on, when they are at least so many. */
  private Optional<Map.Entry<String, Long>> most(Stream<MethodCallExpr> calls, int atLeast) {
    return calls.map(call -> call.getScope().map(Object::toString).orElse(""))
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
        .filter(one -> one.getValue() >= atLeast).max(Map.Entry.comparingByValue());
  }
}
