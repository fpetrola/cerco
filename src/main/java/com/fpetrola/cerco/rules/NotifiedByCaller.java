package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * A setter called on another object and, in the very next statement, the caller announcing it (`notify*`, `fire*`,
 * `publish*` of its own): the change and its announcement are two steps that every caller has to remember, and the
 * ones that forget change the state silently. Born from an autopsy of oozx, where pausing the machine loop was
 * written three times as `speccy.loop.setPaused(..); notifyPauseStateChange(..)` while a breakpoint, a restored
 * session and the debugger paused it without telling anyone.
 */
public final class NotifiedByCaller extends FileRule {
  private static final Pattern SETTER = Pattern.compile("set[A-Z]\\w*");
  private static final Pattern ANNOUNCE = Pattern.compile("(?:notify|fire|publish)[A-Z]?\\w*");

  @Override
  public String id() {
    return "notified-by-caller";
  }

  @Override
  public String principle() {
    return "a change of state and its announcement are one operation of the object that owns the state";
  }

  @Override
  public String description() {
    return "a setter on another object followed by the caller's own notify/fire/publish: the caller announces a "
        + "change that is not its own, and every other caller making it stays silent";
  }

  @Override
  public String remedy() {
    return "let the object whose state changes announce it (listeners on that object, or the setter firing), and "
        + "subscribe the one that used to announce";
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(BlockStmt.class).stream().flatMap(block -> {
      List<Statement> statements = block.getStatements();
      return IntStream.range(1, statements.size())
          .filter(i -> announces(statements.get(i)))
          .mapToObj(i -> foreignSetter(statements.get(i - 1))).flatMap(Optional::stream);
    }).map(set -> finding(file, set, enclosing.of(set) + "|" + clipped(set.toString()),
        "`" + clipped(set.toString()) + "` is announced by the caller, in " + enclosing.of(set))).toList();
  }

  private Optional<MethodCallExpr> foreignSetter(Statement statement) {
    return call(statement).filter(call -> SETTER.matcher(call.getNameAsString()).matches()
        && receiver(call).isPresent());
  }

  private boolean announces(Statement statement) {
    return call(statement).filter(call -> ANNOUNCE.matcher(call.getNameAsString()).matches()
        && call.getScope().map(scope -> scope.isThisExpr()).orElse(true)).isPresent();
  }

  private Optional<MethodCallExpr> call(Statement statement) {
    return Optional.of(statement).filter(Statement::isExpressionStmt)
        .map(s -> s.asExpressionStmt().getExpression()).filter(e -> e.isMethodCallExpr())
        .map(e -> e.asMethodCallExpr());
  }
}
