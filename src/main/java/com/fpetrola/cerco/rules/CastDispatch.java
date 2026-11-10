package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.visitor.GenericVisitor;
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults;
import java.util.ArrayList;
import java.util.List;

/** Forcing a value into a type it was not declared as: a cast, or {@code Class.cast} dressed as a method. */
public final class CastDispatch extends TypeTestRule {

  @Override
  public String id() {
    return "no-cast";
  }

  @Override
  public String principle() {
    return "polymorphism: a value is used through the type it was declared as";
  }

  @Override
  public String description() {
    return "a cast to a reference type, `Type.class.cast(x)` or `Type.class::cast` says the caller knows better than "
        + "the type system which subtype it holds; it is instanceof with the check left out";
  }

  @Override
  public String remedy() {
    return "declare the variable, field or parameter with the type you need, let the producer return that type, or "
        + "move the behaviour into the type so nobody has to narrow it; a cast of a primitive is not a dispatch";
  }

  @Override
  protected String disguises() {
    return "a generic method that casts inside, or an instanceof pattern: the cast is still there";
  }

  @Override
  protected List<Node> suspects(CompilationUnit unit) {
    List<Node> suspects = new ArrayList<>();
    unit.findAll(CastExpr.class).stream().filter(cast -> cast.getType().isClassOrInterfaceType()).forEach(suspects::add);
    unit.findAll(MethodCallExpr.class).stream().filter(this::isClassCast).forEach(suspects::add);
    unit.findAll(MethodReferenceExpr.class).stream().filter(ref -> ref.getIdentifier().equals("cast"))
        .forEach(suspects::add);
    return suspects;
  }

  @Override
  protected GenericVisitor<TypeTest, In> asked() {
    return new Asked();
  }

  private boolean isClassCast(MethodCallExpr call) {
    return call.getNameAsString().equals("cast") && call.getArguments().size() == 1 && call.getScope().isPresent();
  }

  private Finding found(In in, Node node) {
    String where = enclosing.of(node);
    String text = clipped(node.toString());
    return finding(in.file(), node, where + "|" + text, "`" + text + "` in " + where);
  }

  /** A cast names its type; `Type.class.cast(x)` and `Type.class::cast` name it through the class they are asked of. */
  private final class Asked extends GenericVisitorWithDefaults<TypeTest, In> {

    @Override
    public TypeTest visit(CastExpr cast, In in) {
      return in.reader().of(found(in, cast), cast, cast.getType(), cast.getExpression());
    }

    @Override
    public TypeTest visit(MethodCallExpr call, In in) {
      Finding finding = found(in, call);
      return call.getScope().flatMap(scope -> scope.toClassExpr()).map(ClassExpr::getType)
          .map(type -> in.reader().of(finding, call, type, call.getArgument(0)))
          .orElseGet(() -> in.reader().unread(finding, call));
    }

    @Override
    public TypeTest visit(MethodReferenceExpr reference, In in) {
      Finding finding = found(in, reference);
      return reference.getScope().toClassExpr().map(ClassExpr::getType)
          .map(type -> in.reader().sifting(finding, reference, type))
          .orElseGet(() -> in.reader().unread(finding, reference));
    }
  }
}
