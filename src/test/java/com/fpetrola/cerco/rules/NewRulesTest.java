package com.fpetrola.cerco.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.model.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The rules that arrived with 0.2.0, each on the shape of code that slipped past 0.1.0 in the vista project. */
class NewRulesTest {
  private final Snippets snippets = new Snippets();

  private List<String> details(Rule rule, String code) {
    return snippets.findings(rule, code).stream().map(Finding::detail).toList();
  }

  @Test
  void castsInEveryDisguise() {
    List<String> found = details(new CastDispatch(),
        "package p; import java.util.stream.Stream; class A { Object o; A() {"
            + " String s = (String) o; int i = (int) 3L; Object e = Element.class.cast(o);"
            + " Stream.of(o).map(Element.class::cast).count(); Class<A> c = A.class; c.cast(o); } }");
    assertEquals(4, found.size());
    assertTrue(found.get(0).startsWith("`(String) o`"));
    assertTrue(found.get(1).startsWith("`Element.class.cast(o)`"));
    assertTrue(found.get(2).startsWith("`Element.class::cast`"));
    assertTrue(found.get(3).startsWith("`c.cast(o)`"));
  }

  @Test
  void typeCodesComparedAndClassesInspected() {
    List<String> found = details(new TypeCodeDispatch(),
        "package p; class A { boolean f(Node n, Object o, Element e) {"
            + " boolean a = n.getNodeType() == Node.ELEMENT_NODE;"
            + " boolean b = \"a\".equals(e.getTagName());"
            + " boolean c = e.getKind().equals(KIND);"
            + " Class<?> t = o.getClass();"
            + " String name = o.getClass().getSimpleName();"
            + " return a || b || c; } }");
    assertEquals(4, found.size());
    assertTrue(found.get(0).contains("n.getNodeType() == Node.ELEMENT_NODE"));
    assertTrue(found.get(1).contains("\"a\".equals(e.getTagName())"));
    assertTrue(found.get(2).contains("e.getKind().equals(KIND)"));
    assertTrue(found.get(3).contains("o.getClass()"));
  }

  @Test
  void ifChainsOfThreeOrMore() {
    List<String> found = details(new ConditionalChain(),
        "package p; class A { int f(int x) {"
            + " if (x == 1) { return 1; } else if (x == 2) { return 2; } else { return 3; } }"
            + " int g(int x) { if (x == 1) { return 1; } else { return 2; } }"
            + " int h(int x) { if (x == 1) { return 1; } if (x == 2) { return 2; } return 3; } }");
    assertEquals(List.of("3 branches in p.A.f(int)"), found);
  }

  @Test
  void mapsKeyedByClass() {
    List<String> found = details(new TypeKeyedMap(),
        "package p; import java.util.*; class A { Map<Class<?>, Runnable> byType = new HashMap<>();"
            + " Map<String, Runnable> byName; void f(Map<Class<? extends A>, A> m) {} }");
    assertEquals(2, found.size());
    assertTrue(found.get(0).startsWith("`Map<Class<?>, Runnable>`"));
    assertTrue(found.get(1).startsWith("`Map<Class<? extends A>, A>`"));
  }

  @Test
  void constantHolders() {
    List<String> found = details(new ConstantHolder(),
        "package p; final class Marks { static final String ID = \"id\"; private Marks() {} }"
            + " interface Codes { int A = 1; } class Real { static final int K = 1; int f() { return K; } }"
            + " class Empty {} enum E { X }");
    assertEquals(2, found.size());
    assertTrue(found.get(0).contains("`Marks` holds 1 constant(s)"));
    assertTrue(found.get(1).contains("`Codes`"));
  }

  @Test
  void nullReturnsAndSuppressedWarnings() {
    assertEquals(List.of("`return null` in p.A.f()"),
        details(new NullReturn(), "package p; class A { Object f() { return null; } Object g() { return this; } }"));
    List<String> found = details(new SuppressedWarning(),
        "package p; class A { @SuppressWarnings(\"unchecked\") void f() {} @Deprecated void g() {} }");
    assertEquals(1, found.size());
    assertTrue(found.get(0).startsWith("`@SuppressWarnings(\"unchecked\")` on p.A.f()"));
  }

  @Test
  void ignoredResultsOfTheProjectsOwnMethods() {
    List<Finding> found = new IgnoredResult().inspect(List.of(
        snippets.parse("src/main/java/p/Table.java", "package p; import java.util.*; class Table {"
            + " boolean follow(String s) { return true; } Optional<String> find(String s) { return Optional.empty(); }"
            + " void run() {} boolean add(String s) { return true; } }"),
        snippets.parse("src/main/java/p/Web.java", "package p; class Web { void f(Table t, java.util.List<String> l) {"
            + " t.follow(\"x\"); t.find(\"y\"); t.run(); l.add(\"z\"); t.add(\"w\");"
            + " if (t.follow(\"ok\")) { t.run(); } l.forEach(s -> t.follow(s)); } }")));
    assertEquals(2, found.size());
    assertTrue(found.get(0).detail().startsWith("`t.follow(\"x\")` drops"));
    assertTrue(found.get(1).detail().startsWith("`t.find(\"y\")` drops"));
  }

  @Test
  void methodsClonedAcrossThreeTypes() {
    String slot = " public java.util.List<String> parts() { return java.util.List.of(); }";
    List<Finding> found = new ClonedMethods().inspect(List.of(
        snippets.parse("src/main/java/p/A.java", "package p; class A {" + slot + " }"),
        snippets.parse("src/main/java/p/B.java", "package p; class B {" + slot + " }"),
        snippets.parse("src/main/java/p/C.java", "package p; class C {" + slot + " }"),
        snippets.parse("src/main/java/p/D.java", "package p; class D { public String toString() { return \"\"; } }"),
        snippets.parse("src/main/java/p/E.java", "package p; class E { public String toString() { return \"\"; } }"),
        snippets.parse("src/main/java/p/F.java", "package p; class F { public String toString() { return \"\"; } }")));
    assertEquals(3, found.size());
    assertEquals("src/main/java/p/A.java|p.A.parts()", found.get(0).key());
    assertTrue(found.get(0).detail().contains("same method as in 2 other type(s)"));
  }

  @Test
  void twoCopiesAreNotYetAClone() {
    String slot = " public java.util.List<String> parts() { return java.util.List.of(); }";
    assertTrue(new ClonedMethods().inspect(List.of(
        snippets.parse("src/main/java/p/A.java", "package p; class A {" + slot + " }"),
        snippets.parse("src/main/java/p/B.java", "package p; class B {" + slot + " }"))).isEmpty());
  }
  @Test
  void flagsSwitchedFromAnotherClass() {
    List<Finding> found = new FlagDrivenFromOutside().inspect(List.of(
        snippets.parse("src/main/java/p/State.java", "package p; class State { private boolean pending;"
            + " private boolean busy; void setPending(boolean pending) { this.pending = pending; }"
            + " void setBusy(boolean b) { busy = b; if (b) { pending = false; } }"
            + " void reset() { this.setPending(false); setPending(true); } }"),
        snippets.parse("src/main/java/p/Cpu.java", "package p; class Cpu { void run(State s, boolean copy) {"
            + " s.setPending(false); s.setPending(copy); s.setBusy(true); new javax.swing.JButton().setEnabled(true); } }")));
    assertEquals(1, found.size(), found.toString());
    assertTrue(found.get(0).detail().startsWith("`s.setPending(false)` drives a flag of another class from p.Cpu.run"));
  }
}
