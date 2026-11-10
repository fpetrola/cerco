package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import java.util.ArrayList;
import java.util.List;

/** Global mutable state: a static field that is not final. */
public final class MutableStaticField extends FileRule {

  @Override
  public String id() {
    return "no-mutable-static";
  }

  @Override
  public String principle() {
    return "encapsulation: state belongs to an object, not to the program";
  }

  @Override
  public String description() {
    return "a static field that is not final is global mutable state: every class can change it and none owns it";
  }

  @Override
  public String remedy() {
    return "make it an instance field of the object that owns that state and pass the object to whoever needs it; "
        + "a true constant becomes static final";
  }

  private final Hypothesis shared = new Hypothesis(MissingModel.KNOWLEDGE_WITHOUT_ONE_OWNER,
      "it is state the whole program shares",
      "an object that owns it, handed to whoever needs it");
  private final Hypothesis changed = new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
      "callers change it to get something done",
      "a method of its owner");

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a singleton holding the same state", shared, changed);
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> out = new ArrayList<>();
    for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
      if (field.isStatic() && !field.isFinal() && !implicitlyFinal(field)) {
        String owner = enclosing.typeOf(field);
        for (VariableDeclarator variable : field.getVariables()) {
          String name = owner + "." + variable.getNameAsString();
          out.add(finding(file, variable, name, "static mutable field " + name + " of type "
              + variable.getTypeAsString()).read(field.isPrivate() ? shared.because("only its own class can change "
              + "it: one state every instance and every caller of the class shares") : changed.because("it is open "
              + "to every class: whoever sets it does a job of its owner")));
        }
      }
    }
    return out;
  }

  private boolean implicitlyFinal(FieldDeclaration field) {
    boolean inInterface = field.findAncestor(ClassOrInterfaceDeclaration.class)
        .map(ClassOrInterfaceDeclaration::isInterface).orElse(false);
    return inInterface || field.findAncestor(AnnotationDeclaration.class).isPresent();
  }
}
