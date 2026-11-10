package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A call made through a field of another object, `a.b.m()`, when that field is reached so from two or more members:
 * the object `a` hides nothing, its callers talk to its parts. The version across places of the Law of Demeter, which
 * per member the usual checkers flag at every step. Candidate born from the plan of model smells: in oozx, 8 sites in
 * emulator and 76 in machine/core and machine/app (`device.peripheral` 11, `speccy.machine` 9, `speccy.loop` 7).
 */
public final class ReachedThrough extends ProjectRule<ReachedThrough.Gathered> {
  private static final int MEMBERS = 2;

  /**
   * One call through a field: where, the path `a.b`, the field, whether the caller's own type declares it, and
   * whether the member uses `a` for nothing but reaching `b`.
   */
  public record Site(String file, int line, String member, String path, String field, boolean ownField,
      String topLevelType, boolean onlyThePart) {
  }

  private final Hypothesis delegated = new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
      "the callers want something done that `a` could do",
      "a method of `a` that delegates to `b`");
  private final Hypothesis handed = new Hypothesis(MissingModel.KNOWLEDGE_WITHOUT_ONE_OWNER,
      "the callers only ever use `b`, never `a` itself",
      "hand them `b`: their collaborator is the part, not the whole");
  private final Hypothesis bag = new Hypothesis(MissingModel.CONCEPT_WITHOUT_A_CLASS,
      "`a` is a bag of parts with no behaviour of its own",
      "decide what `a` is for; its parts go to whoever owns them");

  /** The project's names, and the calls through fields. */
  public record Gathered(Vocabulary vocabulary, List<Site> sites) {
  }

  @Override
  public String id() {
    return "reached-through";
  }

  @Override
  public String principle() {
    return "information hiding: talk to an object, not to its parts";
  }

  @Override
  public String description() {
    return "calls made through a field of another object (`a.b.m()`) from " + MEMBERS + " or more members: `a` "
        + "exposes `b`, and every caller depends on how `a` is made";
  }

  @Override
  public String remedy() {
    return "if the callers only want `b`, give them `b` instead of `a`; if they want something done, `a` offers the "
        + "operation and delegates to its part";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a getter in place of the field (`a.getB().m()`), or a local holding `a.b`: the same path",
        delegated, handed, bag);
  }

  @Override
  protected Gathered start() {
    return new Gathered(new Vocabulary(), new ArrayList<>());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Gathered gathered) {
    gathered.vocabulary().learn(unit);
    for (TypeDeclaration<?> top : unit.getTypes()) {
      for (MethodCallExpr call : top.findAll(MethodCallExpr.class)) {
        call.getScope().filter(Expression::isFieldAccessExpr).map(Expression::asFieldAccessExpr)
            .filter(this::throughAnotherObject)
            .ifPresent(access -> gathered.sites().add(new Site(file.name(), line(call), enclosing.of(call),
                access.toString(), access.getNameAsString(), ownFields(access).contains(access.getNameAsString()),
                top.getNameAsString(), onlyThePart(access))));
      }
    }
  }

  @Override
  protected List<Finding> conclude(Gathered gathered, Project project) {
    Map<String, List<Site>> byPath = new LinkedHashMap<>();
    gathered.sites().stream().filter(site -> !site.ownField())
        .filter(site -> gathered.vocabulary().declaresField(site.field()))
        .filter(site -> !gathered.vocabulary().onlyIn(site.field(), site.topLevelType()))
        .forEach(site -> byPath.computeIfAbsent(site.path(), k -> new ArrayList<>()).add(site));
    List<Finding> findings = new ArrayList<>();
    byPath.forEach((path, sites) -> {
      Set<String> members = sites.stream().map(Site::member).collect(Collectors.toSet());
      if (members.size() >= MEMBERS) {
        sites.forEach(site -> findings.add(new Finding(id(), site.file(), site.line(),
            site.file() + "|" + site.member() + "|" + path, "`" + path + "` reached in " + site.member() + "; "
                + members.size() + " members reach through `" + path + "`").read(read(site, members.size()))));
      }
    });
    return findings;
  }

  private Reading read(Site site, int members) {
    String whole = site.path().substring(0, site.path().lastIndexOf('.'));
    return site.onlyThePart() ? handed.because("here `" + whole + "` is used only to reach `" + site.field()
        + "`, as in " + members + " members") : delegated.because("here `" + whole + "` is used for more than `"
        + site.field() + "`: what is done through `" + site.path() + "` in " + members + " members is its to offer");
  }

  /** Whether every use of `a` in the member the access sits in is `a.b`. */
  private boolean onlyThePart(FieldAccessExpr access) {
    String whole = access.getScope().toString();
    return access.findAncestor(BodyDeclaration.class).map(member -> {
      Node scope = member;
      return scope.findAll(NameExpr.class, name -> name.getNameAsString().equals(whole)).stream()
          .allMatch(name -> enclosing.parentOf(name, FieldAccessExpr.class)
              .filter(through -> through.getNameAsString().equals(access.getNameAsString())).isPresent());
    }).orElse(false);
  }

  /** `a.b` with `a` a variable (not `this`, not a type, not a call): the part of another object. */
  private boolean throughAnotherObject(FieldAccessExpr access) {
    boolean variable = access.getScope().isNameExpr()
        && Character.isLowerCase(access.getScope().asNameExpr().getNameAsString().charAt(0));
    return variable && Character.isLowerCase(access.getNameAsString().charAt(0));
  }

  /** The fields of the type the access is written in: `other.b` there is the same class's own part. */
  private Set<String> ownFields(FieldAccessExpr access) {
    return access.findAncestor(ClassOrInterfaceDeclaration.class).map(type -> type.getFields().stream()
        .flatMap(field -> field.getVariables().stream()).map(VariableDeclarator::getNameAsString)
        .collect(Collectors.toSet())).orElse(Set.of());
  }

}
