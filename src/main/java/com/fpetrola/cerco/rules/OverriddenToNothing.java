package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.fpetrola.cerco.project.Declared;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A method that overrides another with nothing, or only to refuse: the type inherits a promise it does not keep.
 * Candidate from the plan of model smells (refused bequest; Riel: a derived class must not override with a NOP).
 */
public final class OverriddenToNothing extends PlacesRule<OverriddenToNothing.Site> {

  /** One override that does nothing: where, the method by name, the type it is in, and how it says so. */
  public record Site(String file, int line, String member, String method, String type, String how)
      implements Place {
  }

  @Override
  public String id() {
    return "overridden-to-nothing";
  }

  @Override
  public String principle() {
    return "a subtype does everything its supertype promises: whoever holds one can use it as the other";
  }

  @Override
  public String description() {
    return "a method marked @Override has an empty body, or only throws: the type inherits an operation it does "
        + "not have, and every holder of the supertype may call what does nothing or fails";
  }

  @Override
  public String remedy() {
    return "split the supertype: what every subtype does stays, and the operation only some have goes to a type "
        + "of their own; or hold the other object instead of extending it";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a default method in the interface that does nothing, so that nobody has to write the "
        + "empty one",
        new Hypothesis(MissingModel.HIERARCHY_WITHOUT_IS_A,
            "a subtype answers an inherited operation with nothing, or by refusing",
            "a narrower supertype for what all of them do, and a type of their own for the rest"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    unit.findAll(MethodDeclaration.class, this::refuses).forEach(method -> sites.add(new Site(file.name(),
        line(method), enclosing.signatureOf(method), method.getNameAsString(),
        method.findAncestor(TypeDeclaration.class).map(TypeDeclaration::getNameAsString).orElse(""),
        method.getBody().filter(body -> body.getStatements().isEmpty()).isPresent() ? "nothing" : "a refusal")));
  }

  /** Only what a type of the project promised: an empty callback of a library's listener is that library's width. */
  @Override
  protected List<Finding> conclude(List<Site> sites, Project project) {
    return sites.stream().flatMap(site -> promisedBy(site, project).map(promiser -> new Finding(id(), site.file(),
        site.line(), site.file() + "|" + site.member(), site.member() + " overrides with " + site.how()
        + " what `" + promiser + "` promises")).stream()).toList();
  }

  /** The type of the project above this one that declares the method. */
  private Optional<String> promisedBy(Site site, Project project) {
    Set<String> seen = new TreeSet<>();
    Deque<String> pending = new ArrayDeque<>(project.declared(site.type()).map(Declared::supertypes)
        .orElse(List.of()));
    while (!pending.isEmpty()) {
      String above = pending.pop();
      Optional<Declared> declared = project.declared(above).filter(one -> seen.add(above));
      if (declared.filter(one -> one.declares(site.method()) && !above.equals(site.type())).isPresent()) {
        return Optional.of(above);
      }
      declared.ifPresent(one -> pending.addAll(one.supertypes()));
    }
    return Optional.empty();
  }

  private boolean refuses(MethodDeclaration method) {
    return method.getAnnotationByName("Override").isPresent() && method.getBody().filter(this::doesNothing)
        .isPresent();
  }

  /** Empty, or a single statement that throws. */
  private boolean doesNothing(BlockStmt body) {
    return body.getStatements().isEmpty()
        || (body.getStatements().size() == 1 && body.getStatement(0).isThrowStmt());
  }
}
