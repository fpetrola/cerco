import com.github.javaparser.JavaToken;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.*;

/** Structural features per KLOC for groups of source roots: Probe <root> name=dir[,dir...] ... */
public class Probe {
  static final Set<String> COLLECTIONS = Set.of("List", "Set", "Map", "Collection", "ArrayList", "HashMap",
      "LinkedHashMap", "HashSet", "LinkedHashSet", "TreeMap", "TreeSet", "Deque", "Queue", "LinkedList", "Iterable");
  static final Set<String> NOISE_CALLS = Set.of("append", "add", "put", "println", "print", "write", "addAll");
  static final Set<String> NOISE_PREFIX = Set.of("is", "has", "max", "min", "num", "last", "next", "prev", "current",
      "default", "old", "new", "the", "tmp", "total", "count", "on", "to", "get", "set", "my");

  static class Group {
    final String name;
    final Map<String, Double> n = new LinkedHashMap<>();
    final Map<String, Map<String, Set<String>>> lists = new TreeMap<>();
    final Map<String, Set<String>> windows = new HashMap<>();
    long totalWindows;
    Group(String name) { this.name = name; }
    void add(String feature, double amount) { n.merge(feature, amount, Double::sum); }
    void note(String list, String key, String where) {
      lists.computeIfAbsent(list, k -> new TreeMap<>()).computeIfAbsent(key, k -> new TreeSet<>()).add(where);
    }
  }

  public static void main(String[] args) throws Exception {
    StaticJavaParser.setConfiguration(new ParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    Path root = Path.of(args[0]);
    List<Group> groups = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      String[] spec = args[i].split("=");
      Group group = new Group(spec[0]);
      for (String dir : spec[1].split(",")) {
        Path base = root.resolve(dir).resolve("src/main/java");
        if (!Files.isDirectory(base)) continue;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(base)) {
          files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        for (Path file : files) {
          try {
            measure(group, root.relativize(file).toString(), Files.readString(file));
          } catch (Exception e) {
            group.add("unparsed", 1);
          }
        }
      }
      conclude(group);
      groups.add(group);
    }
    print(groups);
  }

  static void measure(Group g, String name, String text) {
    CompilationUnit unit = StaticJavaParser.parse(text);
    String shortName = name.substring(name.lastIndexOf('/') + 1).replace(".java", "");
    g.add("files", 1);
    g.add("loc", text.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("//")
        && !l.startsWith("/*") && !l.startsWith("*")).count());

    g.add("instanceof", unit.findAll(InstanceOfExpr.class).size());
    g.add("cast (reference)", unit.findAll(CastExpr.class, c -> !c.getType().isPrimitiveType()).size());
    g.add("switch", unit.findAll(SwitchStmt.class).size() + unit.findAll(SwitchExpr.class).size());
    g.add("else-if chain (3+ arms)", unit.findAll(IfStmt.class, Probe::chainHead).stream()
        .filter(i -> arms(i) >= 3).count());
    g.add("null check", unit.findAll(BinaryExpr.class, b -> (b.getOperator() == BinaryExpr.Operator.EQUALS
        || b.getOperator() == BinaryExpr.Operator.NOT_EQUALS)
        && (b.getLeft().isNullLiteralExpr() || b.getRight().isNullLiteralExpr())).size());

    List<MethodCallExpr> calls = unit.findAll(MethodCallExpr.class);
    g.add("getter call on another object", calls.stream().filter(Probe::foreignGetter).count());
    g.add("getter chain (x.getA().getB())", calls.stream().filter(c -> foreignGetter(c)
        && c.getScope().filter(s -> s.isMethodCallExpr() && getter(s.asMethodCallExpr())).isPresent()).count());
    g.add("setter call on another object", calls.stream().filter(c -> foreign(c)
        && c.getNameAsString().matches("set[A-Z].*") && c.getArguments().size() == 1).count());
    g.add("field of another object (x.y)", unit.findAll(FieldAccessExpr.class, f -> lower(f.getNameAsString())
        && !f.getNameAsString().equals("length") && !f.getScope().isThisExpr() && !f.getScope().isSuperExpr()
        && rootLower(f.getScope())
        && f.getParentNode().filter(p -> p instanceof FieldAccessExpr).isEmpty()).size());
    g.add("string compared to literal", calls.stream().filter(c -> c.getNameAsString().startsWith("equals")
        && (c.getScope().filter(Expression::isStringLiteralExpr).isPresent()
        || c.getArguments().stream().anyMatch(Expression::isStringLiteralExpr))).count()
        + unit.findAll(SwitchEntry.class, e -> e.getLabels().stream().anyMatch(Expression::isStringLiteralExpr)).size());
    g.add("bit arithmetic with a literal", unit.findAll(BinaryExpr.class, b -> Set.of(BinaryExpr.Operator.BINARY_AND,
        BinaryExpr.Operator.LEFT_SHIFT, BinaryExpr.Operator.SIGNED_RIGHT_SHIFT,
        BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT).contains(b.getOperator())
        && (b.getLeft().isIntegerLiteralExpr() || b.getRight().isIntegerLiteralExpr())).size());

    for (ForEachStmt loop : unit.findAll(ForEachStmt.class)) {
      Expression over = strip(loop.getIterable());
      if (over.isMethodCallExpr() && foreign(over.asMethodCallExpr())) {
        g.add("for-each over another object's getter", 1);
        g.note("iterated from outside", over.asMethodCallExpr().getNameAsString() + "()", shortName);
        if (loop.findAncestor(ForEachStmt.class).isPresent()) {
          g.add("nested for-each over getters", 1);
        }
      }
    }
    for (MethodCallExpr call : calls) {
      if (Set.of("stream", "forEach").contains(call.getNameAsString())) {
        call.getScope().map(Probe::strip).filter(s -> s.isMethodCallExpr() && foreign(s.asMethodCallExpr()))
            .ifPresent(s -> {
              g.add("stream/forEach over another object's getter", 1);
              g.note("iterated from outside", s.asMethodCallExpr().getNameAsString() + "()", shortName);
            });
      }
    }

    for (SwitchStmt sw : unit.findAll(SwitchStmt.class)) {
      discriminator(g, shortName, sw.getSelector());
      sameCases(g, shortName, sw.getEntries());
    }
    for (SwitchExpr sw : unit.findAll(SwitchExpr.class)) {
      discriminator(g, shortName, sw.getSelector());
      sameCases(g, shortName, sw.getEntries());
    }
    for (BinaryExpr eq : unit.findAll(BinaryExpr.class, b -> b.getOperator() == BinaryExpr.Operator.EQUALS
        || b.getOperator() == BinaryExpr.Operator.NOT_EQUALS)) {
      Stream.of(new Expression[] {eq.getLeft(), eq.getRight()}, new Expression[] {eq.getRight(), eq.getLeft()})
          .filter(p -> p[0].isMethodCallExpr() && foreign(p[0].asMethodCallExpr())
              && p[0].asMethodCallExpr().getArguments().isEmpty() && constant(p[1]))
          .findFirst().ifPresent(p -> {
            g.add("x.getter() == CONSTANT", 1);
            g.note("discriminator asked from outside", p[0].asMethodCallExpr().getNameAsString() + "()", shortName);
          });
    }

    List<CallableDeclaration<?>> callables = new ArrayList<>(unit.findAll(MethodDeclaration.class));
    callables.addAll(unit.findAll(ConstructorDeclaration.class));
    for (CallableDeclaration<?> callable : callables) {
      String where = shortName + "." + callable.getNameAsString();
      int length = callable.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);
      g.add("methods", 1);
      g.add("method lines (total)", length);
      if (length > 40) g.add("method over 40 lines", 1);
      if (depth(callable) >= 4) g.add("method nested 4+ deep", 1);
      g.add("parameters", callable.getParameters().size());
      if (callable.getParameters().size() >= 4) g.add("method with 4+ parameters", 1);
      boolean setter = callable.getNameAsString().matches("set[A-Z].*") && callable.getParameters().size() == 1;
      callable.getParameters().forEach(p -> {
        String type = p.getTypeAsString();
        if (p.getType().isPrimitiveType() || type.equals("String")) {
          g.add("primitive or String parameter", 1);
          g.note("recurring primitive parameter", type + " " + p.getNameAsString(), where);
        }
        if (type.equals("boolean") && !setter) g.add("boolean parameter (not a setter)", 1);
      });
      Set<String> tested = callable.findAll(InstanceOfExpr.class).stream().map(InstanceOfExpr::getTypeAsString)
          .collect(Collectors.toCollection(TreeSet::new));
      tested.forEach(type -> g.note("type tested with instanceof", type, where));
      if (tested.size() >= 2) g.note("family of types tested together", String.join(", ", tested), where);
      externalWalk(g, where, callable);
      body(callable).ifPresent(body -> {
        for (BlockStmt block : body.findAll(BlockStmt.class)) {
          pairs(g, where, block);
          windows(g, where, block);
        }
      });
    }
    g.add("static method, not private", unit.findAll(MethodDeclaration.class, m -> m.isStatic() && !m.isPrivate()
        && !m.getNameAsString().equals("main")).size());

    for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
      g.add("types", 1);
      if (type.isInterface()) g.add("interface", 1);
      else if (type.isAbstract()) g.add("abstract class", 1);
      if (!type.getExtendedTypes().isEmpty() || !type.getImplementedTypes().isEmpty()) {
        g.add("type that extends or implements", 1);
      }
      if (type.isInterface()) continue;
      String where = type.getNameAsString();
      List<FieldDeclaration> fields = type.getFields();
      List<VariableDeclarator> own = fields.stream().filter(f -> !f.isStatic())
          .flatMap(f -> f.getVariables().stream()).toList();
      g.add("fields", own.size());
      if (own.size() >= 10) g.add("class with 10+ fields", 1);
      if (type.getMethods().size() >= 20) g.add("class with 20+ methods", 1);
      long booleans = own.stream().filter(v -> v.getTypeAsString().equals("boolean")).count();
      g.add("boolean field", booleans);
      if (booleans >= 3) g.add("class with 3+ boolean fields", 1);
      List<VariableDeclarator> collections = own.stream().filter(v -> collection(v.getTypeAsString())).toList();
      g.add("collection or map field", collections.size());
      g.add("map field", own.stream().filter(v -> v.getTypeAsString().matches("(\\w+\\.)*\\w*Map<.*")).count());
      g.add("map field keyed by String", own.stream().filter(v -> v.getTypeAsString().matches("\\w*Map<String,.*"))
          .count());
      Map<String, Long> keyed = own.stream().map(VariableDeclarator::getTypeAsString)
          .filter(t -> t.matches("\\w*Map<.*")).map(t -> t.substring(t.indexOf('<') + 1).split(",")[0].strip())
          .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
      keyed.forEach((key, count) -> {
        if (count >= 2) {
          g.add("class with 2+ maps on the same key type", 1);
          g.note("parallel maps", where + " keyed by " + key + " x" + count, where);
        }
      });
      Map<String, List<String>> prefixes = new TreeMap<>();
      own.forEach(v -> prefix(v.getNameAsString()).ifPresent(p ->
          prefixes.computeIfAbsent(p, k -> new ArrayList<>()).add(v.getNameAsString())));
      prefixes.forEach((prefix, names) -> {
        if (names.size() >= 3) {
          g.add("field-name clump (3+ fields, one prefix)", 1);
          g.note("field-name clump", where + ": " + String.join(" ", names), where);
        }
      });
      Set<String> collectionNames = collections.stream().map(VariableDeclarator::getNameAsString)
          .collect(Collectors.toSet());
      for (MethodDeclaration method : type.getMethods()) {
        method.getBody().filter(b -> b.getStatements().size() == 1 && b.getStatement(0).isReturnStmt())
            .flatMap(b -> b.getStatement(0).asReturnStmt().getExpression())
            .filter(e -> e.isNameExpr() && collectionNames.contains(e.asNameExpr().getNameAsString()))
            .ifPresent(e -> {
              g.add("method that hands out its collection field", 1);
              g.note("collection handed out", where + "." + method.getNameAsString() + "()", where);
            });
      }
      stateChecks(g, where, type, own);
    }
    g.add("enum", unit.findAll(EnumDeclaration.class).size());
    g.add("enum with behaviour", unit.findAll(EnumDeclaration.class, e -> !e.getMethods().isEmpty()
        || e.getEntries().stream().anyMatch(c -> !c.getClassBody().isEmpty())).size());
    g.add("record", unit.findAll(RecordDeclaration.class).size());
    g.add("@Override", unit.findAll(MarkerAnnotationExpr.class, a -> a.getNameAsString().equals("Override")).size());
    g.add("lambda", unit.findAll(LambdaExpr.class).size());
    g.add("anonymous class", unit.findAll(ObjectCreationExpr.class, o -> o.getAnonymousClassBody().isPresent()).size());
    g.add("accept/visit method", unit.findAll(MethodDeclaration.class, m -> m.getNameAsString().equals("accept")
        || m.getNameAsString().startsWith("visit")).size());

    List<Node> bodies = new ArrayList<>(callables);
    g.add("line comment inside a method", unit.getAllComments().stream().filter(Comment::isLineComment)
        .filter(c -> bodies.stream().anyMatch(b -> b.getRange().isPresent() && c.getRange().isPresent()
            && b.getRange().get().contains(c.getRange().get()))).count());
    Map<String, Long> literals = unit.findAll(StringLiteralExpr.class).stream().map(StringLiteralExpr::getValue)
        .filter(v -> v.length() >= 3).collect(Collectors.groupingBy(v -> v, Collectors.counting()));
    literals.forEach((value, count) -> {
      if (count >= 3) g.add("string literal repeated 3+ times in a file", count);
    });
  }

  static Optional<BlockStmt> body(CallableDeclaration<?> callable) {
    if (callable.isMethodDeclaration()) return callable.asMethodDeclaration().getBody();
    return Optional.of(callable.asConstructorDeclaration().getBody());
  }

  static void stateChecks(Group g, String where, ClassOrInterfaceDeclaration type, List<VariableDeclarator> own) {
    Set<String> mutable = type.getFields().stream().filter(f -> !f.isStatic() && !f.isFinal())
        .flatMap(f -> f.getVariables().stream()).map(VariableDeclarator::getNameAsString).collect(Collectors.toSet());
    Set<String> booleans = own.stream().filter(v -> v.getTypeAsString().equals("boolean"))
        .map(VariableDeclarator::getNameAsString).collect(Collectors.toSet());
    Map<String, Set<String>> testedIn = new TreeMap<>();
    for (MethodDeclaration method : type.getMethods()) {
      List<Expression> conditions = new ArrayList<>();
      method.findAll(IfStmt.class).forEach(i -> conditions.add(i.getCondition()));
      method.findAll(ConditionalExpr.class).forEach(c -> conditions.add(c.getCondition()));
      method.findAll(WhileStmt.class).forEach(w -> conditions.add(w.getCondition()));
      method.findAll(SwitchStmt.class).forEach(s -> conditions.add(s.getSelector()));
      method.findAll(SwitchExpr.class).forEach(s -> conditions.add(s.getSelector()));
      Set<String> names = new HashSet<>();
      conditions.forEach(c -> {
        c.findAll(NameExpr.class).forEach(nameExpr -> names.add(nameExpr.getNameAsString()));
        if (c.isNameExpr()) names.add(c.asNameExpr().getNameAsString());
      });
      names.stream().filter(mutable::contains).forEach(field ->
          testedIn.computeIfAbsent(field, k -> new TreeSet<>()).add(method.getNameAsString()));
    }
    testedIn.forEach((field, methods) -> {
      if (methods.size() >= 3) {
        g.add("mutable field tested in 3+ methods of its class", 1);
        if (booleans.contains(field)) g.add("boolean field tested in 3+ methods of its class", 1);
        g.note("state decided across methods", where + "." + field + " in " + methods.size() + " methods", where);
      }
    });
  }

  static void externalWalk(Group g, String where, CallableDeclaration<?> callable) {
    Set<String> parameters = callable.getParameters().stream().filter(p -> !p.getType().isPrimitiveType())
        .map(p -> p.getNameAsString()).collect(Collectors.toSet());
    String name = callable.getNameAsString();
    Predicate<Node> recurses = node -> !node.findAll(MethodCallExpr.class, c -> c.getNameAsString().equals(name)
        && c.getScope().filter(s -> !s.isThisExpr()).isEmpty()).isEmpty();
    for (ForEachStmt loop : callable.findAll(ForEachStmt.class)) {
      Expression over = strip(loop.getIterable());
      if (over.isMethodCallExpr() && rootedAt(over, parameters) && recurses.test(loop.getBody())) {
        g.add("recursive walk over a parameter's children", 1);
        g.note("walked from outside", where + " over " + over, where);
      }
    }
    for (MethodCallExpr each : callable.findAll(MethodCallExpr.class, c -> c.getNameAsString().equals("forEach"))) {
      if (each.getScope().filter(s -> rootedAt(s, parameters)).isPresent() && recurses.test(each)) {
        g.add("recursive walk over a parameter's children", 1);
        g.note("walked from outside", where + " over " + each.getScope().get(), where);
      }
    }
  }

  static boolean rootedAt(Expression expression, Set<String> names) {
    Expression current = expression;
    while (true) {
      if (current.isNameExpr()) return names.contains(current.asNameExpr().getNameAsString());
      if (current.isMethodCallExpr() && current.asMethodCallExpr().getScope().isPresent()) {
        current = current.asMethodCallExpr().getScope().get();
      } else if (current.isFieldAccessExpr()) {
        current = current.asFieldAccessExpr().getScope();
      } else {
        return false;
      }
    }
  }

  static void pairs(Group g, String where, BlockStmt block) {
    List<Statement> statements = block.getStatements();
    for (int i = 0; i + 1 < statements.size(); i++) {
      Optional<MethodCallExpr> first = call(statements.get(i));
      Optional<MethodCallExpr> second = call(statements.get(i + 1));
      if (first.isEmpty() || second.isEmpty()) continue;
      String a = first.get().getNameAsString();
      String b = second.get().getNameAsString();
      if (a.equals(b) || NOISE_CALLS.contains(a) || NOISE_CALLS.contains(b)) continue;
      if (first.get().getScope().get().toString().equals(second.get().getScope().get().toString())) {
        g.note("two calls in a row on one receiver", a + "(); " + b + "()", where);
      }
    }
  }

  static Optional<MethodCallExpr> call(Statement statement) {
    return Optional.of(statement).filter(Statement::isExpressionStmt).map(s -> s.asExpressionStmt().getExpression())
        .filter(Expression::isMethodCallExpr).map(Expression::asMethodCallExpr).filter(Probe::foreign);
  }

  static void windows(Group g, String where, BlockStmt block) {
    List<Statement> statements = block.getStatements();
    for (int i = 0; i + 3 <= statements.size(); i++) {
      StringBuilder shape = new StringBuilder();
      int tokens = 0;
      for (Statement statement : statements.subList(i, i + 3)) {
        if (statement.getTokenRange().isEmpty()) continue;
        for (JavaToken token : statement.getTokenRange().get()) {
          if (token.getCategory().isWhitespaceOrComment()) continue;
          tokens++;
          shape.append(token.getCategory().isIdentifier() ? "I" : token.getCategory().isLiteral() ? "L"
              : token.getText()).append(' ');
        }
      }
      if (tokens >= 30) {
        g.totalWindows++;
        g.windows.computeIfAbsent(shape.toString(), k -> new HashSet<>()).add(where);
      }
    }
  }

  static void discriminator(Group g, String where, Expression selector) {
    if (selector.isMethodCallExpr() && foreign(selector.asMethodCallExpr())) {
      g.add("switch on another object's getter", 1);
      g.note("discriminator asked from outside", selector.asMethodCallExpr().getNameAsString() + "()", where);
    }
  }

  static void sameCases(Group g, String where, List<SwitchEntry> entries) {
    TreeSet<String> names = new TreeSet<>();
    for (SwitchEntry entry : entries) {
      if (entry.getLabels().stream().anyMatch(l -> !l.isNameExpr())) return;
      entry.getLabels().forEach(l -> names.add(l.asNameExpr().getNameAsString()));
    }
    if (names.size() >= 2) g.note("same enum constants switched", String.join(", ", names), where);
  }

  static void conclude(Group g) {
    long duplicated = g.windows.values().stream().filter(methods -> methods.size() >= 2).mapToLong(Set::size).sum();
    g.add("3-statement shape shared by 2+ methods (type-2)", duplicated);
    g.add("3-statement windows", g.totalWindows);
    sum(g, "same enum constants switched", 2, "switch on constants also switched elsewhere");
    sum(g, "family of types tested together", 2, "type family tested in 2+ methods");
    sum(g, "type tested with instanceof", 3, "type tested with instanceof in 3+ methods");
    sum(g, "two calls in a row on one receiver", 3, "call pair on one receiver repeated in 3+ methods");
    sum(g, "iterated from outside", 3, "getter iterated from 3+ files");
    sum(g, "discriminator asked from outside", 2, "discriminator getter decided on in 2+ files");
    sum(g, "recurring primitive parameter", 8, "primitive parameter whose (type name) recurs in 8+ methods");
  }

  static void sum(Group g, String list, int atLeast, String feature) {
    g.add(feature, g.lists.getOrDefault(list, Map.of()).values().stream().filter(where -> where.size() >= atLeast)
        .mapToLong(Set::size).sum());
  }

  static int depth(Node node) {
    int deepest = 0;
    for (Node child : node.getChildNodes()) {
      int below = depth(child);
      boolean nests = child instanceof IfStmt || child instanceof ForStmt || child instanceof ForEachStmt
          || child instanceof WhileStmt || child instanceof DoStmt || child instanceof SwitchStmt
          || child instanceof TryStmt;
      boolean elseIf = child instanceof IfStmt && node instanceof IfStmt;
      deepest = Math.max(deepest, below + (nests && !elseIf ? 1 : 0));
    }
    return deepest;
  }

  static boolean chainHead(IfStmt statement) {
    return statement.getParentNode().filter(p -> p instanceof IfStmt parent
        && parent.getElseStmt().filter(e -> e == statement).isPresent()).isEmpty();
  }

  static int arms(IfStmt statement) {
    int arms = 1;
    Optional<Statement> next = statement.getElseStmt();
    while (next.isPresent()) {
      arms++;
      next = next.get().isIfStmt() ? next.get().asIfStmt().getElseStmt() : Optional.empty();
    }
    return arms;
  }

  static boolean foreign(MethodCallExpr call) {
    return call.getScope().filter(s -> !s.isThisExpr() && !s.isSuperExpr()).isPresent();
  }

  static boolean getter(MethodCallExpr call) {
    return call.getArguments().isEmpty() && call.getNameAsString().matches("(get|is|has)[A-Z].*");
  }

  static boolean foreignGetter(MethodCallExpr call) {
    return foreign(call) && getter(call);
  }

  static boolean lower(String name) {
    return !name.isEmpty() && Character.isLowerCase(name.charAt(0));
  }

  static boolean rootLower(Expression scope) {
    Expression current = scope;
    while (current.isFieldAccessExpr()) current = current.asFieldAccessExpr().getScope();
    while (current.isMethodCallExpr() && current.asMethodCallExpr().getScope().isPresent()) {
      current = current.asMethodCallExpr().getScope().get();
    }
    return current.isNameExpr() && lower(current.asNameExpr().getNameAsString()) || current.isMethodCallExpr();
  }

  static boolean constant(Expression expression) {
    String name = expression.isNameExpr() ? expression.asNameExpr().getNameAsString()
        : expression.isFieldAccessExpr() ? expression.asFieldAccessExpr().getNameAsString() : "";
    return name.length() > 1 && name.equals(name.toUpperCase()) && Character.isLetter(name.charAt(0));
  }

  static Expression strip(Expression expression) {
    if (expression.isMethodCallExpr() && Set.of("values", "entrySet", "keySet").contains(
        expression.asMethodCallExpr().getNameAsString()) && expression.asMethodCallExpr().getScope().isPresent()) {
      return expression.asMethodCallExpr().getScope().get();
    }
    return expression;
  }

  static boolean collection(String type) {
    String raw = type.contains("<") ? type.substring(0, type.indexOf('<')) : type;
    return COLLECTIONS.contains(raw.substring(raw.lastIndexOf('.') + 1)) || type.endsWith("[]");
  }

  static Optional<String> prefix(String name) {
    for (int i = 1; i < name.length(); i++) {
      if (Character.isUpperCase(name.charAt(i))) {
        String prefix = name.substring(0, i);
        return prefix.length() >= 3 && !NOISE_PREFIX.contains(prefix) ? Optional.of(prefix) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  static void print(List<Group> groups) {
    Set<String> features = new LinkedHashSet<>();
    groups.forEach(g -> features.addAll(g.n.keySet()));
    Set<String> absolute = Set.of("files", "loc");
    System.out.printf("%-58s", "per 1000 code lines");
    groups.forEach(g -> System.out.printf("%10s", g.name));
    System.out.println();
    for (String feature : features) {
      System.out.printf("%-58s", feature);
      for (Group g : groups) {
        double value = g.n.getOrDefault(feature, 0.0);
        double kloc = g.n.getOrDefault("loc", 1.0) / 1000.0;
        System.out.printf("%10s", absolute.contains(feature) ? String.format("%.0f", value)
            : String.format("%.2f", value / kloc));
      }
      System.out.println();
    }
    for (Group g : groups) {
      System.out.println("\n== " + g.name);
      g.lists.forEach((list, entries) -> {
        List<Map.Entry<String, Set<String>>> top = entries.entrySet().stream()
            .sorted((a, b) -> b.getValue().size() - a.getValue().size()).limit(6).toList();
        System.out.println("  " + list + " (" + entries.size() + " keys): " + top.stream()
            .map(e -> e.getKey() + " x" + e.getValue().size()).collect(Collectors.joining(" | ")));
      });
    }
  }
}
