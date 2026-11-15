package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Declared;
import com.fpetrola.cerco.project.Location;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.surface.Member;
import com.fpetrola.cerco.surface.Offered;
import com.fpetrola.cerco.surface.Reach;
import com.fpetrola.cerco.surface.Seal;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What a class offers that no interface promised: a method that is not private and neither overrides nor implements
 * one an interface of its hierarchy declares, a record component no interface asks for, a field that is not private,
 * and an entrance (a public constructor, a static method) the seal does not have. With the surface sealed, the
 * design is said by interfaces; a class only keeps those promises, so an agent cannot grow API on a class.
 */
public final class UnpromisedMember extends ProjectRule<UnpromisedMember.Seen> {
  private static final Set<String> OBJECT = Set.of("equals/1", "hashCode/0", "toString/0");
  private static final Set<String> BORROWED = Set.of("Runnable", "Callable");
  private static final String FUNCTIONS = "java.util.function.";
  private final Seal seal;
  private final Reach reach;

  /** What the files showed: the findings already sure, and the operations an interface has to declare. */
  public record Seen(List<Finding> found, List<Owed> owed) {
  }

  /** An operation of a class that some interface has to declare: by name and how many it takes. */
  public record Owed(String file, int line, String type, String name, int arity) {

    /** Whether an interface the type reaches, through every supertype the project declares, declares it. */
    boolean keptIn(Project project) {
      if (OBJECT.contains(name + "/" + arity)) {
        return true;
      }
      Set<String> seen = new HashSet<>();
      Deque<String> pending = new ArrayDeque<>(List.of(type));
      while (!pending.isEmpty()) {
        Optional<Declared> declared = project.declared(pending.pop()).filter(one -> seen.add(one.name()));
        if (declared.filter(this::declaredBy).isPresent()) {
          return true;
        }
        declared.ifPresent(one -> pending.addAll(one.supertypes()));
      }
      return false;
    }

    private boolean declaredBy(Declared declared) {
      return declared.isInterface() && declared.operations().stream().anyMatch(operation ->
          operation.name().equals(name) && operation.parameters().size() == arity);
    }

    Finding finding(String rule) {
      return new Finding(rule, file, line, file + "|" + type + "." + name + "/" + arity, type + "." + name + " ("
          + arity + " parameters) is promised by no interface " + type + " implements");
    }
  }

  public UnpromisedMember(Seal seal, Reach reach) {
    this.seal = seal;
    this.reach = reach;
  }

  @Override
  public String id() {
    return "unpromised-member";
  }

  @Override
  public String principle() {
    return "a class keeps promises, it does not make them: what others may ask of it is declared by a sealed "
        + "interface";
  }

  @Override
  public String description() {
    return "a method, record component or field of a class that is not private and that no interface of its "
        + "hierarchy declares, or a public constructor or static method the seal does not have";
  }

  @Override
  public String remedy() {
    return "make it private, or move the need into the interface the class implements (through a proposal), mark "
        + "an implementation @Override; a public constructor or static method is the module's entrance: propose it";
  }

  @Override
  protected Seen start() {
    return new Seen(new ArrayList<>(), new ArrayList<>());
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, Seen seen) {
    read(file, unit, seen.found(), seen.owed());
  }

  @Override
  protected List<Finding> conclude(Seen seen, Project project) {
    return Stream.concat(seen.found().stream(), seen.owed().stream().filter(one -> !one.keptIn(project))
        .map(one -> one.finding(id()))).sorted().toList();
  }

  private void read(ParsedFile file, CompilationUnit unit, List<Finding> found, List<Owed> owed) {
    List<Member> offered = new Offered(file).members();
    String module = new Location(file.name()).module();
    for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class, this::judged)) {
      String name = type.getNameAsString();
      boolean borrows = borrows(unit, type);
      for (BodyDeclaration<?> member : type.getMembers()) {
        member.toFieldDeclaration().filter(field -> !field.isPrivate()).ifPresent(field -> found.add(
            field(file, name, field)));
        member.toConstructorDeclaration().filter(constructor -> constructor.isPublic()
            && !signed(offered, module, constructor)).ifPresent(constructor -> found.add(entrance(file, name,
            constructor)));
        member.toMethodDeclaration().filter(method -> !method.isPrivate()).ifPresent(method -> method(file, name,
            method, offered, module, found, borrows ? owed : new ArrayList<>(), owed));
      }
      type.toRecordDeclaration().ifPresent(record -> record.getParameters().stream()
          .filter(part -> record.getMethodsBySignature(part.getNameAsString()).isEmpty())
          .forEach(part -> owed.add(new Owed(file.name(), line(part), name, part.getNameAsString(), 0))));
    }
  }

  /**
   * The owed of an @Override is kept only by a type that borrows a promise from the JDK's function types: then its
   * overrides have to be promised by an interface of the project too.
   */
  private void method(ParsedFile file, String type, MethodDeclaration method, List<Member> offered, String module,
      List<Finding> found, List<Owed> overriding, List<Owed> owed) {
    if (method.isStatic()) {
      if (!method.isPublic() || !signed(offered, module, method)) {
        found.add(entrance(file, type, method));
      }
      return;
    }
    (method.isAnnotationPresent("Override") ? overriding : owed).add(new Owed(file.name(), line(method), type, method.getNameAsString(), method.getParameters().size()));
  }

  /**
   * Whether the type implements Runnable, Callable or a type of java.util.function: a promise nobody designed or
   * signed, so it keeps none. Lambdas and method references of those types are values, and are left alone.
   */
  private boolean borrows(CompilationUnit unit, TypeDeclaration<?> type) {
    if (!type.isClassOrInterfaceDeclaration()) {
      return false;
    }
    boolean wholePackage = unit.getImports().stream().anyMatch(i -> i.isAsterisk() && !i.isStatic()
        && (i.getNameAsString() + ".").equals(FUNCTIONS));
    return type.asClassOrInterfaceDeclaration().getImplementedTypes().stream().anyMatch(implemented -> {
      String written = implemented.getNameWithScope();
      String simple = implemented.getNameAsString();
      boolean imported = unit.getImports().stream().anyMatch(i -> i.getNameAsString().equals(FUNCTIONS + simple));
      return BORROWED.contains(simple) || written.startsWith(FUNCTIONS) || imported || wholePackage;
    });
  }

  /** A class, record or enum others can reach: not an interface, not local, and with nothing private around it. */
  private boolean judged(TypeDeclaration<?> type) {
    boolean promises = type.isAnnotationDeclaration() || type.isClassOrInterfaceDeclaration()
        && type.asClassOrInterfaceDeclaration().isInterface();
    boolean reachable = type.isTopLevelType() || type.isNestedType();
    return !promises && reachable && !hidden(type);
  }

  /** A type that is private, or sits in one that is. */
  private boolean hidden(TypeDeclaration<?> type) {
    return type.isPrivate() || type.findAncestor(TypeDeclaration.class).map(this::hidden).orElse(false);
  }

  /** Whether the seal has this entrance, as the surface writes it at the same line. */
  private boolean signed(List<Member> offered, String module, CallableDeclaration<?> callable) {
    List<Member> same = offered.stream().filter(member -> member.line() == line(callable)
        && member.name().equals(callable.getNameAsString()) && member.within(reach)).toList();
    return !same.isEmpty() && seal.unsigned(module, same).isEmpty();
  }

  private Finding field(ParsedFile file, String type, FieldDeclaration field) {
    String names = field.getVariables().stream().map(variable -> variable.getNameAsString()).toList().toString();
    return new Finding(id(), file.name(), line(field), file.name() + "|" + type + ".field" + names,
        "field " + type + "." + names + " is not private: a class shows what it does, not what it holds");
  }

  private Finding entrance(ParsedFile file, String type, CallableDeclaration<?> callable) {
    String what = type + "." + callable.getDeclarationAsString(false, false, false);
    return new Finding(id(), file.name(), line(callable), file.name() + "|" + what, what + " is an entrance to the "
        + "module the seal does not have: propose it, or keep it out of reach");
  }
}
