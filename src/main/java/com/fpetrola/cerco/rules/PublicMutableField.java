package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import java.util.ArrayList;
import java.util.List;

/** A public instance field that is not final: state anybody can rewrite from outside. */
public final class PublicMutableField extends FileRule {

  @Override
  public String id() {
    return "no-public-mutable-field";
  }

  @Override
  public String principle() {
    return "encapsulation: an object changes its own state";
  }

  @Override
  public String description() {
    return "a public field that is not final lets any class rewrite the object's state behind its back";
  }

  @Override
  public String remedy() {
    return "make the field private and give the object a method that does the thing callers were doing with the field; "
        + "if the object is only data, make it a record";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a trivial getter and setter: the state is still changed from outside",
        new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
            "callers change the field to get something done",
            "a method that does that thing"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    List<Finding> out = new ArrayList<>();
    for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
      if (field.isPublic() && !field.isStatic() && !field.isFinal()) {
        String owner = enclosing.typeOf(field);
        for (VariableDeclarator variable : field.getVariables()) {
          String name = owner + "." + variable.getNameAsString();
          out.add(finding(file, variable, name, "public mutable field " + name));
        }
      }
    }
    return out;
  }
}
