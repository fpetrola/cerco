package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * A parameter every call in the project passes the same literal for: a variation the signature offers and the model
 * does not have. Candidate born from the autopsies of oozx (BlipBuffer.readSamples, always called with true).
 */
public final class ConstantArgument extends ProjectRule<ConstantArgument.Seen> {
  private static final int CALLS = 2;
  private static final String HANDED_ON = "*";

  /** A method with a body, by its name and how many arguments it takes, and what it calls each of them. */
  public record Site(String file, int line, String member, String called, List<String> parameters) implements Place {
  }

  /**
   * The methods declared, what each call passes (a literal as written, `*` for the caller's own parameter handed on
   * by a method of the same name, or nothing), and the names handed over as references.
   */
  public record Seen(List<Site> declared, Map<String, List<List<String>>> calls, Set<String> referred) {

    void call(String called, List<String> passed) {
      calls.computeIfAbsent(called, key -> new ArrayList<>()).add(passed);
    }

    List<List<String>> callsOf(Site site) {
      return calls.getOrDefault(site.called(), List.of());
    }

    /** The methods nobody hands over uncalled, where what they are passed cannot be seen. */
    List<Site> alwaysCalled() {
      return declared.stream().filter(site -> !referred.contains(site.called().replaceAll("/.*", ""))).toList();
    }
  }

  @Override
  public String id() {
    return "constant-argument";
  }

  @Override
  public String principle() {
    return "a parameter is a variation somebody uses; one nobody varies is a branch nobody takes";
  }

  @Override
  public String description() {
    return "every call of a method in the project passes the same literal for one of its parameters: the method "
        + "keeps a variation, and usually a branch, that the model does not have";
  }

  @Override
  public String remedy() {
    return "delete the parameter and whatever the method did for the other values; not two methods, since nobody "
        + "calls the second one";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a constant or a default overload that passes the same literal on",
        new Hypothesis(MissingModel.VARIATION_AS_DATA,
            "the variation exists only in the signature: every caller passes the same value",
            "delete the parameter and the branch nobody takes"));
  }

  @Override
  protected Seen start() {
    return new Seen(new ArrayList<>(), new HashMap<>(), new HashSet<>());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Seen seen) {
    unit.findAll(MethodDeclaration.class, method -> method.getBody().isPresent() && !method.getParameters().isEmpty()
            && method.getAnnotationByName("Override").isEmpty()
            && method.getParameters().stream().noneMatch(Parameter::isVarArgs))
        .forEach(method -> seen.declared().add(new Site(file.name(), line(method), enclosing.signatureOf(method),
            method.getNameAsString() + "/" + method.getParameters().size(),
            method.getParameters().stream().map(Parameter::getNameAsString).toList())));
    unit.findAll(MethodCallExpr.class).forEach(call -> seen.call(
        call.getNameAsString() + "/" + call.getArguments().size(), passed(call)));
    unit.findAll(MethodReferenceExpr.class).forEach(reference -> seen.referred().add(reference.getIdentifier()));
  }

  @Override
  protected List<Finding> conclude(Seen seen, Project project) {
    List<Finding> findings = new ArrayList<>();
    seen.alwaysCalled().forEach(site -> {
      List<List<String>> calls = seen.callsOf(site);
      IntStream.range(0, site.parameters().size()).forEach(at -> {
        List<String> passed = calls.stream().map(call -> call.get(at)).filter(one -> !one.equals(HANDED_ON)).toList();
        if (passed.size() >= CALLS && !passed.get(0).isEmpty() && Set.copyOf(passed).size() == 1) {
          findings.add(new Finding(id(), site.file(), site.line(),
              site.file() + "|" + site.member() + "|" + site.parameters().get(at),
              "all " + passed.size() + " calls of " + site.member() + " pass `" + passed.get(0) + "` as `"
                  + site.parameters().get(at) + "`"));
        }
      });
    });
    return findings;
  }

  /** What a call passes at each place; a method handing its own parameter on to its namesake passes nothing new. */
  private List<String> passed(MethodCallExpr call) {
    List<String> own = call.findAncestor(MethodDeclaration.class)
        .filter(caller -> caller.getNameAsString().equals(call.getNameAsString())
            && caller.getParameters().size() == call.getArguments().size())
        .map(caller -> caller.getParameters().stream().map(Parameter::getNameAsString).toList()).orElse(List.of());
    return IntStream.range(0, call.getArguments().size()).mapToObj(at -> {
      Expression given = call.getArgument(at);
      boolean handedOn = !own.isEmpty() && given.toString().equals(own.get(at));
      return handedOn ? HANDED_ON : given.isLiteralExpr() ? given.toString() : "";
    }).toList();
  }
}
