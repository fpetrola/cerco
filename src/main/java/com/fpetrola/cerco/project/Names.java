package com.fpetrola.cerco.project;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.ast.type.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The declared type of what an expression names, as far as its own file shows it: a parameter, a local with its type
 * written or made with `new`, a pattern, a field of a type the expression sits in. What only the rest of the project
 * could tell (the answer of a call, an inherited field) is unknown here.
 */
public final class Names {
  private final Typed typed = new Typed();
  private final Reached reached = new Reached();
  private final Imports imports;

  public Names(Imports imports) {
    this.imports = imports;
  }

  public Optional<TypeName> typeOf(Expression expression) {
    return expression.accept(typed, this);
  }

  /** How an expression reaches what it names, for the project to follow; nothing when it starts from the unknown. */
  public Optional<Chain> chainOf(Expression expression) {
    return expression.accept(reached, this);
  }

  private String around(Node use) {
    return use.findAncestor(TypeDeclaration.class).map(TypeDeclaration::getNameAsString).orElse("");
  }

  /** Whether what an expression names is kept by an object, not by the member at work: a field, its own or not. */
  public boolean isField(Expression expression) {
    return expression.isFieldAccessExpr() || expression.toNameExpr()
        .filter(name -> local(name, name.getNameAsString()).isEmpty()).isPresent();
  }

  /** One declaration of a name: where it starts, the type written, and what it was first given. */
  private record Named(Position begin, Type type, Optional<Expression> given) {
  }

  private Optional<TypeName> named(Node use, String name) {
    return local(use, name).or(() -> field(use, name)).flatMap(this::simple);
  }

  /** The nearest declaration of that name before its use, inside the member the use sits in. */
  private Optional<Named> local(Node use, String name) {
    Optional<Position> at = use.getBegin();
    List<Named> declared = new ArrayList<>();
    use.findAncestor(BodyDeclaration.class).ifPresent(member -> {
      Node scope = member;
      scope.findAll(Parameter.class, one -> one.getNameAsString().equals(name)).forEach(one ->
          one.getBegin().ifPresent(begin -> declared.add(new Named(begin, one.getType(), Optional.empty()))));
      scope.findAll(VariableDeclarator.class, one -> one.getNameAsString().equals(name)).forEach(one ->
          one.getBegin().ifPresent(begin -> declared.add(new Named(begin, one.getType(), one.getInitializer()))));
      scope.findAll(TypePatternExpr.class, one -> one.getNameAsString().equals(name)).forEach(one ->
          one.getBegin().ifPresent(begin -> declared.add(new Named(begin, one.getType(), Optional.empty()))));
    });
    return declared.stream().filter(one -> at.filter(one.begin()::isBefore).isPresent())
        .max(Comparator.comparing(Named::begin));
  }

  /** A field by that name of the nearest type around the use that declares one. */
  private Optional<Named> field(Node use, String name) {
    Optional<TypeDeclaration> around = use.findAncestor(TypeDeclaration.class);
    Optional<Named> found = Optional.empty();
    while (around.isPresent() && found.isEmpty()) {
      TypeDeclaration<?> type = around.get();
      found = type.getFields().stream().flatMap(declaration -> declaration.getVariables().stream())
          .filter(one -> one.getNameAsString().equals(name)).findFirst()
          .flatMap(one -> one.getBegin().map(begin -> new Named(begin, one.getType(), one.getInitializer())));
      around = type.findAncestor(TypeDeclaration.class);
    }
    return found;
  }

  private Optional<TypeName> simple(Named named) {
    if (named.type().isVarType()) {
      return named.given().flatMap(this::typeOf);
    }
    return named.type().toClassOrInterfaceType().map(imports::name);
  }

  /** Each kind of expression says in its own way what type it has, when the file shows it. */
  private final class Typed extends WhenKnown<TypeName, Names> {

    @Override
    public Optional<TypeName> visit(NameExpr name, Names names) {
      return named(name, name.getNameAsString());
    }

    @Override
    public Optional<TypeName> visit(FieldAccessExpr access, Names names) {
      return Optional.of(access).filter(own -> own.getScope().isThisExpr())
          .flatMap(own -> field(own, own.getNameAsString())).flatMap(Names.this::simple);
    }

    @Override
    public Optional<TypeName> visit(ObjectCreationExpr created, Names names) {
      return Optional.of(imports.name(created.getType()));
    }

    @Override
    public Optional<TypeName> visit(CastExpr cast, Names names) {
      return cast.getType().toClassOrInterfaceType().map(imports::name);
    }

    @Override
    public Optional<TypeName> visit(EnclosedExpr enclosed, Names names) {
      return typeOf(enclosed.getInner());
    }

    @Override
    public Optional<TypeName> visit(ThisExpr self, Names names) {
      return self.findAncestor(TypeDeclaration.class).map(this::own);
    }

    private TypeName own(TypeDeclaration<?> type) {
      return imports.name(type);
    }
  }

  /** Each kind of expression says how it reaches what it names: from a type the file shows, or through members. */
  private final class Reached extends WhenKnown<Chain, Names> {

    /** A name of known type starts there; a local made from an expression goes on from it; a field is asked. */
    @Override
    public Optional<Chain> visit(NameExpr name, Names names) {
      String text = name.getNameAsString();
      Optional<Chain> typed = typeOf(name).map(type -> new Chain(Optional.of(type), around(name), List.of()));
      Optional<Chain> made = local(name, text).flatMap(Named::given).flatMap(Names.this::chainOf);
      boolean aType = Character.isUpperCase(text.charAt(0));
      Optional<Chain> inherited = Optional.of(aType ? new Chain(Optional.of(imports.named(text)), around(name),
          List.of()) : new Chain(Optional.empty(), around(name), List.of(text)))
          .filter(chain -> local(name, text).isEmpty());
      return typed.or(() -> made).or(() -> inherited);
    }

    @Override
    public Optional<Chain> visit(ThisExpr self, Names names) {
      return typeOf(self).map(type -> new Chain(Optional.of(type), around(self), List.of()));
    }

    @Override
    public Optional<Chain> visit(ObjectCreationExpr created, Names names) {
      return typeOf(created).map(type -> new Chain(Optional.of(type), around(created), List.of()));
    }

    @Override
    public Optional<Chain> visit(EnclosedExpr enclosed, Names names) {
      return chainOf(enclosed.getInner());
    }

    @Override
    public Optional<Chain> visit(FieldAccessExpr access, Names names) {
      return chainOf(access.getScope()).map(chain -> chain.then(access.getNameAsString()));
    }

    @Override
    public Optional<Chain> visit(MethodCallExpr call, Names names) {
      Optional<Chain> own = Optional.of(new Chain(Optional.empty(), around(call), List.of()));
      return call.getScope().map(Names.this::chainOf).orElse(own).map(chain -> chain.then(call.getNameAsString()));
    }
  }
}
