package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A type with one method for a thing and another of the same name for several of them: single and multiple are
 * handled by separate code, and callers have to know which they hold. Candidate from the plan of model smells
 * (Kerievsky's Replace One/Many Distinctions with Composite).
 */
public final class OneAndMany extends FileRule {

  @Override
  public String id() {
    return "one-and-many";
  }

  @Override
  public String principle() {
    return "one and many are the same to whoever uses them: a group is a thing of the same kind";
  }

  @Override
  public String description() {
    return "a type has a method taking one thing and another of the same name taking a collection or an array of "
        + "them: one and many go through separate code, and every caller tells them apart";
  }

  @Override
  public String remedy() {
    return "a composite: a type for the group that is also one of the things, so that one method serves both and "
        + "the group does the walking";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("the method for many calling the one for each element, still two entries and a loop outside "
        + "the things",
        new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
            "one thing and a group of them are handled by separate methods",
            "a composite: the group is one of the things and walks itself"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> findings = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      for (MethodDeclaration many : type.getMethods()) {
        only(many).flatMap(this::elementOf)
            .filter(element -> type.getMethodsByName(many.getNameAsString()).stream()
                .anyMatch(one -> only(one).filter(parameter -> !parameter.isVarArgs()
                    && parameter.getTypeAsString().equals(element))
                    .isPresent()))
            .ifPresent(element -> findings.add(finding(file, many, enclosing.signatureOf(many), enclosing.nameOf(type)
                + " has " + many.getNameAsString() + " for one `" + element + "` and for many: "
                + many.getDeclarationAsString(false, false, true))));
      }
    }
    return findings;
  }

  private Optional<Parameter> only(MethodDeclaration method) {
    return Optional.of(method).filter(one -> one.getParameters().size() == 1).map(one -> one.getParameter(0));
  }

  /** What a collection, an array or a varargs is of: `X` in `List<X>`, `X[]` and `X...`. */
  private Optional<String> elementOf(Parameter parameter) {
    Type many = parameter.getType();
    if (parameter.isVarArgs()) {
      return Optional.of(many.asString());
    }
    Optional<String> listed = many.toClassOrInterfaceType()
        .filter(type -> type.getNameAsString().matches(".*(List|Set|Collection|Iterable)"))
        .flatMap(type -> type.getTypeArguments()).filter(arguments -> arguments.size() == 1)
        .map(arguments -> arguments.get(0).asString());
    return listed.or(() -> many.toArrayType().map(array -> array.getComponentType().asString()));
  }
}
