package com.fpetrola.cerco.model;

import com.github.javaparser.ast.CompilationUnit;
import java.util.Optional;

/**
 * A source file after parsing: its tree when it parsed, the first problem when it did not, its length, and the digest
 * of its code (of the tree as printed when it parsed, so that comments and layout do not count as a change).
 */
public record ParsedFile(SourceFile file, Optional<CompilationUnit> unit, Optional<String> problem, int lines,
    String digest) {

  public String name() {
    return file.name();
  }
}
