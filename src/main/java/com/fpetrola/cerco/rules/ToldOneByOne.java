package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Chain;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Names;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Three or more statements in a row that make the same call on different objects: everyone who has to hear of
 * something is told by hand, and whoever tells keeps the list of them in its code. Candidate from the plan of model
 * smells (Kerievsky's Replace Hard-Coded Notifications with Observer).
 */
public final class ToldOneByOne extends PlacesRule<ToldOneByOne.Site> {
  private static final int TOLD = 3;

  /** One run of calls: where, what is said, to how many, and how the first of them is reached. */
  public record Site(String file, int line, String member, String said, int told, Optional<Chain> first)
      implements Place {

    Finding found(String rule) {
      return new Finding(rule, file, line, file + "|" + member + "|" + said + "()", told
          + " objects in a row are told `" + said + "()` in " + member);
    }
  }

  @Override
  public String id() {
    return "told-one-by-one";
  }

  @Override
  public String principle() {
    return "whoever has something to say says it once; who listens is not written into the one who speaks";
  }

  @Override
  public String description() {
    return TOLD + " or more statements in a row make the same call on different objects: each one that has to "
        + "hear is named by hand, and a new one means finding every such place";
  }

  @Override
  public String remedy() {
    return "the objects told are a group: they register as listeners and are told in one call (an observer), or "
        + "they are parts of one thing that passes the call on (a composite)";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a private method holding the same row of calls, or an array literal walked on the spot",
        new Hypothesis(MissingModel.PROTOCOL_BY_CONVENTION,
            "the same thing is said to several objects, one statement each",
            "listeners that register and are told at once, or a composite that passes it on"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    Names names = new Names(new Imports(unit));
    for (BlockStmt block : unit.findAll(BlockStmt.class)) {
      List<Statement> statements = block.getStatements();
      int at = 0;
      while (at < statements.size()) {
        int run = run(statements, at);
        if (run >= TOLD) {
          MethodCallExpr first = told(statements.get(at)).orElseThrow();
          sites.add(new Site(file.name(), line(first), enclosing.of(first), first.getNameAsString(), run,
              first.getScope().flatMap(names::chainOf)));
        }
        at += Math.max(run, 1);
      }
    }
  }

  /** Only objects of the project: a row of buttons given their listeners is how a library's widgets are set up. */
  @Override
  protected List<Finding> conclude(List<Site> sites, Project project) {
    return sites.stream().filter(site -> site.first().flatMap(project::typeOf).filter(project::declares).isPresent())
        .map(site -> site.found(id())).toList();
  }

  /** How many statements from there on make the same call as the first, each on another object. */
  private int run(List<Statement> statements, int from) {
    Optional<MethodCallExpr> first = told(statements.get(from));
    List<String> receivers = new ArrayList<>();
    int at = from;
    while (at < statements.size() && first.isPresent() && told(statements.get(at)).filter(next ->
        next.getNameAsString().equals(first.get().getNameAsString())
            && next.getArguments().size() == first.get().getArguments().size()
            && !receivers.contains(on(next))).isPresent()) {
      receivers.add(on(told(statements.get(at)).orElseThrow()));
      at++;
    }
    return at - from;
  }

  /** The call a statement is, when it is nothing but a call on a variable or a field. */
  private Optional<MethodCallExpr> told(Statement statement) {
    return statement.toExpressionStmt().flatMap(told -> told.getExpression().toMethodCallExpr())
        .filter(call -> call.getScope().filter(scope -> scope.isNameExpr() || scope.isFieldAccessExpr()).isPresent());
  }

  private String on(MethodCallExpr call) {
    return call.getScope().map(Object::toString).orElse("");
  }
}
