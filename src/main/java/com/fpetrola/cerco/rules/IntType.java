package com.fpetrola.cerco.rules;

import com.github.javaparser.ast.type.Type;
import java.util.regex.Pattern;

/** The primitive int as a declaration writes it: what a position is passed as. */
final class IntType {
  private static final Pattern INT = Pattern.compile("int");

  boolean writes(Type type) {
    return INT.matcher(type.asString()).matches();
  }
}
