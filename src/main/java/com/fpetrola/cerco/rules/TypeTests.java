package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.project.Imports;
import com.fpetrola.cerco.project.Names;
import com.fpetrola.cerco.project.TypeName;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/** Reads, around each type test of one file, what will tell its missing model apart at the end of the run. */
final class TypeTests {
  private static final Set<String> SIEVES = Set.of("filter", "anyMatch", "noneMatch", "allMatch", "map", "flatMap",
      "takeWhile", "dropWhile", "removeIf");
  private static final Set<String> STREAMING = Set.of("stream", "parallelStream", "sorted", "distinct", "peek",
      "limit", "skip");
  private static final Set<String> ANSWER_OBJECT = Set.of("newInstance", "readObject");
  private final Enclosing enclosing = new Enclosing();
  private final ObjectProtocol everyObject = new ObjectProtocol();
  private final Imports imports;
  private final Names names;
  private final Set<String> typeVariables;
  private final UnaryOperator<String> clipped;

  /** The file to read, and how to cut a long expression down to what a report line holds. */
  TypeTests(CompilationUnit unit, UnaryOperator<String> clipped) {
    this.clipped = clipped;
    this.imports = new Imports(unit);
    this.names = new Names(imports);
    this.typeVariables = unit.findAll(TypeParameter.class).stream().map(TypeParameter::getNameAsString)
        .collect(Collectors.toSet());
  }

  /** A test of a value against a type written at the test. */
  TypeTest of(Finding finding, Node test, Type type, Expression subject) {
    Optional<TypeName> tested = type.toClassOrInterfaceType().map(imports::name);
    String text = subject.toString();
    boolean mute = everyObject.insideEquals(test) || tested.filter(this::namesNoModel).isPresent() || subject.isLambdaExpr()
        || subject.isMethodReferenceExpr() || subject.isNullLiteralExpr();
    return new TypeTest(finding, tested, new TypeTest.Subject(text, subject.isThisExpr(), declared(subject),
        lookedUpBy(subject)), enclosing.of(test), inside(test), alongside(test, text), pickedFrom(test, text), mute);
  }

  /** A test that names its type but not what it is applied to, as `Type.class::cast` handed to a stream. */
  TypeTest sifting(Finding finding, Node test, Type type) {
    Optional<TypeName> tested = type.toClassOrInterfaceType().map(imports::name);
    Optional<String> source = enclosing.parentOf(test, MethodCallExpr.class).filter(this::sifts).map(this::source);
    return new TypeTest(finding, tested, nobody(), enclosing.of(test), inside(test), Set.of(), source,
        tested.filter(this::namesNoModel).isPresent());
  }

  /** A test whose type the file does not show: there is nothing to read in it. */
  TypeTest unread(Finding finding, Node test) {
    return new TypeTest(finding, Optional.empty(), nobody(), enclosing.of(test), inside(test), Set.of(),
        Optional.empty(), true);
  }

  private TypeTest.Subject nobody() {
    return new TypeTest.Subject("", false, Optional.empty(), Optional.empty());
  }

  /** A type variable is no type to dispatch on, and telling exceptions apart is the business of a catch. */
  private boolean namesNoModel(TypeName tested) {
    String name = tested.simple();
    return typeVariables.contains(name) || name.endsWith("Exception") || name.endsWith("Error")
        || name.equals("Throwable");
  }

  /** What the value was declared as; `Object` when reflection or a stream of objects made it. */
  private Optional<TypeName> declared(Expression subject) {
    return names.typeOf(subject).filter(declared -> !typeVariables.contains(declared.simple()))
        .or(() -> subject.toMethodCallExpr().filter(call -> ANSWER_OBJECT.contains(call.getNameAsString()))
            .map(call -> imports.ofTheLanguage("Object")));
  }

  /** The call that fetched the value by a class it was handed, as `registry.find(Screen.class)`. */
  private Optional<String> lookedUpBy(Expression subject) {
    return subject.toMethodCallExpr()
        .filter(call -> call.getArguments().stream().anyMatch(Expression::isClassExpr))
        .map(call -> clipped.apply(call.toString()));
  }

  private String inside(Node test) {
    return test.findAncestor(TypeDeclaration.class).map(TypeDeclaration::getNameAsString).orElse("");
  }

  /** Every type the same value is asked for, with instanceof, in the member the test sits in. */
  private Set<String> alongside(Node test, String subject) {
    Set<String> types = new TreeSet<>();
    test.findAncestor(BodyDeclaration.class).ifPresent(member -> {
      Node scope = member;
      scope.findAll(InstanceOfExpr.class, other -> other.getExpression().toString().equals(subject))
          .forEach(other -> other.getType().toClassOrInterfaceType().map(ClassOrInterfaceType::getNameAsString)
              .ifPresent(types::add));
    });
    return types;
  }

  /** The collection whose elements the test picks by type: the one a stream sifts, or a for-each walks. */
  private Optional<String> pickedFrom(Node test, String subject) {
    Optional<String> sifted = test.findAncestor(LambdaExpr.class, lambda -> lambda.getParameters().stream()
            .anyMatch(parameter -> parameter.getNameAsString().equals(subject)))
        .flatMap(lambda -> enclosing.parentOf(lambda, MethodCallExpr.class)).filter(this::sifts).map(this::source);
    return sifted.or(() -> test.findAncestor(ForEachStmt.class, loop -> loop.getVariable().getVariables().stream()
        .anyMatch(variable -> variable.getNameAsString().equals(subject)))
        .map(loop -> clipped.apply(loop.getIterable().toString())));
  }

  private boolean sifts(MethodCallExpr call) {
    return SIEVES.contains(call.getNameAsString());
  }

  /** What a chain of stream calls started from: `ways` in `ways.stream().filter(...)`. */
  private String source(MethodCallExpr sieve) {
    Expression source = sieve;
    Optional<Expression> before = sieve.getScope();
    while (before.isPresent()) {
      source = before.get();
      before = source.toMethodCallExpr().filter(this::streams).flatMap(MethodCallExpr::getScope);
    }
    return clipped.apply(source.toString());
  }

  private boolean streams(MethodCallExpr call) {
    return sifts(call) || STREAMING.contains(call.getNameAsString());
  }
}
