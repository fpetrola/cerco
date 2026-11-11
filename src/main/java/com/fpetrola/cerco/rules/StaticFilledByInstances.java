package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A static field that a constructor or an instance method of its own type changes: every instance writes into what
 * all of them share, so one fills it and another empties it for everybody. Candidate born from the autopsies of oozx
 * (SwingKeyboard.unicodeKeysymsHash, filled by each window and cleared for all by the end() of any).
 */
public final class StaticFilledByInstances extends FileRule {
  private static final Set<String> CHANGING = Set.of("add", "addAll", "addFirst", "addLast", "put", "putAll",
      "putIfAbsent", "computeIfAbsent", "merge", "remove", "removeAll", "removeIf", "retainAll", "clear", "push",
      "pop", "offer", "poll", "set", "append", "insert", "delete", "setLength", "sort");
  private static final Set<UnaryExpr.Operator> STEPS = Set.of(UnaryExpr.Operator.POSTFIX_INCREMENT,
      UnaryExpr.Operator.POSTFIX_DECREMENT, UnaryExpr.Operator.PREFIX_INCREMENT, UnaryExpr.Operator.PREFIX_DECREMENT);

  @Override
  public String id() {
    return "static-filled-by-instances";
  }

  @Override
  public String principle() {
    return "what an instance writes is its own: state shared by all is owned by one object, not by whoever comes";
  }

  @Override
  public String description() {
    return "a constructor or an instance method changes a static field of its own type: every instance writes into "
        + "the one state all of them share, and any of them breaks it for the rest";
  }

  @Override
  public String remedy() {
    return "a field of the instance when each one needs its own; when it really is one for all, an object that "
        + "owns it, built once and handed to each instance";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a static method the instances call to do the same writing",
        new Hypothesis(MissingModel.KNOWLEDGE_WITHOUT_ONE_OWNER,
            "each instance writes a state all instances share",
            "a field of the instance; or one object that owns it, handed to each instance"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      Set<String> shared = type.getFields().stream().filter(FieldDeclaration::isStatic)
          .flatMap(field -> field.getVariables().stream()).map(VariableDeclarator::getNameAsString)
          .collect(Collectors.toCollection(TreeSet::new));
      List<CallableDeclaration<?>> ofInstances = new ArrayList<>(type.getConstructors());
      type.getMethods().stream().filter(method -> !method.isStatic()).forEach(ofInstances::add);
      ofInstances
          .forEach(member -> changedBy(member, type.getNameAsString()).filter(shared::contains).distinct()
              .forEach(field -> findings.add(finding(file, member, enclosing.signatureOf(member) + "|" + field,
                  "`" + field + "` is static and " + enclosing.signatureOf(member) + " changes it: every instance "
                      + "writes into what all of them share"))));
    }
    return findings;
  }

  /** The names a member assigns, steps or tells to change, leaving out its own parameters and local variables. */
  private Stream<String> changedBy(CallableDeclaration<?> member, String type) {
    Set<String> own = Stream.concat(member.getParameters().stream().map(Parameter::getNameAsString),
            member.findAll(VariableDeclarator.class).stream().map(VariableDeclarator::getNameAsString))
        .collect(Collectors.toSet());
    Stream<Expression> assigned = member.findAll(AssignExpr.class).stream().map(AssignExpr::getTarget);
    Stream<Expression> stepped = member.findAll(UnaryExpr.class, step -> STEPS.contains(step.getOperator())).stream()
        .map(UnaryExpr::getExpression);
    Stream<Expression> told = member.findAll(MethodCallExpr.class, call -> CHANGING.contains(call.getNameAsString()))
        .stream().map(MethodCallExpr::getScope).flatMap(Optional::stream);
    return Stream.of(assigned, stepped, told).flatMap(changed -> changed)
        .map(changed -> named(changed, type)).flatMap(Optional::stream).filter(name -> !own.contains(name));
  }

  /** The field an expression stands for: `f`, `f[i]` or `Type.f`. */
  private Optional<String> named(Expression changed, String type) {
    Expression base = changed.toArrayAccessExpr().map(ArrayAccessExpr::getName).orElse(changed);
    return base.toNameExpr().map(NameExpr::getNameAsString)
        .or(() -> base.toFieldAccessExpr().filter(access -> access.getScope().toString().equals(type))
            .map(FieldAccessExpr::getNameAsString));
  }
}
