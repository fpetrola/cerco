package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A boolean flag that other classes switch on and off through its bare setter: a state machine nobody owns. The
 * transitions (and the rule for when each is allowed) end up spread over the callers, one of them forgets its half,
 * and the flag means whatever the last writer thought. Found in oozx, where a pendingEI flag lived in State, the CPU
 * loop switched it off, snapshots copied it, and nothing ever switched it on.
 */
public final class FlagDrivenFromOutside extends ProjectRule<FlagDrivenFromOutside.Seen> {

  /** The files that declare each bare boolean setter (by name), and every call that hands one a literal. */
  public record Seen(Map<String, Set<String>> setters, List<Call> calls) {
    Seen() {
      this(new HashMap<>(), new ArrayList<>());
    }
  }

  /** A call such as {@code state.setPendingEI(false)}: receiver given, argument a literal. */
  public record Call(String file, int line, String member, String name, String text) {
  }

  @Override
  public String id() {
    return "flag-driven-from-outside";
  }

  @Override
  public String principle() {
    return "single owner of a concept: a state and its transitions belong to the object that holds the state";
  }

  @Override
  public String description() {
    return "another class calls a bare setter of a boolean field with true or false: the flag is a protocol driven "
        + "from outside, its transitions are scattered over the callers and nothing guarantees they all happen";
  }

  @Override
  public String remedy() {
    return "give the owner a method named after the transition (enableInterrupt(), instructionRan(), close()) that "
        + "changes the flag and whatever goes with it, and a query for what the flag decides; drop the setter";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("an isX() that wraps the getter, or the setter under another name: the caller still decides",
        new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
            "what is asked and what is decided belong to the same object",
            "give the owner the operation and call it"),
        new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
            "a flag or a mode of the owner is being driven",
            "the owner changes its own mode, or the mode becomes an object"));
  }

  @Override
  protected Seen start() {
    return new Seen();
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Seen seen) {
    unit.findAll(MethodDeclaration.class, this::isBareBooleanSetter).forEach(setter -> seen.setters()
        .computeIfAbsent(setter.getNameAsString(), name -> new HashSet<>()).add(file.name()));
    unit.findAll(MethodCallExpr.class, this::handsALiteralToASetter).forEach(call -> seen.calls().add(new Call(
        file.name(), call.getBegin().map(position -> position.line).orElse(1), enclosing.of(call),
        call.getNameAsString(), clipped(call.toString()))));
  }

  /** {@code void setX(boolean x) { this.x = x; }} and nothing else: a switch with no behaviour of its own. */
  private boolean isBareBooleanSetter(MethodDeclaration method) {
    boolean shape = method.getNameAsString().matches("set[A-Z]\\w*") && method.getParameters().size() == 1
        && method.getParameter(0).getType().asString().matches("boolean|Boolean");
    List<Statement> body = method.getBody().map(block -> block.getStatements().stream().toList()).orElse(List.of());
    return shape && body.size() == 1 && body.get(0).isExpressionStmt()
        && body.get(0).asExpressionStmt().getExpression().toAssignExpr()
            .filter(assign -> assign.getOperator() == AssignExpr.Operator.ASSIGN)
            .filter(assign -> assign.getValue().toString().equals(method.getParameter(0).getNameAsString()))
            .isPresent();
  }

  /** Calls on the setter of someone else ({@code x.setY(true)}); a type setting its own flag is left alone. */
  private boolean handsALiteralToASetter(MethodCallExpr call) {
    return call.getScope().isPresent() && call.getNameAsString().startsWith("set") && call.getArguments().size() == 1
        && call.getArgument(0).isBooleanLiteralExpr();
  }

  @Override
  protected List<Finding> conclude(Seen seen, Project project) {
    return seen.calls().stream()
        .filter(call -> seen.setters().containsKey(call.name()))
        .filter(call -> !seen.setters().get(call.name()).contains(call.file()))
        .map(call -> new Finding(id(), call.file(), call.line(), call.file() + "|" + call.member() + "|" + call.text(),
            "`" + call.text() + "` drives a flag of another class from " + call.member()))
        .toList();
  }
}
