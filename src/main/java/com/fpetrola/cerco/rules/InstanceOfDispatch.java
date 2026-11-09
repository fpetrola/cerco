package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
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
