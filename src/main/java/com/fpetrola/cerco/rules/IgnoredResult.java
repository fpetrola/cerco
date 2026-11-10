package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A call whose answer is thrown away: the project's own method says whether it worked (boolean) or whether it found
 * something (Optional), and the caller does not look. Resolved by name and arity across the project, so a name every
 * collection also uses is left alone.
 */
public final class IgnoredResult extends ProjectRule<IgnoredResult.Seen> {
  private static final Set<String> SHARED_WITH_THE_JDK = Set.of("add", "addAll", "remove", "removeAll", "removeIf",
      "retainAll", "offer", "delete", "deleteIfExists", "mkdir", "mkdirs", "createNewFile", "renameTo", "cancel",
      "tryLock", "await", "compareAndSet", "setReadable", "setWritable", "setExecutable", "setLastModified",
      "containsKey", "contains", "equals", "offerFirst", "offerLast", "tryAdvance", "markSupported", "put",
      "putIfAbsent", "replace", "execute", "awaitTermination", "interrupt", "signal");

  /** Which signatures (name/arity) always report, and every statement-level call seen so far. */
  public record Seen(Map<String, Boolean> reporting, List<Call> calls) {
  }

  /** A call standing alone as a statement. */
  public record Call(String file, int line, String member, String name, int arity, String text) {
  }

  @Override
  public String id() {
    return "no-ignored-result";
  }

  @Override
  public String principle() {
    return "honesty: a method that reports failure is heard";
  }

  @Override
  public String description() {
    return "a statement that calls one of this project's methods returning boolean or Optional and drops the result "
        + "turns a reported failure into silence; the bug shows up later, somewhere else, with no message";
  }

  @Override
  public String remedy() {
    return "act on the answer (branch, throw, log at least), or change the method: one that cannot fail returns void, "
        + "one that must fail throws";
  }

  @Override
  protected Seen start() {
    return new Seen(new HashMap<>(), new ArrayList<>());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Seen seen) {
    for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
      if (!SHARED_WITH_THE_JDK.contains(method.getNameAsString())) {
        String signature = method.getNameAsString() + "/" + method.getParameters().size();
        boolean reports = method.getType().asString().matches("boolean|Boolean|Optional(<.*>)?");
        seen.reporting().merge(signature, reports, (before, now) -> before && now);
      }
    }
    for (ExpressionStmt statement : unit.findAll(ExpressionStmt.class, this::isStatementOfItsOwn)) {
      statement.getExpression().toMethodCallExpr().ifPresent(call -> seen.calls().add(new Call(file.name(),
          statement.getBegin().map(position -> position.line).orElse(1), enclosing.of(statement),
          call.getNameAsString(), call.getArguments().size(), clipped(call.toString()))));
    }
  }

  /** A lambda's expression body is also an ExpressionStmt to the parser, but its value is the lambda's answer. */
  private boolean isStatementOfItsOwn(ExpressionStmt statement) {
    return enclosing.parentOf(statement, LambdaExpr.class).isEmpty();
  }

  @Override
  protected List<Finding> conclude(Seen seen, Project project) {
    return seen.calls().stream()
        .filter(call -> seen.reporting().getOrDefault(call.name() + "/" + call.arity(), false))
        .map(call -> new Finding(id(), call.file(), call.line(), call.file() + "|" + call.member() + "|"
            + call.text(), "`" + call.text() + "` drops a boolean or Optional in " + call.member()))
        .toList();
  }
}
