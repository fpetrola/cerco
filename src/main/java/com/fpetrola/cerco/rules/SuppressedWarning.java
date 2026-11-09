package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import java.util.List;

/** Telling the compiler to look away: a design problem it saw and the code decided to keep. */
public final class SuppressedWarning extends FileRule {

  @Override
  public String id() {
    return "no-suppressed-warning";
  }

  @Override
  public String principle() {
    return "honesty: the compiler's warnings are findings too";
  }

  @Override
  public String description() {
    return "@SuppressWarnings silences unchecked casts, raw types and the rest at the one place that could fix them; "
        + "the problem stays and stops being visible";
  }

  @Override
  public String remedy() {
    return "fix what the compiler warned about: type the collection, drop the raw type, remove the unused member; "
        + "where a library forces a raw type, wrap it once in a typed class of your own at the boundary";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(AnnotationExpr.class).stream()
        .filter(annotation -> annotation.getNameAsString().equals("SuppressWarnings"))
        .map(annotation -> finding(file, annotation, enclosing.of(annotation) + "|" + clipped(annotation.toString()),
            "`" + clipped(annotation.toString()) + "` on " + enclosing.of(annotation)))
        .toList();
  }
}
