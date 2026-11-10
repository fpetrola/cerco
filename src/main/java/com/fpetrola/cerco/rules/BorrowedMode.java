package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * A caller that saves another object's mode in a local, sets it to something else, and later sets it back from the
 * local: it borrows the mode around a call the object should offer as one operation. Born from an autopsy of oozx,
 * where a register pair wrote each half with `boolean was = half.isListening(); half.setListening(false);
 * half.write(v); half.setListening(was);`.
 */
public final class BorrowedMode extends FileRule {
  private static final Pattern GETTER = Pattern.compile("(?:is|get)([A-Z]\\w*)");

  @Override
  public String id() {
    return "borrowed-mode";
  }

  @Override
  public String principle() {
    return "tell, don't ask: what a caller does with another object's mode switched for a moment is an operation of "
        + "that object";
  }

  @Override
  public String description() {
    return "another object's mode is saved in a local, changed, and set back from the local: the caller borrows it "
        + "around a call the object should offer itself";
  }

  @Override
  public String remedy() {
    return "give the object the operation the borrow surrounds (writeUnheard(v), measureCompact()) and call it";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a private method in the caller that saves and restores: every other caller still has "
        + "to remember it",
        new Hypothesis(MissingModel.PROTOCOL_BY_CONVENTION,
            "the mode is set, used and restored around some work",
            "one operation of the owner that takes the work and restores itself (execute-around)"),
        new Hypothesis(MissingModel.STATE_WITHOUT_AN_OBJECT,
            "the borrowed mode is a phase the object goes through",
            "the phase as an object of its own"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(BlockStmt.class).stream().flatMap(block -> {
      List<Statement> statements = block.getStatements();
      return IntStream.range(0, statements.size()).boxed()
          .flatMap(i -> saved(statements.get(i)).filter(save -> restored(save, statements.subList(i + 1,
              statements.size()))).stream());
    }).map(save -> {
      MethodCallExpr get = save.getInitializer().orElseThrow().asMethodCallExpr();
      return finding(file, save, enclosing.of(save) + "|" + clipped(get.toString()), "`" + clipped(get.toString())
          + "` is saved, switched and set back by the caller, in " + enclosing.of(save));
    }).toList();
  }

  /** A single local initialized from a getter of another object. */
  private Optional<VariableDeclarator> saved(Statement statement) {
    return Optional.of(statement).filter(Statement::isExpressionStmt)
        .map(s -> s.asExpressionStmt().getExpression()).filter(e -> e.isVariableDeclarationExpr())
        .map(e -> e.asVariableDeclarationExpr().getVariables()).filter(vars -> vars.size() == 1).map(vars -> vars.get(0))
        .filter(var -> var.getInitializer().filter(init -> init.isMethodCallExpr()
            && GETTER.matcher(init.asMethodCallExpr().getNameAsString()).matches()
            && init.asMethodCallExpr().getArguments().isEmpty()
            && receiver(init.asMethodCallExpr()).isPresent()).isPresent());
  }

  /** Whether the statements after it switch that property to something else and then set it back from the local. */
  private boolean restored(VariableDeclarator save, List<Statement> after) {
    MethodCallExpr get = save.getInitializer().orElseThrow().asMethodCallExpr();
    String setter = GETTER.matcher(get.getNameAsString()).replaceFirst("set$1");
    String local = save.getNameAsString();
    List<MethodCallExpr> sets = after.stream().flatMap(s -> s.findAll(MethodCallExpr.class).stream())
        .filter(call -> call.getNameAsString().equals(setter) && call.getArguments().size() == 1
            && receiver(call).equals(receiver(get))).toList();
    int back = IntStream.range(0, sets.size()).filter(i -> isLocal(sets.get(i), local)).findFirst().orElse(-1);
    return back > 0 && sets.subList(0, back).stream().anyMatch(set -> !isLocal(set, local));
  }

  private boolean isLocal(MethodCallExpr set, String local) {
    return set.getArgument(0).isNameExpr() && set.getArgument(0).asNameExpr().getNameAsString().equals(local);
  }
}
