package com.fpetrola.cerco.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The second batch of candidates of the plan of model smells, each on the shape it is meant to catch. */
class CandidatesTest {
  private final Snippets snippets = new Snippets();

  private List<Finding> found(Rule rule, String... sources) {
    List<ParsedFile> files = new ArrayList<>();
    for (String source : sources) {
      files.add(snippets.parse("src/main/java/p/F" + files.size() + ".java", source));
    }
    return rule.inspect(files);
  }

  private List<String> details(Rule rule, String... sources) {
    return found(rule, sources).stream().map(Finding::detail).toList();
  }

  @Test
  void aBooleanParameterThatPicksWhatTheMethodDoes() {
    List<String> found = details(new FlagArgument(),
        "package p; class Deck { boolean on; void play(boolean fast) { if (fast) { skip(); } else { roll(); } }"
            + " int speed(boolean fast) { return fast ? 2 : 1; } void setOn(boolean on) { if (on) { roll(); } }"
            + " private void quiet(boolean mute) { if (mute) { skip(); } } void skip() {} void roll() {} }");
    assertEquals(List.of("`fast` picks which of two things p.Deck.play(boolean) does",
        "`fast` picks which of two things p.Deck.speed(boolean) does"), found);
  }

  @Test
  void aFieldTestedInThreeMethodsIsAStateOrAKind() {
    List<Finding> found = found(new StateAskedAcrossMethods(),
        "package p; class Deck { boolean playing; final Kind kind; int n; Deck(Kind kind) { this.kind = kind; }"
            + " void press() { if (playing) { n++; } playing = !playing; } void eject() { if (!playing) { n = 0; } }"
            + " int light() { return playing ? 1 : 0; }"
            + " int a() { return kind == Kind.TAPE ? 1 : 0; } int b() { if (kind == Kind.DISK) { return 2; } return 0; }"
            + " int c(int n) { while (kind != Kind.TAPE && n > 0) { n--; } return n; } } enum Kind { TAPE, DISK }");
    assertEquals(List.of("`kind` is tested in 3 methods of p.Deck: a(), b(), c()",
        "`playing` is tested in 3 methods of p.Deck: eject(), light(), press()"),
        found.stream().map(Finding::detail).toList());
    assertEquals("variation-as-data", found.get(0).reading().orElseThrow().model().id());
    assertEquals("state-without-an-object: `playing` is changed in press() and tested in 3 methods",
        found.get(1).reading().map(reading -> reading.model().id() + ": " + reading.because()).orElse(""));
  }

  @Test
  void mapsWithTheSameKindOfKey() {
    List<String> found = details(new MapsKeyedAlike(),
        "package p; import java.util.*; class Roms { Map<String, String> chosen = new HashMap<>();"
            + " HashMap<String, List<String>> sets; Map<Integer, String> byPage; List<String> names; }");
    assertEquals(List.of("p.Roms keeps 2 maps keyed by `String`: chosen, sets"), found);
  }

  @Test
  void anObjectTakenApartForACall() {
    List<String> found = details(new UnpackedAtTheCall(),
        "package p; class State { int pc; int getA() { return 0; } int getB() { return 0; } }"
            + " record Other(int x, int y, int z) {}"
            + " class Loader { void load(State s, Other o, java.awt.Point p, Other maker) {"
            + " set(s.getA(), s.getB(), s.pc, o.x()); set(s.getA(), o.x(), 3, 4); new Pair(o.x(), o.y(), o.z(), 1);"
            + " set(p.getX(), p.getY(), p.x, 0); } void set(int a, int b, int c, int d) {} }");
    assertEquals(List.of("`set(...)` is handed 3 parts of `s`: s.getA(), s.getB(), s.pc",
        "`new Pair(...)` is handed 3 parts of `o`: o.x(), o.y(), o.z()"), found);
  }

  @Test
  void aTextComparedInSeveralMembers() {
    List<String> found = details(new NamedByText(),
        "package p; class Core { void set(String option) { if (option.equals(\"turbo\")) { } }"
            + " boolean fast(String option) { return \"turbo\".equals(option) || option.equals(\"once\"); }"
            + " int f(String o) { switch (o) { case \"turbo\": return 1; default: return 0; } }"
            + " String label() { return \"turbo\"; } }");
    assertEquals(3, found.size());
    assertTrue(found.get(0).startsWith("\"turbo\" is compared in p.Core.set(String); 3 members compare with it"));
  }

  @Test
  void twoCallsThatAlwaysGoTogether() {
    String loop = "package p; class Loop { void stop() {} void clear() {} void go() {} }";
    List<String> found = details(new SaidInTwoCalls(), loop,
        "package p; class A { void a(Loop loop) { loop.stop(); loop.clear(); } void b(Loop l) { l.stop(); l.clear(); }"
            + " void c(Loop loop) { loop.stop(); loop.clear(); loop.go(); }"
            + " void d(java.util.List<Loop> all, Loop one) { all.clear(); all.add(one); }"
            + " void e(java.util.List<Loop> all, Loop one) { all.clear(); all.add(one); }"
            + " void f(java.util.List<Loop> all, Loop one) { all.clear(); all.add(one); } }");
    assertEquals(3, found.size());
    assertEquals("`loop` is told `stop(); clear()` in p.A.a(Loop); 3 members say the same two calls in a row",
        found.get(0));
  }

  @Test
  void theSameQuestionComposedInSeveralMembers() {
    List<String> found = details(new ConditionRepeated(),
        "package p; class Desk { int a(Frame w) { return w.isAttached() && w.machine() == null ? 1 : 0; }"
            + " void b(Frame w) { if (w.isAttached() && w.machine() == null) { } }"
            + " void c(Frame w) { while (w.isAttached() && w.machine() == null) { } }"
            + " void d(Frame w, int n) { if (n > 0 && n < 9) { } if (w.isAttached()) { } } }");
    assertEquals(3, found.size());
    assertTrue(found.contains("`w.isAttached() && w.machine() == null` in p.Desk.a(Frame); 3 members write this "
        + "same condition"));
  }

  @Test
  void aCollectionHandedOutAsItIsKept() {
    List<String> found = details(new HandedOutWhole(),
        "package p; import java.util.*; class Desk { private final List<String> windows = new ArrayList<>();"
            + " int count; List<String> windows() { return windows; } int count() { return count; }"
            + " List<String> copy() { return List.copyOf(windows); } private List<String> own() { return windows; } }");
    assertEquals(List.of("p.Desk.windows() hands out `windows`, the List<String> its object keeps"), found);
  }

  @Test
  void aGroupOfFieldsSeveralTypesKeep() {
    List<String> found = details(new KeptTogether(),
        "package p; class Speccy { Cpu cpu; Display display; Memory memory; int n; }"
            + " class Machine { Cpu cpu; Display display; Memory memory; String name; }"
            + " class Core { Cpu cpu; Display display; Memory memory; }"
            + " class Lone { Cpu cpu; Display display; long memory; }"
            + " class Cpu {} class Display {} class Memory {}");
    assertEquals(3, found.size());
    assertTrue(found.contains("p.Core keeps `cpu, display, memory`, as 2 other types do: Machine, Speccy"));
  }

  @Test
  void walksAreCountedPerOwnerWhenTheProjectKnowsIt() {
    String owners = "package p; import java.util.*; class Tools { static Tools theOne() { return new Tools(); }"
        + " List<String> all() { return List.of(); } } class Scalers { List<String> all() { return List.of(); } }"
        + " class Holder { Tools tools; }";
    List<String> found = details(new WalkedFromOutside(), owners,
        "package p; class A extends Holder { void a(Tools t) { for (String s : t.all()) { } }"
            + " void b() { Tools.theOne().all().forEach(s -> { }); } void c() { tools.all().stream().count(); }"
            + " void d(Scalers s) { s.all().forEach(x -> { }); } void e(Scalers s) { for (String x : s.all()) { } } }");
    assertEquals(3, found.size());
    assertTrue(found.stream().allMatch(detail -> detail.startsWith("what all() of Tools hands out")));
  }

  @Test
  void oneAndManyThroughSeparateMethods() {
    List<String> found = details(new OneAndMany(),
        "package p; import java.util.*; class Bus { void plug(Device d) {} void plug(List<Device> all) {}"
            + " void wire(Device... all) {} void wire(Device d) {} void lone(List<Device> all) {} }");
    assertEquals(2, found.size());
    assertTrue(found.get(0).startsWith("p.Bus has plug for one `Device` and for many"));
  }

  @Test
  void theSameThingSaidToEachByHand() {
    List<String> found = details(new ToldOneByOne(),
        "package p; class Machine { Part cpu, ula, sound; void reset() { cpu.reset(); ula.reset(); sound.reset();"
            + " cpu.start(); cpu.start(); } void two() { cpu.reset(); ula.reset(); }"
            + " void swing(javax.swing.JButton a, javax.swing.JButton b, javax.swing.JButton c) {"
            + " a.doClick(); b.doClick(); c.doClick(); } } class Part { void reset() {} void start() {} }");
    assertEquals(List.of("3 objects in a row are told `reset()` in p.Machine.reset()"), found);
  }

  @Test
  void requestsNamedByTextAreCommands() {
    List<Finding> found = new SwitchDispatch().inspect(List.of(new Snippets().parse("src/main/java/p/A.java",
        "package p; class Core { void run(String command) { switch (command) { case \"reset\": stop(); clear();"
            + " break; case \"go\": start(); break; default: stop(); } } void stop() {} void clear() {}"
            + " void start() {} }")));
    assertEquals("each text compared with `command` picks an action of its own",
        found.get(0).reading().orElseThrow().because());
  }

  @Test
  void anInheritedOperationAnsweredWithNothing() {
    List<String> found = details(new OverriddenToNothing(),
        "package p; interface Device { void reset(); void write(int v); int read(); }"
            + " class Rom implements Device, Runnable { @Override public void reset() { }"
            + " @Override public void write(int v) { throw new UnsupportedOperationException(); }"
            + " @Override public int read() { return 1; } @Override public void run() { } void own() { } }");
    assertEquals(List.of("p.Rom.reset() overrides with nothing what `Device` promises",
        "p.Rom.write(int) overrides with a refusal what `Device` promises"), found);
  }

  @Test
  void aClassThatOnlyPassesCallsOn() {
    List<String> found = details(new MiddleMan(),
        "package p; class Front { private final Back back = new Back(); void a() { back.a(); }"
            + " int b() { return back.b(); } void c(int n) { this.back.c(n); } }"
            + " class Works { Back back; void a() { back.a(); } int b() { return 1; } void c() { back.c(1); } }");
    assertEquals(List.of("p.Front only passes its 3 methods on to `back`"), found);
  }

  @Test
  void dataInOneClassAndTheWorkInAnother() {
    List<String> found = details(new DataWithoutBehaviour(),
        "package p; class State { int a; int b; int c; int getA() { return a; } int getB() { return b; }"
            + " int getC() { return c; } void setA(int a) { this.a = a; } }"
            + " class Model { int a; int b; int c; int getA() { return a; } int getB() { return b; }"
            + " int getC() { return c; } int sum() { return a + b + c; } }",
        "package p; class Saver { int save(State s, Model m) { return s.getA() + s.getB() + s.getC()"
            + " + m.getA() + m.getB() + m.getC(); } }");
    assertEquals(List.of("p.Saver.save(State, Model) asks `s`, a State that only holds data, 3 things: getA, getB, "
        + "getC"), found);
  }

  @Test
  void aSwitchKeptAsAMapOfCode() {
    List<String> found = details(new DispatchByKey(),
        "package p; import java.util.*; import java.util.function.*; class Core {"
            + " Map<String, Runnable> commands = new HashMap<>(); Map<String, Integer> sizes;"
            + " void f() { Map<Kind, Function<Integer, Integer>> byKind = new EnumMap<>(Kind.class); } } enum Kind { A }");
    assertEquals(List.of("`commands` in p.Core maps a key to code: Map<String,Runnable>",
        "`byKind` in p.Core.f() maps a key to code: Map<Kind,Function<Integer,Integer>>"), found);
  }
}
