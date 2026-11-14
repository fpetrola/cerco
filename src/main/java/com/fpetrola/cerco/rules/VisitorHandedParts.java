package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A visitor handed the object in parts: a method that takes a visitor calls it with `this` and, beside it, values of
 * its own (fields, derived values, method references, lambdas). That is a destructuring, not a visit: every new datum
 * changes the visitor's signature and every visitor, the visited type's interface stays empty, and the application
 * ends up with getters too, two ways to the same datum.
 */
public final class VisitorHandedParts extends SiteRule<Finding> {

  @Override
  public String id() {
    return "visitor-handed-parts";
  }

  @Override
  public String principle() {
    return "a visitor resolves the type and receives only the instance; the state is asked of the object";
  }

  @Override
  public String description() {
    return "a call to a visitor that passes `this` together with other arguments: the visited object is taken apart "
        + "for the visitor instead of being visited";
  }

  @Override
  public String remedy() {
    return "pass only `this` (visitX(X)) and let the visitor ask the object through its interface; walk what is "
        + "inside with nested visits (part.accept(partVisitor)) that hand a value, never the object and its parts";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("getters beside the visitor, or one visitor method per datum: the same parts, handed out "
        + "another way", new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
            "the visited type's interface declares nothing and its parts travel as arguments",
            "the visited interface says what can be asked of it, and the visitor receives only the instance"));
  }

  @Override
  protected List<Finding> sites(ParsedFile file, CompilationUnit unit) {
    List<Finding> found = new ArrayList<>();
    for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
      for (Parameter visitor : method.getParameters()) {
        if (visitor.getType().asString().replaceAll("<.*", "").endsWith("Visitor")) {
          method.findAll(MethodCallExpr.class, call -> handsParts(call, visitor.getNameAsString()))
              .forEach(call -> found.add(finding(file, call, method.getNameAsString() + "|" + clipped(call.toString()),
                  "`" + clipped(call.toString()) + "` hands the visitor this and " + (call.getArguments().size() - 1)
                      + " part(s) of it")));
        }
      }
    }
    return found;
  }

  @Override
  protected Finding found(Finding site) {
    return site;
  }

  @Override
  protected Function<Finding, Optional<Reading>> reader(List<Finding> sites, Project project) {
    return site -> Optional.empty();
  }

  private boolean handsParts(MethodCallExpr call, String visitor) {
    boolean onTheVisitor = call.getScope().filter(scope -> scope.isNameExpr()
        && scope.asNameExpr().getNameAsString().equals(visitor)).isPresent();
    return onTheVisitor && call.getArguments().size() > 1
        && call.getArguments().stream().anyMatch(argument -> argument.isThisExpr());
  }

}
