package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.project.TypeName;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A static method callers reach from outside: a procedure, unless it is the factory of its own type. */
public final class NonPrivateStaticMethod extends SiteRule<NonPrivateStaticMethod.Procedure> {
  private final List<String> factoryPrefixes;

  public NonPrivateStaticMethod(List<String> factoryPrefixes) {
    this.factoryPrefixes = factoryPrefixes;
  }

  @Override
  public String id() {
    return "no-static-method";
  }

  @Override
  public String principle() {
    return "objects over procedures: behaviour lives with the state it works on";
  }

  @Override
  public String description() {
    return "a non-private static method is a procedure: it has no object, so its inputs travel as parameters and "
        + "its callers cannot substitute it; factories of the enclosing type and main are the exceptions";
  }

  @Override
  public String remedy() {
    return "make it an instance method of the type whose data it uses, or turn the parameters it keeps receiving "
        + "into an object with this as its method; a factory keeps a factory name (" + String.join(", ", factoryPrefixes)
        + ") or returns its own type";
  }

  /** A static method found, and what it takes: each parameter by name, with its type when it is a class. */
  public record Procedure(Finding finding, List<Taken> takes) {
  }

  /** One parameter: as written, and the class it is declared as when it is one. */
  public record Taken(String written, Optional<TypeName> type) {
  }

  private final Hypothesis owned = new Hypothesis(MissingModel.BEHAVIOUR_NOT_ON_OWNER,
      "a parameter is an object of the project",
      "an instance method of that object");
  private final Hypothesis computed = new Hypothesis(MissingModel.VALUE_WITHOUT_A_TYPE,
      "it computes on primitives or strings that mean something",
      "a value object that owns the computation");

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a singleton, or a stateless instance doing the same", owned, computed);
  }

  @Override
  protected List<Procedure> sites(ParsedFile file, CompilationUnit unit) {
    Imports imports = new Imports(unit);
    return unit.findAll(MethodDeclaration.class).stream()
        .filter(method -> method.isStatic() && !method.isPrivate() && !allowed(method))
        .map(method -> new Procedure(finding(file, method, enclosing.signatureOf(method),
            "non-private static method " + enclosing.signatureOf(method)),
            method.getParameters().stream().map(taken -> new Taken(taken.getType() + " " + taken.getNameAsString(),
                taken.getType().toClassOrInterfaceType().map(imports::name))).toList())).toList();
  }

  @Override
  protected Finding found(Procedure procedure) {
    return procedure.finding();
  }

  @Override
  protected Function<Procedure, Optional<Reading>> reader(List<Procedure> procedures, Project project) {
    return procedure -> read(procedure, project);
  }

  /** It belongs to the first object of the project it is handed; or, taking only plain values, to a type for them. */
  private Optional<Reading> read(Procedure procedure, Project project) {
    Optional<Reading> ours = procedure.takes().stream().filter(taken -> taken.type().filter(project::declares)
        .isPresent()).findFirst().map(taken -> owned.because("it takes `" + taken.written()
        + "`, an object of this project"));
    boolean plain = !procedure.takes().isEmpty() && procedure.takes().stream().allMatch(taken -> taken.type()
        .map(type -> type.simple().equals("String")).orElse(true));
    return ours.or(() -> Optional.of(computed.because("it takes only plain values: " + procedure.takes().stream()
        .map(Taken::written).collect(Collectors.joining(", ")))).filter(reading -> plain));
  }

  private boolean allowed(MethodDeclaration method) {
    String name = method.getNameAsString();
    if (name.equals("main")) {
      return true;
    }
    boolean factoryName = factoryPrefixes.stream().anyMatch(prefix -> name.equals(prefix)
        || (name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length()))));
    String returned = method.getType().asString().replaceAll("<.*", "");
    boolean returnsOwnType = method.findAncestor(TypeDeclaration.class)
        .map(type -> type.getNameAsString().equals(returned)).orElse(false);
    return factoryName || returnsOwnType;
  }
}
