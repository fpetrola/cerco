package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import java.util.ArrayList;
import java.util.List;

/**
 * A method that is not private and branches on its own boolean parameter: two methods behind one name, told apart
 * by a true or a false at every call. Candidate from the plan of model smells (Fowler's flag argument).
 */
public final class FlagArgument extends FileRule {

  @Override
  public String id() {
    return "flag-argument";
  }

  @Override
  public String principle() {
    return "a method does one thing: which one the caller wants is said by the method it calls";
  }

  @Override
  public String description() {
    return "a method that is not private branches on its own boolean parameter: it is two methods behind one name, "
        + "and every caller has to know what true means";
  }

  @Override
  public String remedy() {
    return "two methods, each named for what it does; when the flag is the state of something, the object that has "
        + "that state decides and the parameter goes away";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("an enum of two constants and the same if, or an overload that passes the literal on",
        new Hypothesis(MissingModel.VARIATION_AS_DATA,
            "the caller picks one of two behaviours with true or false",
            "two methods, each named for what it does; or the object whose state the flag is decides"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (MethodDeclaration method : unit.findAll(MethodDeclaration.class, one -> !one.isPrivate()
        && !isAPropertySetter(one))) {
      method.getParameters().stream().filter(this::isAFlag).map(Parameter::getNameAsString)
          .filter(flag -> branchesOn(method, flag))
          .forEach(flag -> findings.add(finding(file, method, enclosing.signatureOf(method) + "|" + flag,
              "`" + flag + "` picks which of two things " + enclosing.signatureOf(method) + " does")));
    }
    return findings;
  }

  /** `setX(boolean)` sets a property, which a rule of its own looks at when it is driven from outside. */
  private boolean isAPropertySetter(MethodDeclaration method) {
    return method.getNameAsString().matches("set[A-Z].*") && method.getParameters().size() == 1;
  }

  private boolean isAFlag(Parameter parameter) {
    return parameter.getType().asString().matches("boolean|Boolean");
  }

  private boolean branchesOn(MethodDeclaration method, String flag) {
    return !method.findAll(IfStmt.class, branch -> isTheFlag(branch.getCondition(), flag)).isEmpty()
        || !method.findAll(ConditionalExpr.class, branch -> isTheFlag(branch.getCondition(), flag)).isEmpty();
  }

  /** The flag alone or negated: a condition that says nothing but the parameter. */
  private boolean isTheFlag(Expression condition, String flag) {
    return condition.toString().replaceAll("[!()\\s]", "").equals(flag);
  }
}
