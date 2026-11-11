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
  void askingAnotherObjectThenSettingWhatWasAsked() {
    List<String> found = details(new AskThenTell(), "package p; class A { State s; boolean on; void f(Cpu c) {"
        + " if (s.isHalted()) { s.setHalted(false); s.getPc().increment(); }"
        + " if (!s.isHalted()) s.setHalted(true); else other.setHalted(false);"
        + " if (c.getMode() == 2 && on) { log(); } else { c.setMode(1); }"
        + " if (s.isHalted()) { s.setRunning(true); other.setHalted(true); }"
        + " if (isOn()) { setOn(false); } if (this.isOn()) { this.setOn(false); }"
        + " if (c.getAt(3) == 0) { c.setAt(3); } } }");
    assertEquals(List.of("`s.setHalted(false)` sets what the if just asked, in p.A.f(Cpu)",
        "`s.setHalted(true)` sets what the if just asked, in p.A.f(Cpu)",
        "`c.setMode(1)` sets what the if just asked, in p.A.f(Cpu)"), found);
  }

  @Test
  void movingOneOfAnotherObjectsPropertiesIntoAnother() {
    List<String> found = details(new PropertyShuffle(), "package p; class A { State s; void f(Cpu c) {"
        + " s.setIff1(s.isIff2()); s.setIff2(s.getIff2()); c.setA(s.getB()); setA(getB());"
        + " this.setA(this.getB()); s.setA(s.getB(1)); s.put(s.getB()); s.setA(s.getB(), 2); } }");
    assertEquals(List.of("`s.setIff1(s.isIff2())` moves one property of an object into another, in p.A.f(Cpu)"),
        found);
  }

  @Test
  void aChangeOfAnotherObjectAnnouncedByTheCaller() {
    List<String> found = details(new NotifiedByCaller(), "package p; class A { Loop loop; boolean on;"
        + " void f(boolean p) { loop.setPaused(p); notifyPaused(p); }"
        + " void g() { loop.setPaused(true); this.fireChanged(); }"
        + " void h() { setOn(true); notifyOn(); this.setOn(false); notifyOn(); on = true; fireOn(); }"
        + " void i() { loop.setPaused(true); other.notifyPaused(true); loop.setPaused(false); log(); notifyX(); }"
        + " void j() { loop.pause(); notifyPaused(true); } }");
    assertEquals(List.of("`loop.setPaused(p)` is announced by the caller, in p.A.f(boolean)",
        "`loop.setPaused(true)` is announced by the caller, in p.A.g()"), found);
  }

  @Test
  void anotherObjectsModeSavedSwitchedAndSetBack() {
    List<String> found = details(new BorrowedMode(), "package p; class A { R r; boolean on;"
        + " void f(int v) { boolean was = r.isListening(); r.setListening(false); r.write(v); r.setListening(was); }"
        + " void g() { boolean was = r.isListening(); r.write(1); r.setListening(was); }"
        + " void h() { boolean was = isOn(); setOn(false); setOn(was); }"
        + " void i() { boolean was = r.isOn(); q.setOn(false); q.setOn(was); }"
        + " int j() { int w = r.getWidth(); r.setWidth(0); int t = r.measure(); r.setWidth(w); return t; } }");
    assertEquals(List.of("`r.isListening()` is saved, switched and set back by the caller, in p.A.f(int)",
        "`r.getWidth()` is saved, switched and set back by the caller, in p.A.j()"), found);
  }

  @Test
  void loopsThatKeepPickingTheSameKindOutOfTheSameCollection() {
    List<String> found = details(new RefilteredByType(), "package p; class A { Pane pane;"
        + " void f() { for (Frame f : pane.getAllFrames()) { if (f instanceof Machine m) m.refill(); } }"
        + " void g() { for (Frame f : pane.getAllFrames()) if (f instanceof Machine && f.isVisible()) use(f); }"
        + " void h() { for (Frame f : pane.getAllFrames()) if (f instanceof Player p) p.stop(); }"
        + " void i() { for (Frame f : frames) if (f instanceof Player p) p.stop(); }"
        + " void j() { for (Frame f : pane.getAllFrames()) if (other instanceof Machine) use(f); } }");
    assertEquals(List.of("2 loops over `pane.getAllFrames()` pick out Machine, in p.A.f()"), found);
  }

  @Test
  void aKeyKeptWithTheValueItWasBuiltFrom() {
    List<String> found = details(new KeyHoldsValue(), "package p; class A { Desk desk; java.util.Map<Core, M> m;"
        + " void f(M speccy) { Core core = new Core(speccy); desk.registerMachine(core, speccy); }"
        + " void g(M speccy, M other) { Core core = new Core(speccy); m.put(core, other); m.put(core, speccy); }"
        + " void h(M speccy) { Core core = make(speccy); m.put(core, speccy); }"
        + " void i(M speccy) { Core core = new Core(speccy); m.add(core, speccy); m.put(speccy, core); } }");
    assertEquals(List.of("`desk.registerMachine(core, speccy)` keeps a key with the value it was built from, in p.A.f(M)",
        "`m.put(core, speccy)` keeps a key with the value it was built from, in p.A.g(M, M)"), found);
  }

  @Test
  void theSameConstantsDecidedOverInSeveralPlaces() {
    List<String> found = details(new EnumDecidedOutside(), "package p; class A { Dock dock;"
        + " int f() { switch (dock) { case TOP -> { return 1; } case BOTTOM -> { return 2; } default -> { return 0; } } }"
        + " int g() { return switch (dock) { case BOTTOM -> 3; case TOP -> 4; default -> 0; }; }"
        + " int h(int n) { switch (n) { case 1: return 1; case 2: return 2; } return 0; }"
        + " int i(int n) { switch (n) { case 1: return 5; case 2: return 6; } return 0; }"
        + " boolean j() { return dock == Dock.TOP || dock == Dock.BOTTOM; }"
        + " boolean k() { return (dock == Dock.BOTTOM) || dock == Dock.TOP; }"
        + " boolean l() { return dock == Dock.LEFT || dock.flat(); } }");
    assertEquals(List.of("2 places decide over case BOTTOM, TOP, the first in p.A.f()",
        "2 places decide over Dock.BOTTOM, Dock.TOP, the first in p.A.j()"), found);
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

  @Test
  void aFieldAskedForNullInTwoMethodsButNotOwnNamesNorOneMethod() {
    List<String> found = details(new AbsenceAsState(),
        "package p; class A { private Window window; private Line line; private Sound sound; static Object shared;"
            + " void open() { if (window != null) window.show(); if (line == null) return; }"
            + " void close() { if (this.window == null) return; java.util.Objects.isNull(sound); }"
            + " void play(Line line) { if (line != null) line.start(); }"
            + " void mute() { Sound sound = null; if (sound == null) return; if (shared == null) return; } }");
    assertEquals(List.of("`window` is compared with null in 2 methods of p.A: close(), open()"), found);
  }

  @Test
  void candidatesAreKnownButDoNotGate() {
    Rules rules = new Rules(com.fpetrola.cerco.model.Settings.DEFAULTS);
    assertTrue(rules.known().contains("set-from-outside"));
    assertTrue(!rules.ids().contains("set-from-outside"));
  }

  @Test
  void aFamilyOfTheProjectTestedFromTwoMembersOutsideIt() {
    List<Finding> found = new HierarchyAskedFromOutside().inspect(List.of(
        snippets.parse("src/main/java/p/Ref.java", "package p; interface Ref { void accept(RefVisitor v); }"
            + " class Reg implements Ref {} class Mem implements Ref {} class Ind extends Mem {}"
            + " class Own implements Ref { boolean same(Object o) { return o instanceof Reg; }"
            + " public boolean equals(Object o) { return o instanceof Own; } }"),
        snippets.parse("src/main/java/p/Use.java", "package p; class Use {"
            + " int size(Ref r) { return r instanceof Reg ? 1 : 2; }"
            + " Mem memory(Ref r) { return (Mem) r; }"
            + " boolean text(Object o) { return o instanceof String; } }"),
        snippets.parse("src/main/java/p/Two.java", "package p; class Two { boolean pair(Ref r) { return r instanceof"
            + " Ind; } boolean alone(Object o) { return o instanceof Solo; } } class Solo {}")));
    assertEquals(List.of("src/main/java/p/Two.java|p.Two.pair(Ref)|Ref|instanceof Ind",
        "src/main/java/p/Use.java|p.Use.memory(Ref)|Ref|cast Mem",
        "src/main/java/p/Use.java|p.Use.size(Ref)|Ref|instanceof Reg"),
        found.stream().map(Finding::key).sorted().toList());
    assertTrue(found.get(0).detail().contains("Ref (4 subtypes) is tested in 3 members of 2 types outside it; "
        + "the family already accepts RefVisitor"), found.get(0).detail());
  }

  @Test
  void threeOfTheProjectsSettersOnOneObjectButNotLibrarySetters() {
    List<Finding> found = new SetFromOutside().inspect(List.of(
        snippets.parse("src/main/java/p/Z.java", "package p; class Z { void setA(int a) {} void setB(int b) {}"
            + " void setC(int c) {} void setD(int d) {} }"),
        snippets.parse("src/main/java/p/L.java", "package p; class L {"
            + " void load(Z z, State s) { z.setA(s.getA()); z.setB(s.getB()); z.setC(s.getC()); z.setD(1); }"
            + " void two(Z z) { z.setA(1); z.setB(2); }"
            + " void swing(javax.swing.JButton b) { b.setText(\"x\"); b.setEnabled(true); b.setVisible(true); }"
            + " void self() { setA(1); setB(2); setC(3); } void setA(int a) {} void setB(int b) {} void setC(int c) {} }")));
    assertEquals(1, found.size(), found.toString());
    assertEquals("p.L.load(Z, State) sets 4 properties of `z`: setA, setB, setC, setD; 3 of the values are getters of "
        + "s, a copy property by property", found.get(0).detail());
  }

  @Test
  void aFieldOfAnotherObjectReachedThroughFromTwoMembers() {
    List<Finding> found = new ReachedThrough().inspect(List.of(
        snippets.parse("src/main/java/p/Speccy.java", "package p; class Speccy { Loop loop; Machine machine; }"),
        snippets.parse("src/main/java/p/Core.java", "package p; class Core { Speccy speccy;"
            + " void pause() { speccy.loop.setPaused(true); } void resume() { speccy.loop.setPaused(false); }"
            + " void reset() { speccy.machine.reset(); } void out() { System.out.println(); } }"),
        snippets.parse("src/main/java/p/Pair.java", "package p; class Pair { Loop loop;"
            + " boolean same(Pair other) { return other.loop.equals(loop); } void again(Pair o) { o.loop.run(); } }")));
    assertEquals(List.of("src/main/java/p/Core.java|p.Core.pause()|speccy.loop",
        "src/main/java/p/Core.java|p.Core.resume()|speccy.loop"), found.stream().map(Finding::key).sorted().toList());
  }

  @Test
  void whatOneMethodHandsOutWalkedFromThreeMembersOfOtherTypes() {
    List<Finding> found = new WalkedFromOutside().inspect(List.of(
        snippets.parse("src/main/java/p/Desk.java", "package p; import java.util.*; class Desk {"
            + " List<W> windows() { return null; } Map<String, W> table() { return null; }"
            + " int own() { int n = 0; for (W w : windows()) n++; return n; } }"),
        snippets.parse("src/main/java/p/A.java", "package p; class A {"
            + " void a(Desk d) { for (W w : d.windows()) w.hide(); }"
            + " long b(Desk d) { return d.windows().stream().count(); }"
            + " void c(Desk d) { d.table().values().forEach(W::hide); }"
            + " void e(Desk d) { d.table().forEach((k, v) -> v.hide()); }"
            + " void f(javax.swing.JPanel p) { for (Object o : p.getComponents()) {} } }"),
        snippets.parse("src/main/java/p/B.java", "package p; class B { void g(Desk d) { d.windows().forEach(W::show); } }")));
    assertEquals(List.of("src/main/java/p/A.java|p.A.a(Desk)|windows()", "src/main/java/p/A.java|p.A.b(Desk)|windows()",
        "src/main/java/p/B.java|p.B.g(Desk)|windows()"), found.stream().map(Finding::key).sorted().toList());
  }

  @Test
  void theConstantsOfOneEnumDecidedInTwoMembersOfDifferentFiles() {
    List<Finding> found = new EnumDecidedAcross().inspect(List.of(
        snippets.parse("src/main/java/p/Kind.java", "package p; enum Kind { CHOICE, NUMBER, SWITCH }"),
        snippets.parse("src/main/java/p/Rows.java", "package p; class Rows { int f(Knob k) {"
            + " switch (k.kind()) { case CHOICE: return 1; case NUMBER: return 2; default: return 3; } } }"),
        snippets.parse("src/main/java/p/Settings.java", "package p; class Settings {"
            + " boolean g(Knob k) { return k.kind() == Kind.SWITCH; }"
            + " int h(int x) { switch (x) { case 1: return 1; default: return 0; } }"
            + " boolean i(Thread t) { return t.getState() == Thread.State.NEW; } }")));
    assertEquals(List.of("src/main/java/p/Rows.java|p.Rows.f(Knob)|Kind|CHOICE,NUMBER",
        "src/main/java/p/Settings.java|p.Settings.g(Knob)|Kind|SWITCH"), found.stream().map(Finding::key).sorted().toList());
  }
}
