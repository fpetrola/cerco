package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.util.List;

/** Returning null: an absence the caller has to remember to check for, and forgets. */
public final class NullReturn extends FileRule {

  @Override
  public String id() {
    return "no-null-return";
  }

  @Override
  public String principle() {
    return "honesty: a method's type tells the whole truth about what it returns";
  }

  @Override
  public String remedy() {
    return "return an Optional, an empty collection or a null object that behaves as nothing; a visitor with nothing "
        + "to return is a visitor over Void and should not exist, give it a result type";
  }

  @Override
  public String description() {
    return "`return null` hides an absence inside a type that claims presence; every caller then needs a check the "
        + "compiler cannot demand, and the one that forgets fails far from here";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(ReturnStmt.class).stream()
        .filter(statement -> statement.getExpression().filter(value -> value.isNullLiteralExpr()).isPresent())
        .map(statement -> finding(file, statement, enclosing.of(statement) + "|return null",
            "`return null` in " + enclosing.of(statement)))
        .toList();
  }
}
