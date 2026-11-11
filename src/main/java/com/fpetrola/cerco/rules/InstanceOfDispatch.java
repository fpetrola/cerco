package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Asking a value what type it is, with instanceof or by comparing classes, instead of letting the type answer. */
public final class InstanceOfDispatch extends TypeTestRule {
  private final ObjectProtocol everyObject = new ObjectProtocol();

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
  protected String disguises() {
    return "getClass(), a kind() enum and a switch, an isX() per subtype, a map keyed by Class, a sealed switch: the "
        + "same test, moved";
  }

  @Override
  protected List<Node> suspects(CompilationUnit unit) {
    List<Node> suspects = new ArrayList<>(unit.findAll(InstanceOfExpr.class));
    unit.findAll(MethodCallExpr.class).stream().filter(call -> comparison(call).isPresent()).forEach(suspects::add);
    return suspects;
  }

  @Override
  protected GenericVisitor<TypeTest, In> asked() {
    return new Asked();
  }

  /** The comparison a `getClass()` call sits in, when it is one side of an == or a !=. */
  private Optional<BinaryExpr> comparison(MethodCallExpr call) {
    if (!everyObject.asksItsClass(call)) {
      return Optional.empty();
    }
    return call.findAncestor(BinaryExpr.class, binary -> binary.getLeft() == call || binary.getRight() == call)
        .filter(binary -> binary.getOperator() == BinaryExpr.Operator.EQUALS
            || binary.getOperator() == BinaryExpr.Operator.NOT_EQUALS);
  }

  /** An instanceof names its type and its value; a compared `getClass()` has them on either side of the ==. */
  private final class Asked extends GenericVisitorWithDefaults<TypeTest, In> {

    @Override
    public TypeTest visit(InstanceOfExpr test, In in) {
      String where = enclosing.of(test);
      return in.reader().of(finding(in.file(), test, where + "|instanceof " + test.getTypeAsString(),
          "`" + clipped(test.toString()) + "` in " + where), test, test.getType(), test.getExpression());
    }

    @Override
    public TypeTest visit(MethodCallExpr call, In in) {
      String where = enclosing.of(call);
      Finding finding = finding(in.file(), call, where + "|getClass()==", "`" + clipped(call.toString()) + "` in "
          + where);
      Expression subject = call.getScope().orElseGet(ThisExpr::new);
      return comparison(call).stream().flatMap(binary -> Stream.of(binary.getLeft(), binary.getRight()))
          .map(Expression::toClassExpr).flatMap(Optional::stream).map(ClassExpr::getType).findFirst()
          .map(type -> in.reader().of(finding, call, type, subject))
          .orElseGet(() -> in.reader().unread(finding, call));
    }
  }
}
