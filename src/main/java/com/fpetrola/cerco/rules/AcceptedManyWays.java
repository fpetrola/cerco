package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.ArrayList;
import java.util.List;

/**
 * A type with more than one accept: besides accept(visitor), acceptViews, acceptRows, acceptMark, each taking another
 * visitor. Each extra one hands out a piece of the structure, and whoever calls it decides what to walk: the element
 * no longer keeps its structure to itself.
 */
public final class AcceptedManyWays extends FoundInPlaceRule {

  @Override
  public String id() {
    return "one-accept";
  }

  @Override
  public String principle() {
    return "only the element knows its structure: it has one accept, visits itself and has the visitor accepted by "
        + "its parts";
  }

  @Override
  public String description() {
    return "a type with several accept methods taking visitors: each extra one opens a part of its structure to "
        + "whoever wants to walk it";
  }

  @Override
  public String remedy() {
    return "keep one accept(visitor) that visits the element and then has its parts accept the visitor; a visitor "
        + "that does not care for a part does nothing when it visits it";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a getter of the parts, or an iterator over them: the structure, handed out another way",
        new Hypothesis(MissingModel.STRUCTURE_WALKED_FROM_OUTSIDE,
            "callers pick which accept to call, so they know how the element is built",
            "a single accept that walks the whole element, and visitors that ignore what they do not need"));
  }

  @Override
  protected List<Finding> sites(ParsedFile file, CompilationUnit unit) {
    List<Finding> found = new ArrayList<>();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class)) {
      List<MethodDeclaration> accepts = type.getMethods().stream().filter(this::accepts).toList();
      if (accepts.size() > 1) {
        List<String> names = accepts.stream().map(MethodDeclaration::getNameAsString).toList();
        found.add(finding(file, accepts.get(1), type.getNameAsString(), type.getNameAsString() + " has "
            + accepts.size() + " accepts " + names + ": its structure is walked from outside"));
      }
    }
    return found;
  }

  /** A method named accept-something that takes a visitor. */
  private boolean accepts(MethodDeclaration method) {
    return method.getNameAsString().startsWith("accept") && method.getParameters().stream()
        .anyMatch(parameter -> parameter.getType().asString().replaceAll("<.*", "").endsWith("Visitor"));
  }
}
