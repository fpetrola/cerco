package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A call that sets one property of another object to what a different property of that same object holds,
 * `x.setA(x.getB())`: the caller moves state inside an object it does not own, a transition the object should name.
 * Born from an autopsy of oozx, where RETN was `state.setIff1(state.isIff2())` and the NMI
 * `state.setIff2(state.isIff1())`, the interrupt flip-flops' protocol written by their callers.
 */
public final class PropertyShuffle extends FileRule {
  private static final Pattern SETTER = Pattern.compile("set([A-Z]\\w*)");
  private static final Pattern GETTER = Pattern.compile("(?:is|get)([A-Z]\\w*)");

  @Override
  public String id() {
    return "property-shuffle";
  }

  @Override
  public String principle() {
    return "tell, don't ask: a transition between two parts of an object's state belongs to that object";
  }

  @Override
  public String description() {
    return "a property of another object is set to a different property of that same object: the caller moves "
        + "state inside an object it does not own";
  }

  @Override
  public String remedy() {
    return "give the object a method named for the transition (enterNmi(), leaveNmi(), restore()) and call it";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(MethodCallExpr.class).stream().filter(this::shuffles)
        .map(call -> finding(file, call, enclosing.of(call) + "|" + clipped(call.toString()),
            "`" + clipped(call.toString()) + "` moves one property of an object into another, in "
                + enclosing.of(call))).toList();
  }

  private boolean shuffles(MethodCallExpr set) {
    Matcher setter = SETTER.matcher(set.getNameAsString());
    if (!setter.matches() || set.getArguments().size() != 1 || receiver(set).isEmpty()
        || !set.getArgument(0).isMethodCallExpr()) {
      return false;
    }
    MethodCallExpr get = set.getArgument(0).asMethodCallExpr();
    Matcher getter = GETTER.matcher(get.getNameAsString());
    return getter.matches() && get.getArguments().isEmpty() && receiver(get).equals(receiver(set))
        && !getter.group(1).equals(setter.group(1));
  }
}
