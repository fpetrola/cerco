package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Asking a value what type it is, with instanceof or by comparing classes, instead of letting the type answer. */
public final class InstanceOfDispatch extends FileRule {

  @Override
  public String id() {
    return "no-instanceof";
  }

  @Override
  public String principle() {
    return "polymorphism: the object decides, the caller does not inspect it";
  }

  @Override
  public String description() {
    return "instanceof, and comparing getClass() results, dispatch on the type from outside the type; every new "
        + "subtype then has to be remembered at every such place";
  }

  @Override
  public String remedy() {
    return "declare the behaviour as a method of the common type and implement it in each subtype, or dispatch "
        + "with a visitor the subtypes accept; a sealed hierarchy makes the set of cases explicit";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("getClass(), a kind() enum and a switch, an isX() per subtype, a map keyed by Class, a "
        + "sealed switch: the same test, moved",
        new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
            "the tested types are the project's and share a supertype",
            "a method on the common type; a visitor when two or more operations test the same family, or when "
                + "the hierarchy cannot see the caller's module"),
        new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
            "you are picking elements out of a collection another object owns",
            "its owner keeps or hands out the typed view"),
        new Hypothesis(MissingModel.VARIATION_AS_DATA,
            "the test stands for a capability rather than a kind",
            "a method every element answers, or a role the owner knows its members by"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Node> suspects = new ArrayList<>(unit.findAll(InstanceOfExpr.class));
    unit.findAll(MethodCallExpr.class).stream().filter(this::comparesClasses).forEach(suspects::add);
    return suspects.stream().sorted(Node.NODE_BY_BEGIN_POSITION).map(node -> finding(file, node)).toList();
  }

  private Finding finding(ParsedFile file, Node node) {
    String where = enclosing.of(node);
    String what = Stream.of(node).flatMap(n -> n.findFirst(InstanceOfExpr.class, candidate -> candidate == n).stream())
        .findFirst().map(test -> "instanceof " + test.getTypeAsString()).orElse("getClass()==");
    return finding(file, node, where + "|" + what, "`" + clipped(node.toString()) + "` in " + where);
  }

  private boolean comparesClasses(MethodCallExpr call) {
    if (!call.getNameAsString().equals("getClass") || !call.getArguments().isEmpty()) {
      return false;
    }
    return call.findAncestor(BinaryExpr.class, binary -> binary.getLeft() == call || binary.getRight() == call)
        .map(binary -> binary.getOperator() == BinaryExpr.Operator.EQUALS
            || binary.getOperator() == BinaryExpr.Operator.NOT_EQUALS)
        .orElse(false);
  }
}
