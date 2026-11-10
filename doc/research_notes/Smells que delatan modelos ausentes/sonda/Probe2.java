import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Second pass: features that need to know which names the project itself declares. */
public class Probe2 {
  record Parsed(String group, String name, CompilationUnit unit, long loc) {}

  static final Set<String> ownMethods = new HashSet<>();
  static final Set<String> ownFields = new HashSet<>();
  static final Set<String> ownTypes = new HashSet<>();
  static final Map<String, Set<String>> supertypes = new HashMap<>();

  public static void main(String[] args) throws Exception {
    StaticJavaParser.setConfiguration(new ParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    Path root = Path.of(args[0]);
    List<Parsed> all = new ArrayList<>();
    List<String> order = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      String[] spec = args[i].split("=");
      order.add(spec[0]);
      for (String dir : spec[1].split(",")) {
        Path base = root.resolve(dir).resolve("src/main/java");
        if (!Files.isDirectory(base)) continue;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(base)) {
          files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        for (Path file : files) {
          String text = Files.readString(file);
          try {
            long loc = text.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("//")
                && !l.startsWith("/*") && !l.startsWith("*")).count();
            String name = file.getFileName().toString().replace(".java", "");
            all.add(new Parsed(spec[0], name, StaticJavaParser.parse(text), loc));
          } catch (Exception e) {
            System.err.println("unparsed " + file);
          }
        }
      }
    }
    for (Parsed parsed : all) {
      parsed.unit().findAll(MethodDeclaration.class).forEach(m -> ownMethods.add(m.getNameAsString()));
      parsed.unit().findAll(FieldDeclaration.class).forEach(f -> f.getVariables()
          .forEach(v -> ownFields.add(v.getNameAsString())));
      parsed.unit().findAll(TypeDeclaration.class).forEach(t -> ownTypes.add(t.getNameAsString()));
      parsed.unit().findAll(ClassOrInterfaceDeclaration.class).forEach(t -> Stream.concat(
          t.getExtendedTypes().stream(), t.getImplementedTypes().stream()).forEach(s ->
          supertypes.computeIfAbsent(s.getNameAsString(), k -> new TreeSet<>()).add(t.getNameAsString())));
    }
    Map<String, Map<String, Double>> table = new LinkedHashMap<>();
    Map<String, Map<String, Map<String, Set<String>>>> lists = new LinkedHashMap<>();
    for (String group : order) {
      table.put(group, new LinkedHashMap<>());
      lists.put(group, new TreeMap<>());
    }
    for (Parsed parsed : all) {
      measure(parsed, table.get(parsed.group()), lists.get(parsed.group()));
    }
    Set<String> features = new LinkedHashSet<>();
    table.values().forEach(m -> features.addAll(m.keySet()));
    System.out.printf("%-62s", "per 1000 code lines");
    order.forEach(g -> System.out.printf("%10s", g));
    System.out.println();
    for (String feature : features) {
      System.out.printf("%-62s", feature);
      for (String group : order) {
        double kloc = table.get(group).getOrDefault("loc", 1.0) / 1000.0;
        double value = table.get(group).getOrDefault(feature, 0.0);
        System.out.printf("%10s", feature.equals("loc") ? String.format("%.0f", value)
            : String.format("%.2f", value / kloc));
      }
      System.out.println();
    }
    for (String group : order) {
      System.out.println("\n== " + group);
      lists.get(group).forEach((list, entries) -> {
        List<Map.Entry<String, Set<String>>> top = entries.entrySet().stream()
            .sorted((a, b) -> b.getValue().size() - a.getValue().size()).limit(6).toList();
        long repeated = entries.values().stream().filter(s -> s.size() >= 2).count();
        System.out.println("  " + list + " (" + entries.size() + " keys, " + repeated + " in 2+ places): "
            + top.stream().map(e -> e.getKey() + " x" + e.getValue().size()).collect(Collectors.joining(" | ")));
      });
    }
    System.out.println("\n== hierarchies of the project with 3+ direct subtypes, and who type-tests them");
    Map<String, Set<String>> tests = new TreeMap<>();
    for (Parsed parsed : all) {
      parsed.unit().findAll(InstanceOfExpr.class).forEach(test -> {
        String type = test.getTypeAsString().replaceAll("<.*", "");
        tests.computeIfAbsent(type.substring(type.lastIndexOf('.') + 1), k -> new TreeSet<>())
            .add(parsed.group() + "/" + parsed.name());
      });
    }
    supertypes.entrySet().stream().filter(e -> ownTypes.contains(e.getKey()) && e.getValue().size() >= 3)
        .sorted((a, b) -> b.getValue().size() - a.getValue().size()).forEach(e -> {
          Set<String> testers = new TreeSet<>();
          e.getValue().forEach(sub -> testers.addAll(tests.getOrDefault(sub, Set.of())));
          testers.addAll(tests.getOrDefault(e.getKey(), Set.of()));
          if (!testers.isEmpty()) {
            System.out.println("  " + e.getKey() + " (" + e.getValue().size() + " subtypes) tested in "
                + testers.size() + " files: " + testers.stream().limit(5).collect(Collectors.joining(", ")));
          }
        });
  }

  static void add(Map<String, Double> n, String feature, double amount) {
    n.merge(feature, amount, Double::sum);
  }

  static void note(Map<String, Map<String, Set<String>>> lists, String list, String key, String where) {
    lists.computeIfAbsent(list, k -> new TreeMap<>()).computeIfAbsent(key, k -> new TreeSet<>()).add(where);
  }

  static void measure(Parsed parsed, Map<String, Double> n, Map<String, Map<String, Set<String>>> lists) {
    CompilationUnit unit = parsed.unit();
    add(n, "loc", parsed.loc());
    List<MethodCallExpr> calls = unit.findAll(MethodCallExpr.class);
    for (MethodCallExpr call : calls) {
      if (!foreign(call)) continue;
      String name = call.getNameAsString();
      boolean own = ownMethods.contains(name);
      if (name.matches("set[A-Z].*") && call.getArguments().size() == 1) {
        add(n, own ? "setter on another object, declared in the project" : "setter on another object, library", 1);
      }
      if (call.getArguments().isEmpty() && name.matches("(get|is|has)[A-Z].*")) {
        add(n, own ? "getter on another object, declared in the project" : "getter on another object, library", 1);
      }
      Map<String, Long> unpacked = call.getArguments().stream().filter(Expression::isMethodCallExpr)
          .map(Expression::asMethodCallExpr).filter(a -> foreign(a) && a.getArguments().isEmpty())
          .collect(Collectors.groupingBy(a -> a.getScope().get().toString(), Collectors.counting()));
      if (unpacked.values().stream().anyMatch(count -> count >= 2)) {
        add(n, "call passing 2+ getters of one object as arguments", 1);
        note(lists, "unpacked object", name + "(..)", parsed.name());
      }
      if (Set.of("add", "put", "remove", "clear", "addAll", "removeIf").contains(name)
          && call.getScope().filter(s -> s.isMethodCallExpr() && foreign(s.asMethodCallExpr())
          && s.asMethodCallExpr().getArguments().isEmpty()).isPresent()) {
        add(n, "collection of another object changed through its getter", 1);
        note(lists, "collection changed from outside", call.getScope().get().asMethodCallExpr().getNameAsString()
            + "()." + name, parsed.name());
      }
    }
    for (FieldAccessExpr access : unit.findAll(FieldAccessExpr.class)) {
      String name = access.getNameAsString();
      if (!Character.isLowerCase(name.charAt(0)) || name.equals("length") || access.getScope().isThisExpr()
          || access.getScope().isSuperExpr()) continue;
      Expression scope = access.getScope();
      boolean variable = scope.isNameExpr() && Character.isLowerCase(scope.asNameExpr().getNameAsString().charAt(0))
          || scope.isMethodCallExpr() || scope.isFieldAccessExpr()
          && Character.isLowerCase(scope.asFieldAccessExpr().getNameAsString().charAt(0));
      if (!variable) continue;
      boolean written = access.getParentNode().filter(p -> p instanceof AssignExpr a && a.getTarget() == access)
          .isPresent();
      add(n, ownFields.contains(name) ? "field of another object read or written (project field)"
          : "field of another object (library or unknown)", 1);
      if (written) add(n, "field of another object WRITTEN", 1);
      if (ownFields.contains(name)) note(lists, "field reached from outside", name, parsed.name());
    }
    for (BinaryExpr test : unit.findAll(BinaryExpr.class, b -> (b.getOperator() == BinaryExpr.Operator.EQUALS
        || b.getOperator() == BinaryExpr.Operator.NOT_EQUALS)
        && (b.getLeft().isNullLiteralExpr() || b.getRight().isNullLiteralExpr()))) {
      Expression what = test.getLeft().isNullLiteralExpr() ? test.getRight() : test.getLeft();
      Set<String> fields = test.findAncestor(ClassOrInterfaceDeclaration.class).map(t -> t.getFields().stream()
          .flatMap(f -> f.getVariables().stream()).map(VariableDeclarator::getNameAsString)
          .collect(Collectors.toSet())).orElse(Set.of());
      Set<String> parameters = test.findAncestor(CallableDeclaration.class).map(c -> ((CallableDeclaration<?>) c)
          .getParameters().stream().map(p -> p.getNameAsString()).collect(Collectors.toSet())).orElse(Set.of());
      String kind = what.isMethodCallExpr() ? "null check of a call result"
          : what.isNameExpr() && parameters.contains(what.asNameExpr().getNameAsString()) ? "null check of a parameter"
          : what.isNameExpr() && fields.contains(what.asNameExpr().getNameAsString())
          || what.isFieldAccessExpr() ? "null check of a field" : "null check of a local";
      add(n, kind, 1);
      if (what.isMethodCallExpr()) {
        note(lists, "call result null-checked", what.asMethodCallExpr().getNameAsString() + "()", parsed.name());
      }
      if (kind.equals("null check of a field")) {
        note(lists, "field null-checked", parsed.name() + "." + what.toString().replace("this.", ""),
            test.findAncestor(MethodDeclaration.class).map(m -> m.getNameAsString()).orElse("?"));
      }
    }
    List<CallableDeclaration<?>> callables = new ArrayList<>(unit.findAll(MethodDeclaration.class));
    callables.addAll(unit.findAll(ConstructorDeclaration.class));
    for (CallableDeclaration<?> callable : callables) {
      String where = parsed.name() + "." + callable.getNameAsString();
      Map<String, Long> setters = callable.findAll(MethodCallExpr.class).stream().filter(c -> foreign(c)
          && c.getNameAsString().matches("set[A-Z].*") && c.getArguments().size() == 1
          && ownMethods.contains(c.getNameAsString()))
          .collect(Collectors.groupingBy(c -> c.getScope().get().toString(), Collectors.counting()));
      setters.forEach((receiver, count) -> {
        if (count >= 3) {
          add(n, "method setting 3+ properties of one project object", 1);
          note(lists, "puppeteer", where + " sets " + count + " of " + receiver, where);
        }
      });
      Map<String, Long> getters = callable.findAll(MethodCallExpr.class).stream().filter(c -> foreign(c)
          && c.getArguments().isEmpty() && c.getNameAsString().matches("(get|is|has)[A-Z].*")
          && ownMethods.contains(c.getNameAsString()) && c.getScope().get().isNameExpr())
          .collect(Collectors.groupingBy(c -> c.getScope().get().toString(),
              Collectors.mapping(c -> c.getNameAsString(), Collectors.toSet())))
          .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue().size()));
      getters.forEach((receiver, count) -> {
        if (count >= 4) {
          add(n, "method reading 4+ different properties of one project object", 1);
          note(lists, "interrogator", where + " reads " + count + " of " + receiver, where);
        }
      });
      Set<String> flags = callable.getParameters().stream().filter(p -> p.getTypeAsString().equals("boolean"))
          .map(p -> p.getNameAsString()).collect(Collectors.toSet());
      List<Expression> conditions = new ArrayList<>();
      callable.findAll(IfStmt.class).forEach(i -> conditions.add(i.getCondition()));
      callable.findAll(ConditionalExpr.class).forEach(c -> conditions.add(c.getCondition()));
      if (!flags.isEmpty() && conditions.stream().anyMatch(c -> c.isNameExpr()
          && flags.contains(c.asNameExpr().getNameAsString()) || c.isUnaryExpr()
          && c.asUnaryExpr().getExpression().isNameExpr()
          && flags.contains(c.asUnaryExpr().getExpression().asNameExpr().getNameAsString()))) {
        add(n, "method that branches on its own boolean parameter", 1);
        note(lists, "flag argument", where, where);
      }
      for (ForEachStmt loop : callable.findAll(ForEachStmt.class)) {
        String variable = loop.getVariable().getVariables().get(0).getNameAsString();
        boolean search = loop.getBody().findAll(IfStmt.class).stream().anyMatch(i ->
            i.getThenStmt().findFirst(ReturnStmt.class).flatMap(ReturnStmt::getExpression)
                .filter(e -> e.toString().equals(variable)).isPresent());
        if (search) {
          add(n, "search loop (for-each, if, return the element)", 1);
          note(lists, "search loop", where + " over " + loop.getIterable(), where);
        }
      }
      long narrated = callable.getAllContainedComments().stream().filter(c -> c.isLineComment()).count()
          + callable.getOrphanComments().stream().filter(c -> c.isLineComment()).count();
      if (narrated >= 3) {
        add(n, "method with 3+ line comments inside", 1);
        note(lists, "narrated method", where + " (" + narrated + ")", where);
      }
      long locals = callable.findAll(VariableDeclarationExpr.class).stream()
          .mapToLong(v -> v.getVariables().size()).sum();
      if (locals >= 8) {
        add(n, "method with 8+ local variables", 1);
      }
    }
    for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class, t -> !t.isInterface())) {
      List<MethodDeclaration> methods = type.getMethods();
      long accessors = methods.stream().filter(m -> m.getNameAsString().matches("(get|is|set|has)[A-Z].*")
          && m.getBody().filter(b -> b.getStatements().size() <= 1).isPresent()).count();
      long fields = type.getFields().stream().filter(f -> !f.isStatic()).mapToLong(f -> f.getVariables().size()).sum();
      if (fields >= 3 && !methods.isEmpty() && accessors * 10 >= methods.size() * 8) {
        add(n, "data class (3+ fields, 80%+ of methods are accessors)", 1);
        note(lists, "data class", type.getNameAsString() + " (" + fields + " fields)", type.getNameAsString());
      }
      long listeners = type.getFields().stream().flatMap(f -> f.getVariables().stream())
          .filter(v -> v.getTypeAsString().matches("(List|Set|Collection|CopyOnWriteArrayList)<.*(Listener|Consumer|"
              + "Runnable|Observer|Handler).*")).count();
      if (listeners > 0) {
        add(n, "hand-rolled listener list field", listeners);
        note(lists, "listener list", type.getNameAsString(), type.getNameAsString());
      }
    }
  }

  static boolean foreign(MethodCallExpr call) {
    return call.getScope().filter(s -> !s.isThisExpr() && !s.isSuperExpr()).isPresent();
  }
}
