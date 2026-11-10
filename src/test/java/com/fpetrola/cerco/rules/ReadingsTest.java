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
}
