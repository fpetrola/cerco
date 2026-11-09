package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

/** Code a tool wrote: the gate does not judge it, since nobody is going to fix it by hand. */
public final class Generated {

  public boolean marks(ParsedFile file) {
    return file.unit().map(this::marks).orElse(false);
  }

  private boolean marks(CompilationUnit unit) {
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      for (AnnotationExpr annotation : type.getAnnotations()) {
        if (annotation.getNameAsString().endsWith("Generated")) {
          return true;
        }
      }
    }
    return false;
  }
}
