package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A local built from a value, `k = new T(.., v, ..)`, then handed with that same value to a map or a registry,
 * `x.put(k, v)` or `x.registerX(k, v)`: the key was built from the value and holds it, so the map is a second copy
 * of a link the key could answer itself. Born from an autopsy of oozx, where every SpeccyEmulatorCore was built from
 * its machine and then registered with the desk next to it, so a machine's window asked the desk for the machine its
 * own core held.
 */
public final class KeyHoldsValue extends FileRule {
  private static final Pattern KEEPS = Pattern.compile("put|register\\w*");

  @Override
  public String id() {
    return "key-holds-value";
  }

  @Override
  public String principle() {
    return "one owner per fact: an object built from another answers for that link itself";
  }

  @Override
  public String description() {
    return "a key built from a value is stored with that value in a map or registry: the map copies a link the key "
        + "already holds";
  }

  @Override
  public String remedy() {
    return "let the key answer the value it was built from (core.machine()), and drop the map and its upkeep";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(BlockStmt.class).stream().flatMap(block -> block.findAll(VariableDeclarator.class).stream()
        .filter(local -> local.getInitializer().filter(Expression::isObjectCreationExpr).isPresent())
        .flatMap(local -> block.findAll(MethodCallExpr.class).stream().filter(call -> keeps(call, local))))
        .distinct().map(call -> finding(file, call, enclosing.of(call) + "|" + clipped(call.toString()),
            "`" + clipped(call.toString()) + "` keeps a key with the value it was built from, in "
                + enclosing.of(call))).toList();
  }

  private boolean keeps(MethodCallExpr call, VariableDeclarator local) {
    if (!KEEPS.matcher(call.getNameAsString()).matches() || call.getArguments().size() != 2
        || !isName(call.getArgument(0), local.getNameAsString()) || !call.getArgument(1).isNameExpr()) {
      return false;
    }
    ObjectCreationExpr built = local.getInitializer().orElseThrow().asObjectCreationExpr();
    Set<String> from = built.getArguments().stream().filter(Expression::isNameExpr)
        .map(argument -> argument.asNameExpr().getNameAsString()).collect(Collectors.toSet());
    return from.contains(call.getArgument(1).asNameExpr().getNameAsString());
  }

  private boolean isName(Expression expression, String name) {
    return expression.isNameExpr() && expression.asNameExpr().getNameAsString().equals(name);
  }
}
