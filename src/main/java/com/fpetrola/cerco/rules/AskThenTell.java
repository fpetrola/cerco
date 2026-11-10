package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * An `if` that reads a property of another object through its getter and, in a branch, sets that same property on
 * it: the caller takes the owner's decision about its own state, and every caller writes its own copy of the
 * transition. Born from an autopsy of oozx, where leaving HALT (clear the flag, step past the HALT) was written in
 * both interrupt paths as `if (state.isHalted()) { state.setHalted(false); ... }`.
 */
public final class AskThenTell extends FileRule {
  private static final Pattern GETTER = Pattern.compile("(?:is|get)([A-Z]\\w*)");

  @Override
  public String id() {
    return "ask-then-tell";
  }

  @Override
  public String principle() {
    return "tell, don't ask: an object that holds a state owns its transitions";
  }

  @Override
  public String description() {
    return "an if asks another object for a property and then sets that property on it: the transition is decided "
        + "by the caller, and each caller keeps its own copy of it";
  }

  @Override
  public String remedy() {
    return "give the owner a method named for what happens (wake(), takeNmi(), open()) that checks and changes its "
        + "own state, and call that instead of asking and setting";
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
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    Set<MethodCallExpr> told = new LinkedHashSet<>();
    unit.findAll(IfStmt.class).forEach(test -> test.getCondition().findAll(MethodCallExpr.class).stream()
        .flatMap(asked -> setters(test, asked)).forEach(told::add));
    return told.stream().map(setter -> finding(file, setter, enclosing.of(setter) + "|" + clipped(setter.toString()),
        "`" + clipped(setter.toString()) + "` sets what the if just asked, in " + enclosing.of(setter))).toList();
  }

  /** The calls in the branches of the if that set, on the same receiver, the property this getter read. */
  private Stream<MethodCallExpr> setters(IfStmt test, MethodCallExpr asked) {
    Matcher getter = GETTER.matcher(asked.getNameAsString());
    Optional<String> receiver = receiver(asked);
    if (!getter.matches() || receiver.isEmpty() || !asked.getArguments().isEmpty()) {
      return Stream.empty();
    }
    String setter = "set" + getter.group(1);
    return Stream.of(Optional.of(test.getThenStmt()), test.getElseStmt()).flatMap(Optional::stream)
        .flatMap(branch -> branch.findAll(MethodCallExpr.class).stream())
        .filter(call -> call.getNameAsString().equals(setter) && receiver(call).equals(receiver));
  }
}
