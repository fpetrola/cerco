package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Chain;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Names;
import com.fpetrola.cerco.project.Project;
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

/**
 * What another object holds, walked from three or more members outside it: a for-each over `x.items()`, or a stream
 * or forEach on it, where items() is a method of the project with no arguments. The structure hands its insides out
 * and every client writes the walk again. Generalises refiltered-by-type, which saw one shape of it in one file.
 */
public final class WalkedFromOutside extends IndexingRule<Set<String>, WalkedFromOutside.Site> {
  private static final int MEMBERS = 3;
  private static final Set<String> WALKS = Set.of("stream", "forEach", "iterator", "removeIf");
  private static final Set<String> MAP_VIEWS = Set.of("values", "keySet", "entrySet");

  /**
   * One walk: where, which method handed the collection out, which top-level type is walking, whether it goes
   * through a stream, which looks for or counts things, rather than over every element, and how the object that
   * handed it out is reached.
   */
  public record Site(String file, int line, String member, String handedOut, String walker, boolean streamed,
      Optional<Chain> owner) {
  }

  private final Hypothesis queried = new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
      "the walks look for, count or filter the same things",
      "a named query on the owner");
  private final Hypothesis iterated = new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
      "the walks do something to every element",
      "an internal iterator on the owner (each(action)), or a composite that does it itself");
  private final Hypothesis sorted = new Hypothesis(MissingModel.DISPATCH_FROM_OUTSIDE,
      "the walks treat each kind of element differently",
      "a visitor the elements accept, walked by the owner");


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
        + "walked from outside", queried, iterated, sorted);
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Index<Set<String>, Site> gathered) {
    Names names = new Names(new Imports(unit));
    for (TypeDeclaration<?> top : unit.getTypes()) {
      String walker = top.getNameAsString();
      top.findAll(MethodDeclaration.class, method -> method.getParameters().isEmpty()).forEach(method ->
          gathered.declared().computeIfAbsent(method.getNameAsString(), k -> new HashSet<>()).add(walker));
      top.findAll(ForEachStmt.class).stream().map(ForEachStmt::getIterable).map(this::handedOut)
          .flatMap(Optional::stream).forEach(call -> gathered.sites().add(new Site(file.name(), line(call),
              enclosing.of(call), call.getNameAsString(), walker, false,
              call.getScope().flatMap(names::chainOf))));
      top.findAll(MethodCallExpr.class, call -> WALKS.contains(call.getNameAsString())).forEach(walk ->
          walk.getScope().flatMap(this::handedOut).ifPresent(call -> gathered.sites().add(new Site(file.name(),
              line(call), enclosing.of(call), call.getNameAsString(), walker,
              walk.getNameAsString().equals("stream"), call.getScope().flatMap(names::chainOf)))));
    }
  }

  /**
   * Walks are counted per owner when the project can say whose the collection is, so that `all()` of one type is
   * not added to `all()` of another; where it cannot, by the name alone, among the types that declare it.
   */
  @Override
  protected List<Finding> conclude(Index<Set<String>, Site> gathered, Project project) {
    Map<String, List<Site>> byMethod = new LinkedHashMap<>();
    gathered.sites().stream()
        .filter(site -> gathered.declared().containsKey(site.handedOut()))
        .filter(site -> !gathered.declared().get(site.handedOut()).contains(site.walker()))
        .filter(site -> owner(site, project).map(project::declares).orElse(true))
        .forEach(site -> byMethod.computeIfAbsent(owner(site, project).map(type -> type + ".").orElse("")
            + site.handedOut(), k -> new ArrayList<>()).add(site));
    List<Finding> findings = new ArrayList<>();
    byMethod.forEach((method, sites) -> {
      Set<String> members = sites.stream().map(Site::member).collect(Collectors.toSet());
      if (members.size() >= MEMBERS) {
        String owners = sites.stream().findFirst().flatMap(site -> owner(site, project))
            .orElseGet(() -> String.join(", ", gathered.declared().get(sites.get(0).handedOut())));
        String handed = sites.get(0).handedOut();
        sites.forEach(site -> findings.add(new Finding(id(), site.file(), site.line(),
            site.file() + "|" + site.member() + "|" + handed + "()", "what " + handed + "() of " + owners
                + " hands out is walked in " + site.member() + "; " + members.size() + " members walk it")
            .read(site.streamed() ? queried.because("a stream over what `" + handed + "()` hands out, which "
                + members.size() + " members walk") : iterated.because("a loop over what `" + handed
                + "()` hands out, which " + members.size() + " members walk"))));
      }
    });
    return findings;
  }

  private Optional<String> owner(Site site, Project project) {
    return site.owner().flatMap(project::typeOf);
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
