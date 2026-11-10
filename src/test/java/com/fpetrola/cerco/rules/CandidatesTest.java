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
}
