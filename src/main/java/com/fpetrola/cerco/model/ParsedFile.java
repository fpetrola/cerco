package com.fpetrola.cerco.model;

import com.github.javaparser.ast.CompilationUnit;
import java.util.Optional;

/** A source file after parsing: its tree when it parsed, the first problem when it did not, and its length. */
public record ParsedFile(SourceFile file, Optional<CompilationUnit> unit, Optional<String> problem, int lines) {

  public String name() {
    return file.name();
  }
}
