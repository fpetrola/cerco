package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Names;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import java.util.List;
import java.util.Optional;

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

  private final Clues clues = new Clues();
  private final Compared compared = new Compared();
  private final Hypothesis nothing = new Hypothesis(MissingModel.VARIATION_AS_DATA,
      "absence is a case of the domain (nothing found, nothing plugged)",
      "a null object or a special case that does the empty thing");
  private final Hypothesis notYet = new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
      "what is returned is null until something arrives",
      "make it mandatory, or split the object into its two phases");

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("an Optional unwrapped with get() by every caller, or a sentinel value", nothing, notYet);
  }

  @Override
  public String description() {
    return "`return null` hides an absence inside a type that claims presence; every caller then needs a check the "
        + "compiler cannot demand, and the one that forgets fails far from here";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    Names names = new Names(new Imports(unit));
    return unit.findAll(ReturnStmt.class).stream()
        .filter(statement -> statement.getExpression().filter(value -> value.isNullLiteralExpr()).isPresent())
        .map(statement -> {
          Finding finding = finding(file, statement, enclosing.of(statement) + "|return null",
              "`return null` in " + enclosing.of(statement));
          return read(statement, names).map(finding::read).orElse(finding);
        })
        .toList();
  }

  /** A failure turned into null inside a catch is another matter, and is not read here. */
  private Optional<Reading> read(ReturnStmt statement, Names names) {
    return clues.first(() -> awaited(statement, names), () -> looked(statement), () -> elsewhere(statement),
        () -> alone(statement)).filter(reading -> statement.findAncestor(CatchClause.class).isEmpty());
  }

  /** The returns of the member a statement sits in that give a value. */
  private List<ReturnStmt> answers(ReturnStmt statement) {
    return statement.findAncestor(CallableDeclaration.class).map(callable -> {
      Node member = callable;
      return member.findAll(ReturnStmt.class, found -> found.getExpression()
          .filter(value -> !value.isNullLiteralExpr()).isPresent());
    }).orElse(List.of());
  }

  /** The method finds something inside a loop and returns it from there: this null is the case of not finding. */
  private Optional<Reading> looked(ReturnStmt statement) {
    return Optional.of(nothing.because("the method looks for something in a loop, and this is the case of finding "
        + "none")).filter(reading -> !inALoop(statement) && answers(statement).stream().anyMatch(this::inALoop));
  }

  /** The method gives a value elsewhere: this null is the case in which it has none to give. */
  private Optional<Reading> elsewhere(ReturnStmt statement) {
    return Optional.of(nothing.because("the method answers with a value elsewhere: this is the case where it has "
        + "none")).filter(reading -> !answers(statement).isEmpty());
  }

  /** The whole method is this null: the empty answer of something others answer in earnest. */
  private Optional<Reading> alone(ReturnStmt statement) {
    return Optional.of(nothing.because("the whole method answers nothing: the empty case of what others answer"))
        .filter(reading -> statement.findAncestor(BlockStmt.class).filter(body -> body.getStatements().size() == 1
            && body.getParentNode().flatMap(parent -> statement.findAncestor(CallableDeclaration.class)
            .map(callable -> callable == parent)).orElse(false)).isPresent());
  }

  private boolean inALoop(Node node) {
    return node.findAncestor(ForEachStmt.class).isPresent() || node.findAncestor(ForStmt.class).isPresent()
        || node.findAncestor(WhileStmt.class).isPresent();
  }

  /** The null is returned because a field is null: the object is in the phase before that field arrives. */
  private Optional<Reading> awaited(ReturnStmt statement, Names names) {
    return statement.findAncestor(IfStmt.class).stream().flatMap(guard -> compared.in(guard.getCondition()).stream())
        .filter(term -> term.what() == Decision.Case.ABSENT).map(Compared.Term::subject).flatMap(Optional::stream)
        .filter(names::isField).findFirst()
        .map(field -> notYet.because("it is returned while `" + field + "` is not there yet"));
  }
}
