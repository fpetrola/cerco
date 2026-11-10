package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What another object holds, walked from three or more members outside it: a for-each over `x.items()`, or a stream
 * or forEach on it, where items() is a method of the project with no arguments. The structure hands its insides out
 * and every client writes the walk again. Generalises refiltered-by-type, which saw one shape of it in one file.
 */
public final class WalkedFromOutside extends ProjectRule<WalkedFromOutside.Gathered> {
  private static final int MEMBERS = 3;
  private static final Set<String> WALKS = Set.of("stream", "forEach", "iterator", "removeIf");
  private static final Set<String> MAP_VIEWS = Set.of("values", "keySet", "entrySet");

  /** One walk: where, which method handed the collection out, and which top-level type is walking. */
  public record Site(String file, int line, String member, String handedOut, String walker) {
  }

  /** Which top-level types declare each no-argument method, and the walks found. */
  public record Gathered(Map<String, Set<String>> declaredIn, List<Site> sites) {
  }

  @Override
  public String id() {
    return "walked-from-outside";
  }

  @Override
  public String principle() {
    return "a structure knows how to walk itself; its clients say what to do, not how to get there";
  }

  @Override
  public String description() {
    return "what one method of the project hands out is walked (for-each, stream, forEach) in " + MEMBERS + " or more "
        + "members of other types: each one writes the same walk over the same insides";
  }

  @Override
  public String remedy() {
    return "give the owner the operation the walks perform (a query with a name, an internal iterator, a method that "
        + "takes what to do), or a visitor when the walks do different things to different elements";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("handing out an unmodifiable copy or a Stream instead of the list: the insides are still "
        + "walked from outside",
        new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
            "the walks look for, count or filter the same things",
            "a named query on the owner"),
        new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
            "the walks do something to every element",
            "an internal iterator on the owner (each(action)), or a composite that does it itself"),
        new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
            "the walks treat each kind of element differently",
            "a visitor the elements accept, walked by the owner"));
  }

  @Override
  protected Gathered start() {
    return new Gathered(new HashMap<>(), new ArrayList<>());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Gathered gathered) {
    for (TypeDeclaration<?> top : unit.getTypes()) {
      String walker = top.getNameAsString();
      top.findAll(MethodDeclaration.class, method -> method.getParameters().isEmpty()).forEach(method ->
          gathered.declaredIn().computeIfAbsent(method.getNameAsString(), k -> new HashSet<>()).add(walker));
      Stream<Expression> iterated = top.findAll(ForEachStmt.class).stream().map(ForEachStmt::getIterable);
      Stream<Expression> streamed = top.findAll(MethodCallExpr.class,
              call -> WALKS.contains(call.getNameAsString())).stream()
          .map(MethodCallExpr::getScope).flatMap(Optional::stream);
      Stream.concat(iterated, streamed).map(this::handedOut).flatMap(Optional::stream)
          .forEach(call -> gathered.sites().add(new Site(file.name(), line(call), enclosing.of(call),
              call.getNameAsString(), walker)));
    }
  }

  @Override
  protected List<Finding> conclude(Gathered gathered) {
    Map<String, List<Site>> byMethod = new LinkedHashMap<>();
    gathered.sites().stream()
        .filter(site -> gathered.declaredIn().containsKey(site.handedOut()))
        .filter(site -> !gathered.declaredIn().get(site.handedOut()).contains(site.walker()))
        .forEach(site -> byMethod.computeIfAbsent(site.handedOut(), k -> new ArrayList<>()).add(site));
    List<Finding> findings = new ArrayList<>();
    byMethod.forEach((method, sites) -> {
      Set<String> members = sites.stream().map(Site::member).collect(Collectors.toSet());
      if (members.size() >= MEMBERS) {
        String owners = String.join(", ", gathered.declaredIn().get(method));
        sites.forEach(site -> findings.add(new Finding(id(), site.file(), site.line(),
            site.file() + "|" + site.member() + "|" + method + "()", "what " + method + "() of " + owners
                + " hands out is walked in " + site.member() + "; " + members.size() + " members walk it")));
      }
    });
    return findings;
  }

  /** `x.items()` on another object, with no arguments: the call that handed the collection out. */
  private Optional<MethodCallExpr> handedOut(Expression walked) {
    return Optional.of(walked).filter(Expression::isMethodCallExpr).map(Expression::asMethodCallExpr)
        .map(this::pastTheMapView)
        .filter(call -> call.getArguments().isEmpty() && !MAP_VIEWS.contains(call.getNameAsString()))
        .filter(call -> call.getScope().filter(scope -> !scope.isThisExpr() && !scope.isSuperExpr()).isPresent());
  }

  /** `x.table().values()` walks what table() handed out. */
  private MethodCallExpr pastTheMapView(MethodCallExpr call) {
    return Optional.of(call).filter(view -> MAP_VIEWS.contains(view.getNameAsString()))
        .flatMap(MethodCallExpr::getScope).filter(Expression::isMethodCallExpr).map(Expression::asMethodCallExpr)
        .orElse(call);
  }

}
