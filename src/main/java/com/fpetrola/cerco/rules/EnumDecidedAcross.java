package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.rules.Decision.Effect;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
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
 * The constants of one enum of the project decided over in two or more members anywhere in the project: switches
 * whose labels are all its constants, or comparisons `x == E.A`. The project-wide reach of enum-decided-outside,
 * which looks at one file; a switch an enum makes over itself counts too, the case JDeodorant's type checking misses.
 */
public final class EnumDecidedAcross extends IndexingRule<Set<String>, EnumDecidedAcross.Site> {
  private static final int MEMBERS = 2;

  /**
   * One decision: where, in which member, the constants it names, the enum they were written with when they were,
   * and what its branches do.
   */
  public record Site(String file, int line, String member, Set<String> constants, Optional<String> qualifier,
      Effect effect) {
  }

  private final Hypothesis meant = new Hypothesis(MissingModel.VARIATION_AS_DATA,
      "every place asks what a constant means (a value, a label, a capability)",
      "a field or a method on the enum, answered by each constant");
  private final Hypothesis done = new Hypothesis(MissingModel.VARIATION_AS_DATA,
      "the places do different things per constant",
      "a body per constant, or a strategy each constant holds");
  private final Hypothesis mode = new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
      "the enum is a mode some object switches between",
      "a state object per constant");


  @Override
  public String id() {
    return "enum-decided-across";
  }

  @Override
  public String principle() {
    return "polymorphism over type tests: what is decided per constant is the constant's behaviour";
  }

  @Override
  public String description() {
    return "the constants of one enum of the project are decided over (switch, ==) in " + MEMBERS + " or more members "
        + "anywhere: each keeps its own copy of what every constant means";
  }

  @Override
  public String remedy() {
    return "give the enum the behaviour (a method, a field per constant, or a body per constant) and call it where "
        + "the decisions were";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a map keyed by the constants, an EnumMap of lambdas, or the switch moved into one helper: "
        + "the same decision, outside the constants", meant, done, mode);
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Index<Set<String>, Site> gathered) {
    for (EnumDeclaration declared : unit.findAll(EnumDeclaration.class)) {
      gathered.declared().computeIfAbsent(declared.getNameAsString(), k -> new TreeSet<>()).addAll(declared.getEntries()
          .stream().map(EnumConstantDeclaration::getNameAsString).toList());
    }
    Decisions reader = new Decisions(unit);
    unit.findAll(SwitchStmt.class).forEach(decision -> labels(decision).ifPresent(names -> gathered.sites().add(
        new Site(file.name(), line(decision), enclosing.of(decision), names, Optional.empty(),
            reader.effectOf(decision, decision, false)))));
    unit.findAll(SwitchExpr.class).forEach(decision -> labels(decision).ifPresent(names -> gathered.sites().add(
        new Site(file.name(), line(decision), enclosing.of(decision), names, Optional.empty(),
            reader.effectOf(decision, decision, true)))));
    unit.findAll(BinaryExpr.class, this::comparesWithAConstant).forEach(test -> qualified(test).ifPresent(constant ->
        gathered.sites().add(new Site(file.name(), line(test), enclosing.of(test),
            Set.of(constant.getNameAsString()), Optional.of(constant.getScope().toString()), Effect.UNKNOWN))));
  }

  @Override
  protected List<Finding> conclude(Index<Set<String>, Site> gathered, Project project) {
    Map<String, List<Site>> byEnum = new LinkedHashMap<>();
    for (Site decision : gathered.sites()) {
      gathered.declared().entrySet().stream()
          .filter(entry -> decision.qualifier().map(entry.getKey()::equals).orElse(true))
          .filter(entry -> entry.getValue().containsAll(decision.constants()))
          .map(Map.Entry::getKey).sorted().findFirst()
          .ifPresent(name -> byEnum.computeIfAbsent(name, k -> new ArrayList<>()).add(decision));
    }
    List<Finding> findings = new ArrayList<>();
    byEnum.forEach((name, decisions) -> {
      Set<String> members = decisions.stream().map(Site::member).collect(Collectors.toCollection(TreeSet::new));
      if (members.size() >= MEMBERS) {
        decisions.forEach(decision -> findings.add(new Finding(id(), decision.file(), decision.line(),
            decision.file() + "|" + decision.member() + "|" + name + "|" + String.join(",", decision.constants()),
            decision.member() + " decides over " + name + " (" + String.join(", ", decision.constants()) + "); "
                + members.size() + " members decide over its constants").read(read(decision, name, members.size()))));
      }
    });
    return findings;
  }

  /** What the branches do says whether the constants lack data, behaviour, or are the states of something. */
  private Reading read(Site decision, String name, int members) {
    Map<Effect, Reading> byEffect = Map.of(
        Effect.REASSIGN, mode.because("the branches change the `" + name + "` they test, in one of " + members
            + " members that decide over it"),
        Effect.ACT, done.because("the cases do different work per constant of `" + name + "`, in one of " + members
            + " members that decide over it"),
        Effect.ANSWER, meant.because("every case only answers a value per constant of `" + name + "`, in one of "
            + members + " members that decide over it"));
    return byEffect.getOrDefault(decision.effect(), meant.because("it asks whether a value is one of some "
        + "constants of `" + name + "`, as " + members + " members do"));
  }

  /** The constants a switch's labels name, when they are all bare names and at least two. */
  private Optional<Set<String>> labels(SwitchNode decision) {
    Set<String> names = new TreeSet<>();
    for (SwitchEntry entry : decision.getEntries()) {
      if (entry.getLabels().stream().anyMatch(label -> !label.isNameExpr())) {
        return Optional.empty();
      }
      entry.getLabels().forEach(label -> names.add(label.asNameExpr().getNameAsString()));
    }
    return names.size() >= 2 ? Optional.of(names) : Optional.empty();
  }

  private boolean comparesWithAConstant(BinaryExpr test) {
    boolean equality = test.getOperator() == BinaryExpr.Operator.EQUALS
        || test.getOperator() == BinaryExpr.Operator.NOT_EQUALS;
    return equality && qualified(test).isPresent();
  }

  /** The `E.A` side of a comparison, when one side is a constant named through its type. */
  private Optional<com.github.javaparser.ast.expr.FieldAccessExpr> qualified(BinaryExpr test) {
    return java.util.stream.Stream.of(test.getLeft(), test.getRight()).filter(Expression::isFieldAccessExpr)
        .map(Expression::asFieldAccessExpr)
        .filter(side -> side.getScope().isNameExpr()
            && Character.isUpperCase(side.getScope().asNameExpr().getNameAsString().charAt(0))
            && Character.isUpperCase(side.getNameAsString().charAt(0)))
        .findFirst();
  }
}
