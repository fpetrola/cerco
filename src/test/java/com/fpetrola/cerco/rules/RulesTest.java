package com.fpetrola.cerco.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Settings;
import java.util.List;
import org.junit.jupiter.api.Test;

class RulesTest {
  private final Snippets snippets = new Snippets();

  private List<String> keys(Rule rule, String code) {
    return snippets.findings(rule, code).stream().map(Finding::key).toList();
  }

  @Test
  void mutableStaticsButNotConstantsNorInterfaceFields() {
    List<String> keys = keys(new MutableStaticField(),
        "package p; class A { static int n; static final int K = 1; private static volatile String s; }"
            + " interface I { int X = 1; } @interface Q { int V = 2; }");
    assertEquals(List.of("src/main/java/p/A.java|p.A.n", "src/main/java/p/A.java|p.A.s"), keys);
  }

  @Test
  void publicMutableInstanceFieldsOnly() {
    List<String> keys = keys(new PublicMutableField(),
        "package p; class A { public int a; public final int b = 1; public static int c; private int d; }");
    assertEquals(List.of("src/main/java/p/A.java|p.A.a"), keys);
  }

  @Test
  void instanceofAndClassComparisons() {
    List<Finding> found = snippets.findings(new InstanceOfDispatch(),
        "package p; class A { boolean f(Object o) { return o instanceof String || o.getClass() == A.class; }"
            + " void g(Object o) { if (o instanceof String s) { s.length(); } o.getClass().getName(); } }");
    assertEquals(3, found.size());
    assertTrue(found.get(0).key().endsWith("|p.A.f(Object)|instanceof String"));
    assertTrue(found.get(1).key().endsWith("|p.A.f(Object)|getClass()=="));
    assertTrue(found.get(2).key().endsWith("|p.A.g(Object)|instanceof String"));
  }

  @Test
  void switchStatementsAndExpressions() {
    List<Finding> found = snippets.findings(new SwitchDispatch(),
        "package p; class A { int f(int x) { switch (x) { case 1: return 2; default: return 3; } }"
            + " int g(int x) { return switch (x) { case 1 -> 2; default -> 3; }; } }");
    assertEquals(2, found.size());
    assertTrue(found.get(0).detail().contains("2 entries in p.A.f(int)"));
  }

  @Test
  void utilityNames() {
    List<String> keys = keys(new UtilityClassName(Settings.DEFAULTS.utilitySuffixes()),
        "package p; class StringUtils {} class FooImpl {} class Parser {} class Simple {} class Templ {}");
    assertEquals(List.of("src/main/java/p/A.java|p.StringUtils", "src/main/java/p/A.java|p.FooImpl"), keys);
  }

  @Test
  void swallowedExceptions() {
    List<Finding> found = snippets.findings(new SwallowedException(),
        "package p; class A { void f() throws Exception {"
            + " try { f(); } catch (Exception e) {}"
            + " try { f(); } catch (Exception e) { throw new RuntimeException(e); }"
            + " try { f(); } catch (Exception e) { System.out.println(e); }"
            + " try { f(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }"
            + " try { f(); } catch (Exception ignored) { return; } } }");
    assertEquals(2, found.size());
    assertTrue(found.get(0).key().endsWith("|p.A.f()|catch(Exception)"));
  }

  @Test
  void staticMethodsExceptMainAndFactories() {
    List<String> keys = keys(new NonPrivateStaticMethod(Settings.DEFAULTS.factoryPrefixes()),
        "package p; class A { public static void main(String[] a) {} static A of(int x) { return null; }"
            + " public static A build() { return null; } private static int h() { return 1; }"
            + " public static int count() { return 1; } static String parseName(String s) { return s; }"
            + " static java.util.Optional<A> maybe() { return null; } }");
    assertEquals(List.of("src/main/java/p/A.java|p.A.count()", "src/main/java/p/A.java|p.A.maybe()"), keys);
  }

  @Test
  void lengthsAreMeasuredAndJudgedAgainstTheCeiling() {
    StringBuilder code = new StringBuilder("package p; class A {\n void f() {\n");
    for (int i = 0; i < 45; i++) {
      code.append("  int v").append(i).append(" = ").append(i).append(";\n");
    }
    code.append(" }\n}\n");
    ParsedFile file = snippets.parse("src/main/java/p/A.java", code.toString());
    assertEquals(49, new FileLength(400).measure(List.of(file)).get(0).value());
    assertTrue(new FileLength(400).inspect(List.of(file)).isEmpty());
    List<Finding> methods = new MethodLength(40).inspect(List.of(file));
    assertEquals(1, methods.size());
    assertEquals("src/main/java/p/A.java|p.A.f()", methods.get(0).key());
    assertTrue(methods.get(0).detail().contains("47 lines in p.A.f() (ceiling 40)"));
  }

  @Test
  void repeatedRunsOfSixStatements() {
    String six = " a(); b(); c(); d(); e(); f();";
    List<Finding> found = new RepeatedStatements().inspect(List.of(
        snippets.parse("src/main/java/p/A.java", "package p; class A { void one() {" + six + " } void a() {} }"),
        snippets.parse("src/main/java/p/B.java", "package p; class B { void two() {" + six + " } void b() {} }")));
    assertEquals(1, found.size());
    assertEquals("src/main/java/p/B.java", found.get(0).file());
    assertTrue(found.get(0).detail().contains("repeat src/main/java/p/A.java:1 (p.A.one())"));
  }

  @Test
  void packageCycles() {
    List<Finding> found = new PackageCycles().inspect(List.of(
        snippets.parse("src/main/java/a/A.java", "package a; import b.B; class A { B b; }"),
        snippets.parse("src/main/java/b/B.java", "package b; import a.A.Inner; class B { Object a; }"),
        snippets.parse("src/main/java/c/C.java", "package c; import a.*; import java.util.List; class C {}")));
    assertEquals(1, found.size());
    assertEquals("src/main/java/a/A.java|cycle:a,b", found.get(0).key());
  }

  @Test
  void filesThatDoNotParseAreFindings() {
    List<Finding> found = new Unparseable().inspect(List.of(snippets.parse("src/main/java/p/A.java", "class {")));
    assertEquals(1, found.size());
    assertEquals("src/main/java/p/A.java|parse", found.get(0).key());
  }

  @Test
  void theCatalogueDropsDisabledRules() {
    Settings settings = new Settings(400, 40, List.of("Util"), List.of("of"), List.of(), 10, false,
        List.of("no-switch"), true, List.of());
    assertTrue(new Rules(settings).ids().contains("no-instanceof"));
    assertTrue(!new Rules(settings).ids().contains("no-switch"));
    assertEquals(41, new Rules(Settings.DEFAULTS).all().size());
  }

  @Test
  void everyDesignRuleSaysWhatModelItsSmellMayHide() {
    List<String> silent = new Rules(Settings.DEFAULTS).all().stream()
        .filter(rule -> !rule.diagnosis().says() || rule.diagnosis().notAFix().isBlank()).map(Rule::id).sorted()
        .toList();
    assertEquals(List.of("no-ignored-result", "no-suppressed-warning", "no-swallowed-exception", "parseable"), silent);
  }
}
