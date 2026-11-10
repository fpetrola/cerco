package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * instanceof by other names: a {@code getClass()} that is not merely printed, or a type code ({@code getNodeType()},
 * {@code getKind()}, {@code getTagName()}, a class name) compared against a constant.
 */
public final class TypeCodeDispatch extends DecisionRule {
  private static final Pattern TYPE_CODE = Pattern.compile(
      "get(\\w*Type|\\w*Kind|TagName|NodeName|SimpleName|TypeName|CanonicalName)|kind|typeName");
  private static final Set<String> NAMING = Set.of("getSimpleName", "getName", "getCanonicalName", "getTypeName",
      "getPackageName");

  @Override
  public String id() {
    return "no-type-code";
  }

  @Override
  public String principle() {
    return "polymorphism: the object decides, the caller does not inspect it";
  }

  @Override
  public String description() {
    return "getClass() used for anything but a name in a message, or a type code (getNodeType(), getKind(), "
        + "getTagName(), a class name) compared with == or equals, is instanceof spelled differently: the caller "
        + "still enumerates the kinds of thing it may receive";
  }

  @Override
  public String remedy() {
    return "give the common type a method that each kind answers in its own way, or wrap the foreign objects (DOM "
        + "nodes, Swing components) in your own types at the boundary, once, and dispatch on those";
  }

  @Override
  protected List<Decision> decisions(ParsedFile file, CompilationUnit unit, Decisions reader) {
    List<MethodCallExpr> suspects = new ArrayList<>();
    for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
      if (inspectsClass(call) || comparesTypeCode(call)) {
        suspects.add(call);
      }
    }
    return suspects.stream().sorted(Node.NODE_BY_BEGIN_POSITION).map(call -> decision(file, call, reader)).toList();
  }

  /** A type code is read with the comparison it sits in; a class that is only inspected has nothing to read. */
  private Decision decision(ParsedFile file, MethodCallExpr call, Decisions reader) {
    Finding finding = finding(file, call);
    return enclosing.parentOf(call, Expression.class).filter(comparison -> comparesTypeCode(call))
        .map(comparison -> reader.ofTest(finding, comparison)).orElseGet(() -> reader.unread(finding, call));
  }

  private boolean inspectsClass(MethodCallExpr call) {
    if (!call.getNameAsString().equals("getClass") || !call.getArguments().isEmpty()) {
      return false;
    }
    return enclosing.parentOf(call, MethodCallExpr.class)
        .map(parent -> !NAMING.contains(parent.getNameAsString()))
        .orElse(true);
  }

  private boolean comparesTypeCode(MethodCallExpr call) {
    if (!TYPE_CODE.matcher(call.getNameAsString()).matches() || !call.getArguments().isEmpty()) {
      return false;
    }
    return comparedWithOperator(call) || comparedWithEquals(call);
  }

  private boolean comparedWithOperator(MethodCallExpr call) {
    return enclosing.parentOf(call, BinaryExpr.class)
        .map(binary -> binary.getOperator() == BinaryExpr.Operator.EQUALS
            || binary.getOperator() == BinaryExpr.Operator.NOT_EQUALS)
        .orElse(false);
  }

  private boolean comparedWithEquals(MethodCallExpr call) {
    return enclosing.parentOf(call, MethodCallExpr.class)
        .filter(parent -> parent.getNameAsString().equals("equals") && parent.getArguments().size() == 1)
        .map(parent -> parent.getScope().filter(scope -> scope == call).isPresent()
            || parent.getArgument(0) == call || isStringConstant(parent.getArgument(0)))
        .orElse(false);
  }

  private boolean isStringConstant(Expression expression) {
    return expression.isStringLiteralExpr() || expression.isFieldAccessExpr() || expression.isNameExpr();
  }

  private Finding finding(ParsedFile file, Node node) {
    String where = enclosing.of(node);
    String text = clipped(node.getParentNode().orElse(node).toString());
    return finding(file, node, where + "|" + text, "`" + text + "` in " + where);
  }
}
