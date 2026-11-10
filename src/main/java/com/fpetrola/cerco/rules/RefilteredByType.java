package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Two or more loops in one file over the same call, each testing its element for the same type: the class keeps
 * asking a mixed collection for one kind of part it should hold itself. Born from an autopsy of oozx, where the desk
 * walked `desktop.getAllFrames()` testing for its machine windows in every question about its machines, and so had
 * two different answers for the machine in use.
 */
public final class RefilteredByType extends FileRule {

  @Override
  public String id() {
    return "refiltered-by-type";
  }

  @Override
  public String principle() {
    return "a container that needs its parts by kind keeps them by kind";
  }

  @Override
  public String description() {
    return "several loops over the same collection test each element for the same type: which elements are of that "
        + "kind is a fact the class re-derives every time instead of keeping";
  }

  @Override
  public String remedy() {
    return "keep the elements of that kind where they are added and removed (a typed list or map) and ask it";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    Map<String, List<ForEachStmt>> loops = new LinkedHashMap<>();
    unit.findAll(ForEachStmt.class).stream().filter(loop -> loop.getIterable().isMethodCallExpr())
        .forEach(loop -> tested(loop).forEach(type -> loops.computeIfAbsent(
            clipped(loop.getIterable().toString()) + "|" + type, key -> new ArrayList<>()).add(loop)));
    return loops.entrySet().stream().filter(entry -> entry.getValue().size() >= 2).map(entry -> {
      String[] parts = entry.getKey().split("\\|", 2);
      ForEachStmt first = entry.getValue().get(0);
      return finding(file, first, enclosing.of(first) + "|" + entry.getKey(), entry.getValue().size() + " loops over `"
          + parts[0] + "` pick out " + parts[1] + ", in " + enclosing.of(first));
    }).toList();
  }

  /** The types the loop's own element is tested for in its body. */
  private Stream<String> tested(ForEachStmt loop) {
    String element = loop.getVariableDeclarator().getNameAsString();
    return loop.getBody().findAll(InstanceOfExpr.class).stream()
        .filter(test -> test.getExpression().isNameExpr()
            && test.getExpression().asNameExpr().getNameAsString().equals(element))
        .map(test -> test.getType().asString()).distinct();
  }
}
