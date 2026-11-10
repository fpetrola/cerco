package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The same constants of an enum decided over in two or more places of one file: switches whose cases name the same
 * constants, or tests `x == E.A || x == E.B` naming the same ones. What is decided is the constants' own behaviour.
 * Born from an autopsy of oozx, where the four edges a window docks on were switched over in four methods of the
 * window, each writing the same geometry per edge, and `side == TOP || side == BOTTOM` was asked four times.
 */
public final class EnumDecidedOutside extends DecisionRule {

  @Override
  public String id() {
    return "enum-decided-outside";
  }

  @Override
  public String principle() {
    return "polymorphism over type tests: what is decided per constant is the constant's behaviour";
  }

  @Override
  public String description() {
    return "the same constants of an enum are decided over in several places of one file: each place keeps its own "
        + "copy of what every constant means";
  }

  @Override
  public String remedy() {
    return "give the enum the behaviour (a method, or a field per constant) and call it where the switches were";
  }

  /** One place that decides, and how to read it once the finding of its group is written. */
  private record Place(Node node, Function<Finding, Decision> read) {
  }

  @Override
  protected List<Decision> decisions(ParsedFile file, CompilationUnit unit, Decisions reader) {
    List<Place> switches = new ArrayList<>();
    unit.findAll(SwitchStmt.class).forEach(decision -> switches.add(new Place(decision,
        finding -> reader.ofSwitch(finding, decision, decision, false))));
    unit.findAll(SwitchExpr.class).forEach(decision -> switches.add(new Place(decision,
        finding -> reader.ofSwitch(finding, decision, decision, true))));
    Map<String, List<Place>> places = new LinkedHashMap<>();
    switches.stream().sorted(Comparator.comparing(Place::node, Node.NODE_BY_BEGIN_POSITION))
        .forEach(place -> cases(place.node()).ifPresent(names -> keep(places, "case " + names, place)));
    List<BinaryExpr> ors = unit.findAll(BinaryExpr.class).stream().filter(this::isOr).toList();
    Set<Expression> inner = ors.stream().flatMap(or -> Stream.of(or.getLeft(), or.getRight())).map(this::bare)
        .filter(this::isOr).collect(Collectors.toSet());
    ors.stream().filter(or -> !inner.contains(or)).forEach(test -> constants(test).ifPresent(names ->
        keep(places, names, new Place(test, finding -> reader.ofTest(finding, test)))));
    return places.entrySet().stream().filter(entry -> entry.getValue().size() >= 2).map(entry -> {
      Place first = entry.getValue().get(0);
      return first.read().apply(finding(file, first.node(), entry.getKey(), entry.getValue().size()
          + " places decide over " + entry.getKey() + ", the first in " + enclosing.of(first.node())));
    }).toList();
  }

  private void keep(Map<String, List<Place>> places, String key, Place place) {
    places.computeIfAbsent(key, k -> new ArrayList<>()).add(place);
  }

  /** The constants a switch's cases name, when they are all bare names and at least two. */
  private Optional<String> cases(Node decision) {
    TreeSet<String> names = new TreeSet<>();
    for (SwitchEntry entry : decision.findAll(SwitchEntry.class)) {
      if (entry.getParentNode().orElse(null) != decision) {
        continue;
      }
      NodeList<Expression> labels = entry.getLabels();
      if (labels.stream().anyMatch(label -> !label.isNameExpr())) {
        return Optional.empty();
      }
      labels.forEach(label -> names.add(label.asNameExpr().getNameAsString()));
    }
    return names.size() >= 2 ? Optional.of(String.join(", ", names)) : Optional.empty();
  }

  private boolean isOr(Expression expression) {
    return expression.isBinaryExpr() && expression.asBinaryExpr().getOperator() == BinaryExpr.Operator.OR;
  }

  private Expression bare(Expression expression) {
    return expression.isEnclosedExpr() ? expression.asEnclosedExpr().getInner() : expression;
  }

  /** The constants an `x == E.A || x == E.B` chain names, as `E.A, E.B`, when every term is such a test. */
  private Optional<String> constants(BinaryExpr chain) {
    TreeSet<String> names = new TreeSet<>();
    List<Expression> terms = new ArrayList<>();
    flatten(chain, terms);
    for (Expression term : terms) {
      if (!term.isBinaryExpr() || term.asBinaryExpr().getOperator() != BinaryExpr.Operator.EQUALS) {
        return Optional.empty();
      }
      Optional<String> constant = Stream.of(term.asBinaryExpr().getLeft(), term.asBinaryExpr().getRight())
          .filter(side -> side.isFieldAccessExpr() && side.asFieldAccessExpr().getScope().isNameExpr()
              && Character.isUpperCase(side.asFieldAccessExpr().getNameAsString().charAt(0)))
          .map(Expression::toString).findFirst();
      if (constant.isEmpty()) {
        return Optional.empty();
      }
      names.add(constant.get());
    }
    return names.size() >= 2 ? Optional.of(String.join(", ", names)) : Optional.empty();
  }

  private void flatten(Expression expression, List<Expression> terms) {
    Expression bare = bare(expression);
    if (isOr(bare)) {
      flatten(bare.asBinaryExpr().getLeft(), terms);
      flatten(bare.asBinaryExpr().getRight(), terms);
    } else {
      terms.add(bare);
    }
  }
}
