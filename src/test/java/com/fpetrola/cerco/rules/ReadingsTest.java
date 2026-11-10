package com.fpetrola.cerco.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Each finding says which model the code at that place is missing, read against what the project declares. */
class ReadingsTest {
  private static final String SHAPES = "package p; interface Shape {} class Circle implements Shape {}"
      + " class Square implements Shape {} class Dot implements Shape {}";
  private final Snippets snippets = new Snippets();

  private List<Finding> found(Rule rule, String... sources) {
    List<ParsedFile> files = new ArrayList<>();
    for (String source : sources) {
      files.add(snippets.parse("src/main/java/p/F" + files.size() + ".java", source));
    }
    return rule.inspect(files);
  }

  private String read(Finding finding) {
    return finding.reading().map(reading -> reading.model().id() + ": " + reading.because()).orElse("unread");
  }

  @Test
  void aKindOfAFamilyAskedInOnePlaceLacksAMethod() {
    List<Finding> found = found(new InstanceOfDispatch(), SHAPES,
        "package p; class Painter { int sides(Shape s) { return s instanceof Square ? 4 : 0; } }");
    assertEquals("dispatch-from-outside: `Square` is a kind of `Shape` (3 subtypes)", read(found.get(0)));
    assertEquals(Optional.of("a method on the common type, implemented by each subtype"),
        found.get(0).reading().map(Reading::then));
  }

  @Test
  void aFamilyAskedInSeveralPlacesLacksAVisitor() {
    List<Finding> found = found(new InstanceOfDispatch(), SHAPES,
        "package p; class Painter { int sides(Shape s) { return s instanceof Square ? 4 : 0; }"
            + " boolean round(Shape s) { return s instanceof Circle; } }");
    assertEquals("dispatch-from-outside: `Square` is a kind of `Shape` (3 subtypes), asked in 2 members",
        read(found.get(0)));
    assertTrue(found.get(1).reading().orElseThrow().then().startsWith("a visitor the family accepts"));
  }

  @Test
  void aFamilyThatAlreadyAcceptsAVisitorIsToldSo() {
    List<Finding> found = found(new CastDispatch(),
        "package p; interface ShapeVisitor {} abstract class Shape { abstract void accept(ShapeVisitor v); }"
            + " class Circle extends Shape { void accept(ShapeVisitor v) {} }"
            + " class Square extends Shape { void accept(ShapeVisitor v) {} }",
        "package p; class Painter { void paint(Shape s) { Circle c = (Circle) s; } }");
    assertEquals("dispatch-from-outside: `Circle` is a kind of `Shape` (2 subtypes), which already accepts "
        + "`ShapeVisitor`", read(found.get(0)));
  }

  @Test
  void aFamilyOfAnotherModuleCannotTakeTheOperationIn() {
    List<Finding> found = new InstanceOfDispatch().inspect(List.of(
        snippets.parse("core/src/main/java/p/Shape.java", SHAPES),
        snippets.parse("ui/src/main/java/q/Painter.java",
            "package q; import p.Circle; class Painter { boolean round(p.Shape s) { return s instanceof Circle; } }")));
    assertEquals("dispatch-from-outside: `Circle` is a kind of `Shape` (3 subtypes), in `core`, which cannot depend "
        + "on `ui`", read(found.get(0)));
  }

  @Test
  void aTypeAskingItselfWhichSubtypeItIs() {
    List<Finding> found = found(new InstanceOfDispatch(),
        "package p; class Shape { boolean round() { return this instanceof Circle; } } class Circle extends Shape {}"
            + " class Square extends Shape { boolean like(Shape other) { return other instanceof Circle; } }");
    assertEquals("dispatch-from-outside: `this` is asked which subtype it is", read(found.get(0)));
    assertEquals("dispatch-from-outside: `Circle` is asked about inside its own family, `Shape`", read(found.get(1)));
  }

  @Test
  void pickingATypeOutOfACollection() {
    List<Finding> found = found(new InstanceOfDispatch(), SHAPES,
        "package p; import java.util.*; class Drawing { List<Shape> shapes;"
            + " long circles() { return shapes.stream().filter(s -> s instanceof Circle).count(); }"
            + " int corners() { int n = 0; for (Shape s : shapes) { if (s instanceof Square) n += 4;"
            + " if (s instanceof Dot) n += 1; } return n; } }");
    assertEquals("structure-walked-from-outside: it picks `Circle` out of `shapes`", read(found.get(0)));
    assertEquals("dispatch-from-outside: 2 kinds of `Shape` are told apart while walking `shapes`",
        read(found.get(1)));
  }

  @Test
  void aCastHandedToAStreamPicksToo() {
    List<Finding> found = found(new CastDispatch(), SHAPES,
        "package p; import java.util.*; class Drawing { List<Shape> shapes;"
            + " Object circles() { return shapes.stream().map(Circle.class::cast).toList(); } }");
    assertEquals("structure-walked-from-outside: it picks `Circle` out of `shapes`", read(found.get(0)));
  }

  @Test
  void aRoleSomeMembersPlay() {
    List<Finding> found = found(new InstanceOfDispatch(),
        "package p; interface Opens { boolean empty(); } class Frame {} class Deck extends Frame implements Opens {"
            + " public boolean empty() { return true; } } class Clock extends Frame {}",
        "package p; class Desk { boolean free(Frame window) { return window instanceof Opens holds && holds.empty(); }"
            + " boolean any(java.util.function.Supplier<?> s) { return s.get() instanceof Opens; } }");
    assertEquals("variation-as-data: `Opens` is not a kind of `Frame`: a role some of them play", read(found.get(0)));
    assertEquals("variation-as-data: the types that implement `Opens` belong to other families: a role",
        read(found.get(1)));
  }

  @Test
  void aRoleNobodyInTheProjectPlays() {
    List<Finding> found = found(new InstanceOfDispatch(),
        "package p; interface Opens {} class Desk { boolean player(java.util.function.Supplier<?> s) {"
            + " return s.get() instanceof Opens; } }");
    assertEquals("variation-as-data: nothing in this project implements `Opens`: a role played from outside",
        read(found.get(0)));
  }

  @Test
  void aTypeThatIsNotTheProjects() {
    List<Finding> found = found(new CastDispatch(),
        "package p; class Desk { Object label(javax.swing.JComponent c) { return (javax.swing.JLabel) c; } }");
    assertEquals("concept-without-a-class: `JLabel` is not a type of this project", read(found.get(0)));
  }

  @Test
  void aValueThatTravelsAsObject() {
    List<Finding> found = found(new CastDispatch(),
        "package p; class Desk { String name(Object o) { return (String) o; }"
            + " Desk made(Class<?> k) throws Exception { return (Desk) k.getConstructor().newInstance(); } }");
    assertEquals("value-without-a-type: `String` is cast back from `o`, which travels as `Object`",
        read(found.get(0)));
    assertEquals("structure-walked-from-outside: `Desk` arrives as `Object`", read(found.get(1)));
  }

  @Test
  void aTypeOfTheSameNameAsOneOfTheProjectsIsToldApartByItsImport() {
    List<Finding> found = found(new CastDispatch(), "package p; class Timer {} class Frame {}",
        "package q; import javax.swing.Timer; class Desk { Object t(java.awt.event.ActionEvent e, Object o) {"
            + " return e.getSource() == null ? (java.awt.Frame) o : (Timer) e.getSource(); } }",
        "package r; import javax.swing.*; class Bar { Object t(java.awt.event.ActionEvent e) {"
            + " return (Timer) e.getSource(); } }",
        "package p; class Near { Object t(java.util.function.Supplier<?> s) { return (Timer) s.get(); } }");
    assertEquals("value-without-a-type: `Frame` is cast back from `o`, which travels as `Object`",
        read(found.get(0)));
    assertEquals("concept-without-a-class: `Timer` is not a type of this project", read(found.get(1)));
    assertEquals("concept-without-a-class: `Timer` is not a type of this project", read(found.get(2)));
    assertEquals("structure-walked-from-outside: `Timer` has no family in this project: what is tested arrives as "
        + "less than it is", read(found.get(3)));
  }

  @Test
  void aThingFetchedByItsClassAndCastBack() {
    List<Finding> found = found(new CastDispatch(),
        "package p; class Screen {} class Registry { Object find(Class<?> kind) { return this; } }"
            + " class Desk { Screen screen(Registry registry) { return (Screen) registry.find(Screen.class); } }");
    assertEquals("structure-walked-from-outside: `Screen` is fetched by a class with `registry.find(Screen.class)` "
        + "and cast back", read(found.get(0)));
  }

  @Test
  void theTopOfAFamilyIsWhereAValueComesIn() {
    List<Finding> found = found(new InstanceOfDispatch(), SHAPES,
        "package p; class Desk { boolean drawn(java.util.function.Supplier<?> s) { return s.get() instanceof Shape; } }");
    assertEquals("structure-walked-from-outside: `Shape` is the top of its family: what is tested arrives as less "
        + "than it is", read(found.get(0)));
  }

  @Test
  void aLoneTypeOfTheProjectArrivesAsLessThanItIs() {
    List<Finding> found = found(new InstanceOfDispatch(),
        "package p; class Frame {} class Desk { boolean ours(Object o, javax.swing.JComponent c) {"
            + " return o instanceof Frame || c instanceof Frame; } }");
    assertEquals("structure-walked-from-outside: `Frame` arrives as `Object`", read(found.get(0)));
    assertEquals("structure-walked-from-outside: `Frame` arrives as `JComponent`", read(found.get(1)));
  }

  @Test
  void whereNoModelIsMissingNothingIsRead() {
    List<Finding> found = found(new InstanceOfDispatch(),
        "package p; class Point { int x; public boolean equals(Object o) { return o instanceof Point p && p.x == x; }"
            + " <T> boolean is(Object o) { return o instanceof T; } }");
    assertEquals(List.of("unread", "unread"), found.stream().map(this::read).toList());
    List<Finding> casts = found(new CastDispatch(),
        "package p; class A { Runnable r = (Runnable) () -> {}; Object c(Class<?> k, Object o) { return k.cast(o); }"
            + " String none() { return (String) null; }"
            + " boolean late(Exception e) { return e instanceof java.io.IOException; } }");
    assertEquals(List.of("unread", "unread", "unread"), casts.stream().map(this::read).toList());
    assertEquals(List.of("unread"), found(new InstanceOfDispatch(),
        "package p; class A { boolean late(Exception e) { return e instanceof java.io.IOException; } }").stream()
        .map(this::read).toList());
  }

  @Test
  void aPartialRunDoesNotCallForeignWhatItHasNotSeen() {
    Rule rule = new InstanceOfDispatch();
    com.fpetrola.cerco.project.Project partial = new com.fpetrola.cerco.project.Project();
    ParsedFile file = snippets.parse("src/main/java/p/A.java",
        "package p; class Desk { boolean ours(Object o) { return o instanceof Frame; } }");
    partial.learn(file);
    Inspection inspection = rule.inspection(partial);
    inspection.visit(file);
    assertEquals(List.of("unread"), inspection.findings().stream().map(this::read).toList());
  }

  private static final String KINDS = "package p; enum Kind { CHOICE, SWITCH, NUMBER }"
      + " class Knob { Kind kind; Kind kind() { return kind; } }";

  @Test
  void casesThatOnlyAnswerAValueAreDataOfTheConstant() {
    List<Finding> found = found(new SwitchDispatch(), KINDS,
        "package p; class Rows { String label(Knob knob) { switch (knob.kind()) { case CHOICE: return \"one of\";"
            + " case SWITCH: return \"on or off\"; default: return \"how much\"; } }"
            + " int width(Kind kind) { return switch (kind) { case CHOICE -> 3; case SWITCH -> 1; case NUMBER -> 2; }; } }");
    assertEquals("variation-as-data: every case only answers a value per constant of `Kind`, asked of `knob`",
        read(found.get(0)));
    assertEquals("variation-as-data: every case only answers a value per constant of `Kind`", read(found.get(1)));
    assertEquals(Optional.of("a field or a method on the enum or type, answered by each case"),
        found.get(0).reading().map(Reading::then));
  }

  @Test
  void casesThatDoDifferentWorkAreBehaviourOfTheConstant() {
    List<Finding> found = found(new SwitchDispatch(), KINDS,
        "package p; class Rows { void show(Kind kind) { switch (kind) { case CHOICE: open(); list(); break;"
            + " case SWITCH: toggle(); break; default: slide(); } } void open() {} void list() {} void toggle() {}"
            + " void slide() {} }");
    assertEquals("variation-as-data: the cases do different work per constant of `Kind`", read(found.get(0)));
    assertTrue(found.get(0).reading().orElseThrow().then().startsWith("subtypes, or a body per constant"));
  }

  @Test
  void anEnumThatDecidesOverItsOwnConstants() {
    List<Finding> found = found(new SwitchDispatch(),
        "package p; enum Kind { CHOICE, SWITCH; int width() { switch (this) { case CHOICE: return 3;"
            + " default: return 1; } } }");
    assertEquals("variation-as-data: `Kind` decides over its own constants: every case only answers a value per "
        + "constant of `Kind`", read(found.get(0)));
  }

  @Test
  void branchesThatReassignWhatTheyTestAreAState() {
    List<Finding> found = found(new SwitchDispatch(),
        "package p; enum Phase { IDLE, RUNNING, DONE } class Job { Phase phase; void step() { switch (phase) {"
            + " case IDLE: phase = Phase.RUNNING; break; case RUNNING: phase = Phase.DONE; break; default: } } }");
    assertEquals("state-without-an-object: the branches reassign `phase`, the value they test", read(found.get(0)));
  }

  @Test
  void casesThatAreTextsOrNumbersAreAValueWithoutAType() {
    List<Finding> found = found(new ConditionalChain(),
        "package p; class Core { boolean turbo; void set(String option, Object value) {"
            + " if (option.equals(\"turbo\")) { turbo = true; } else if (option.equals(\"speed\")) { turbo = false; }"
            + " else if (\"mute\".equals(option)) { turbo = false; } else { turbo = true; } } }");
    assertEquals("value-without-a-type: the cases are texts, compared with `option`", read(found.get(0)));
    List<Finding> numbers = found(new SwitchDispatch(),
        "package p; class Core { int f(int code) { switch (code) { case 1: return 10; case 2: return 20;"
            + " default: return 0; } } }");
    assertEquals("value-without-a-type: the cases are numbers, compared with `code`", read(numbers.get(0)));
  }

  @Test
  void armsThatTestWhetherSomethingIsThere() {
    List<Finding> found = found(new ConditionalChain(),
        "package p; class Core { Object line; int f() { if (line == null) { return 0; } else if (line != null) {"
            + " return 1; } else { return 2; } } }");
    assertEquals("state-without-an-object: every arm tests whether `line` is there", read(found.get(0)));
  }

  @Test
  void casesThatAreTypes() {
    List<Finding> found = found(new SwitchDispatch(), SHAPES,
        "package p; class Painter { int sides(Shape s) { return switch (s) { case Square q -> 4; case Circle c -> 0;"
            + " default -> 1; }; } }");
    assertEquals("dispatch-from-outside: the cases are types, told apart over `s`", read(found.get(0)));
  }

  @Test
  void aTypeCodeThatIsALibrarys() {
    List<Finding> found = found(new TypeCodeDispatch(),
        "package p; class Walk { boolean element(org.w3c.dom.Node n) {"
            + " return n.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE; } }");
    assertEquals("concept-without-a-class: `getNodeType()` is not a method of this project: what it answers is a "
        + "library's", read(found.get(0)));
  }

  @Test
  void constantsThatBelongToNoEnum() {
    List<Finding> found = found(new SwitchDispatch(),
        "package p; class Modes { static final int FAST = 1; static final int SLOW = 2; int f(int mode) {"
            + " switch (mode) { case FAST: return 10; case SLOW: return 20; default: return 0; } }"
            + " int key(java.awt.event.KeyEvent e) { switch (e.getKeyCode()) { case VK_UP: return 1;"
            + " case VK_DOWN: return 2; default: return 0; } } }");
    assertEquals("value-without-a-type: the cases are constants (`FAST`, `SLOW`) of no enum: plain values with names",
        read(found.get(0)));
    assertEquals("concept-without-a-class: `getKeyCode()` is not a method of this project: what it answers is a "
        + "library's", read(found.get(1)));
  }

  @Test
  void aChainOfUnrelatedConditionsIsNotRead() {
    List<Finding> found = found(new ConditionalChain(),
        "package p; class Core { int f(int a, int b) { if (a > b) { return 1; } else if (b > 10) { return 2; }"
            + " else { return 3; } } }");
    assertEquals(List.of("unread"), found.stream().map(this::read).toList());
  }

  @Test
  void placesThatDecideOverTheSameConstants() {
    List<Finding> found = found(new EnumDecidedOutside(), "package p; enum Dock { TOP, BOTTOM, LEFT, RIGHT }",
        "package p; class Frame { Dock side; boolean flat() { return side == Dock.TOP || side == Dock.BOTTOM; }"
            + " boolean wide() { return side == Dock.TOP || side == Dock.BOTTOM; } }");
    assertEquals("variation-as-data: it asks whether `side` is one of some constants of `Dock`",
        read(found.get(0)));
  }

  @Test
  void aLocalWorkedOutCaseByCaseIsNotAState() {
    List<Finding> found = found(new SwitchDispatch(),
        "package p; class Pages { int page(int ramPage) { switch (ramPage) { case 3: ramPage = 5; break;"
            + " case 4: ramPage = 2; break; default: ramPage = 0; } return ramPage; } }");
    assertEquals("value-without-a-type: the cases are numbers, compared with `ramPage`", read(found.get(0)));
  }

  @Test
  void theEnumIsTheOneItsOwnerDeclares() {
    List<Finding> found = found(new SwitchDispatch(),
        "package p; enum Model { K48, K128, PLUS3, PLUS2 } enum Code { K48, K128, PLUS3 }"
            + " class Machine { Code code; Model model; }",
        "package p; class Maker { int pages(Machine machine) { return switch (machine.code) { case K48 -> 1;"
            + " case K128 -> 8; case PLUS3 -> 8; }; }"
            + " int roms(Machine machine) { return switch (machine.model) { case K48 -> 1; case K128 -> 2;"
            + " default -> 4; }; } }");
    assertEquals("variation-as-data: every case only answers a value per constant of `Code`, asked of `machine`",
        read(found.get(0)));
    assertEquals("variation-as-data: every case only answers a value per constant of `Model`, asked of `machine`",
        read(found.get(1)));
  }

  @Test
  void aProcedureBelongsToTheObjectItIsHanded() {
    List<Finding> found = found(new NonPrivateStaticMethod(List.of("of")),
        "package p; class Speccy {} class Tools { static void reset(Speccy speccy, int times) {}"
            + " static int clamp(int value, int top) { return value; } static Object now() { return null; }"
            + " static void show(javax.swing.JLabel label) {} }");
    assertEquals(List.of("behaviour-not-on-owner: it takes `Speccy speccy`, an object of this project",
        "value-without-a-type: it takes only plain values: int value, int top", "unread", "unread"),
        found.stream().map(this::read).toList());
  }

  @Test
  void aRuleThatSuspectsOneModelReadsAsThatModel() {
    List<Finding> found = found(new PublicMutableField(), "package p; class State { public int clock; }");
    assertEquals("behaviour-not-on-owner: callers change the field to get something done", read(found.get(0)));
  }

  @Test
  void aNullThatMeansNothingWasFoundOrNothingArrivedYet() {
    List<Finding> found = found(new NullReturn(),
        "package p; import java.util.*; class Shelf { List<String> books; Object line;"
            + " String titled(String title) { for (String book : books) { if (book.equals(title)) { return book; } }"
            + " return null; }"
            + " Object open() { if (line == null) { return null; } return line; }"
            + " Object broken() { try { return line; } catch (RuntimeException e) { return null; } } }");
    assertEquals(List.of("variation-as-data: the method looks for something in a loop, and this is the case of "
        + "finding none", "state-without-an-object: it is returned while `line` is not there yet", "unread"),
        found.stream().map(this::read).toList());
  }

  @Test
  void copiesInOneFamilyBelongInWhatTheyShare() {
    String shapes = "package p; interface Shape {} class Circle implements Shape { int sides() { return 0; } }"
        + " class Square implements Shape { int sides() { return 0; } }"
        + " class Dot implements Shape { int sides() { return 0; } }";
    assertEquals("shape-repeated: the 3 copies sit in types of one family, `Shape`",
        read(found(new ClonedMethods(), shapes).get(0)));
    String strangers = "package p; class A { int sides() { return 0; } } class B { int sides() { return 0; } }"
        + " class C { int sides() { return 0; } }";
    assertEquals("concept-without-a-class: the 3 copies sit in types with nothing of the project in common",
        read(found(new ClonedMethods(), strangers).get(0)));
  }
}
