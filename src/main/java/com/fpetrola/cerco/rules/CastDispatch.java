package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import java.util.ArrayList;
import java.util.List;

/** Forcing a value into a type it was not declared as: a cast, or {@code Class.cast} dressed as a method. */
public final class CastDispatch extends FileRule {

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
  public Diagnosis diagnosis() {
    return new Diagnosis("a generic method that casts inside, or an instanceof pattern: the cast is still there",
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
    List<Node> suspects = new ArrayList<>();
    unit.findAll(CastExpr.class).stream().filter(cast -> cast.getType().isClassOrInterfaceType()).forEach(suspects::add);
    unit.findAll(MethodCallExpr.class).stream().filter(this::isClassCast).forEach(suspects::add);
    unit.findAll(MethodReferenceExpr.class).stream().filter(ref -> ref.getIdentifier().equals("cast"))
        .forEach(suspects::add);
    return suspects.stream().sorted(Node.NODE_BY_BEGIN_POSITION).map(node -> finding(file, node)).toList();
  }

  private boolean isClassCast(MethodCallExpr call) {
    return call.getNameAsString().equals("cast") && call.getArguments().size() == 1 && call.getScope().isPresent();
  }

  private Finding finding(ParsedFile file, Node node) {
    String where = enclosing.of(node);
    String text = clipped(node.toString());
    return finding(file, node, where + "|" + text, "`" + text + "` in " + where);
  }
}
