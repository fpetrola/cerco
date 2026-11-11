package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.List;
import java.util.Optional;

/**
 * A map from a key to a piece of code: a switch written as data. Each case is a lambda with no name and no type,
 * looked up by a value. Candidate from the plan of model smells (the covert dispatch that no-switch names as not
 * a fix).
 */
public final class DispatchByKey extends FileRule {
  private static final String CODE = "Runnable|Callable|Supplier|Consumer|BiConsumer|Function|BiFunction|Predicate"
      + "|BiPredicate|UnaryOperator|BinaryOperator|\\w*Action|\\w*Handler";

  @Override
  public String id() {
    return "dispatch-by-key";
  }

  @Override
  public String principle() {
    return "polymorphism: what is done for each case is a type that does it, not an entry in a table";
  }

  @Override
  public String description() {
    return "a map from a key to a function is a switch kept as data: each case is a nameless piece of code picked "
        + "by a value, and what a case can do is whatever the function type allows";
  }

  @Override
  public String remedy() {
    return "when the keys are an enum of the project, the behaviour goes on its constants; when they are texts "
        + "that name requests, a command type whose instances register under their names";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("the same table built by a method instead of kept in a field, or an EnumMap in its place",
        new Hypothesis(MissingModel.VARIATION_AS_DATA,
            "each key stands for a case and its value for what the case does",
            "behaviour on the enum the keys are, or a command type the cases are instances of"));
  }

  @Override
  protected List<Finding> inspect(ParsedFile file, CompilationUnit unit) {
    return unit.findAll(VariableDeclarator.class).stream().flatMap(variable -> dispatch(variable.getType())
        .map(shape -> finding(file, variable, enclosing.of(variable) + "|" + variable.getNameAsString(), "`"
            + variable.getNameAsString() + "` in " + enclosing.of(variable) + " maps a key to code: " + shape))
        .stream()).toList();
  }

  /** The type as written, when it is a map whose values are functions. */
  private Optional<String> dispatch(Type type) {
    return type.toClassOrInterfaceType().filter(map -> map.getNameAsString().endsWith("Map"))
        .filter(map -> map.getTypeArguments().filter(arguments -> arguments.size() == 2)
            .flatMap(arguments -> arguments.get(1).toClassOrInterfaceType())
            .map(ClassOrInterfaceType::getNameAsString).filter(value -> value.matches(CODE)).isPresent())
        .map(ClassOrInterfaceType::asString);
  }
}
