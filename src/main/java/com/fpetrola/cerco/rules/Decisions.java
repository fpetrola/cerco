package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Names;
import com.fpetrola.cerco.rules.Decision.Case;
import com.fpetrola.cerco.rules.Decision.Effect;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Reads, around each decision of one file, what will tell its missing model apart at the end of the run. */
final class Decisions {
  private static final Set<UnaryExpr.Operator> STEPS = Set.of(UnaryExpr.Operator.PREFIX_INCREMENT,
      UnaryExpr.Operator.PREFIX_DECREMENT, UnaryExpr.Operator.POSTFIX_INCREMENT, UnaryExpr.Operator.POSTFIX_DECREMENT);
  private final Names names;
  private final Compared compared = new Compared();
  private final Arms arms = new Arms();

  Decisions(CompilationUnit unit) {
    this.names = new Names(new Imports(unit));
  }

  /**
   * A switch, given both as the node it is and as the switch it is; `valued` when it is an expression, whose every
   * arm stands for a value.
   */
  Decision ofSwitch(Finding finding, Node place, SwitchNode decision, boolean valued) {
    List<Compared.Term> terms = new ArrayList<>();
    decision.getEntries().forEach(entry -> entry.getLabels().forEach(label -> terms.add(compared.label(label))));
    return decision(finding, place, Optional.of(decision.getSelector()), terms,
        effectOf(place, decision, valued));
  }

  /** What the branches of a switch do with what it decides on. */
  Effect effectOf(Node place, SwitchNode decision, boolean valued) {
    List<List<Statement>> bodies = new ArrayList<>();
    decision.getEntries().forEach(entry -> bodies.add(entry.getStatements()));
    return effect(place, Optional.of(decision.getSelector()), bodies, valued);
  }

  /** An if / else if chain, from its first if: what its conditions compare, when they all compare one thing. */
  Decision ofChain(Finding finding, IfStmt head) {
    List<Compared.Term> terms = new ArrayList<>();
    List<List<Statement>> bodies = new ArrayList<>();
    Optional<Statement> arm = Optional.of(head);
    while (arm.isPresent()) {
      Optional<IfStmt> test = arm.get().toIfStmt();
      test.ifPresent(one -> terms.addAll(compared.in(one.getCondition())));
      bodies.add(List.of(test.map(IfStmt::getThenStmt).orElse(arm.get())));
      arm = test.flatMap(IfStmt::getElseStmt);
    }
    Optional<Expression> subject = common(terms);
    return decision(finding, head, subject, terms, effect(head, subject, bodies, false));
  }

  /** One test of a value against constants, where no branches hang from it to be read. */
  Decision ofTest(Finding finding, Expression test) {
    List<Compared.Term> terms = compared.in(test);
    return decision(finding, test, common(terms), terms, Effect.UNKNOWN);
  }

  /** A place there is nothing to read in. */
  Decision unread(Finding finding, Node place) {
    return decision(finding, place, Optional.empty(), List.of(compared.unknown()), Effect.UNKNOWN);
  }

  private Decision decision(Finding finding, Node place, Optional<Expression> subject, List<Compared.Term> terms,
      Effect effect) {
    Set<Case> cases = terms.stream().map(Compared.Term::what)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(Case.class)));
    Set<String> named = terms.stream().map(Compared.Term::name).flatMap(Optional::stream)
        .collect(Collectors.toCollection(TreeSet::new));
    String inside = place.findAncestor(TypeDeclaration.class).map(TypeDeclaration::getNameAsString).orElse("");
    return new Decision(finding, selector(subject), inside, cases, named, effect);
  }

  private Decision.Selector selector(Optional<Expression> subject) {
    Optional<Decision.Asked> called = subject.flatMap(Expression::toMethodCallExpr)
        .filter(call -> call.getArguments().isEmpty())
        .flatMap(call -> call.getScope().map(scope -> asked(scope, call.getNameAsString(), true)));
    Optional<Decision.Asked> read = subject.flatMap(Expression::toFieldAccessExpr)
        .map(access -> asked(access.getScope(), access.getNameAsString(), false));
    return new Decision.Selector(subject.map(Expression::toString).orElse(""), subject.flatMap(names::typeOf),
        called.or(() -> read).filter(asked -> !asked.of().equals("this")));
  }

  private Decision.Asked asked(Expression of, String member, boolean called) {
    return new Decision.Asked(of.toString(), names.typeOf(of), member, called);
  }

  /** What every term compares, when they all compare the same thing. */
  private Optional<Expression> common(List<Compared.Term> terms) {
    Set<String> subjects = terms.stream().map(term -> term.subject().map(Expression::toString).orElse(""))
        .collect(Collectors.toSet());
    return terms.stream().findFirst().flatMap(Compared.Term::subject).filter(first -> subjects.size() == 1);
  }

  /** A field the branches assign anew is a state changing; a local they assign is only a value being worked out. */
  private Effect effect(Node decision, Optional<Expression> selector, List<List<Statement>> bodies, boolean valued) {
    if (selector.filter(names::isField).filter(field -> reassigned(decision, field.toString())).isPresent()) {
      return Effect.REASSIGN;
    }
    return arms.allAnswer(bodies, valued) ? Effect.ANSWER : Effect.ACT;
  }

  private boolean reassigned(Node decision, String selector) {
    return !decision.findAll(AssignExpr.class, assignment -> same(assignment.getTarget(), selector)).isEmpty()
        || !decision.findAll(UnaryExpr.class, step -> STEPS.contains(step.getOperator())
            && same(step.getExpression(), selector)).isEmpty();
  }

  private boolean same(Expression written, String selector) {
    String text = written.toString();
    return text.equals(selector) || ("this." + text).equals(selector) || text.equals("this." + selector);
  }
}
