package com.fpetrola.cerco.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.surface.Reach;
import com.fpetrola.cerco.surface.Seal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What a class may offer when the surface is sealed: only what an interface promised. */
class UnpromisedMemberTest {
  private final Snippets snippets = new Snippets();

  private List<String> found(Path seal, String... sources) {
    try {
      java.nio.file.Files.writeString(seal.resolve("_root.txt"), "p.Shape interface\n");
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
    List<ParsedFile> files = new ArrayList<>();
    for (String source : sources) {
      files.add(snippets.parse("src/main/java/p/F" + files.size() + ".java", source));
    }
    return new UnpromisedMember(new Seal(seal), Reach.ALL).inspect(files).stream().map(Finding::detail).toList();
  }

  @Test
  void aClassKeepsPromisesAndMakesNone(@TempDir Path seal) {
    List<String> found = found(seal,
        "package p; public interface Shape { int area(); }",
        "package p; interface Square extends Shape { }",
        "package p; class Box implements Square { Box() {} public int area() { return 1; } @Override public String"
            + " toString() { return \"\"; } int side() { return 1; } private int hidden() { return 2; }"
            + " public static Box of() { return new Box(); } int size; private class Inner { public void x() {} } }");
    assertEquals(List.of(
        "Box.Box of() is an entrance to the module the seal does not have: propose it, or keep it out of reach",
        "field Box.[size] is not private: a class shows what it does, not what it holds",
        "Box.side (0 parameters) is promised by no interface Box implements"), found);
  }

  @Test
  void aRecordsComponentsNeedAnInterfaceThatAsksForThem(@TempDir Path seal) {
    List<String> found = found(seal,
        "package p; public interface Point { int column(); int row(); }",
        "package p; record Cell(int column, int row) implements Point { }",
        "package p; record Stroke(String key, boolean ctrl) { }");
    assertEquals(List.of("Stroke.ctrl (0 parameters) is promised by no interface Stroke implements",
        "Stroke.key (0 parameters) is promised by no interface Stroke implements"), found);
  }

  @Test
  void aFunctionTypeOfTheJdkPromisesNothing(@TempDir Path seal) {
    List<String> found = found(seal,
        "package p; import java.util.function.Consumer; public interface Drawing { void draw(String s); }",
        "package p; import java.util.function.Consumer; class Frame implements Consumer<String> {"
            + " @Override public void accept(String s) { } }",
        "package p; class Task implements Runnable { @Override public void run() { } }",
        "package p; class Shadow implements Drawing { @Override public void draw(String s) { Runnable r = () -> { }; } }",
        "package p; class Sorted implements Comparable<Sorted> { @Override public int compareTo(Sorted o) { return 0; } }");
    assertEquals(List.of("Frame.accept (1 parameters) is promised by no interface Frame implements",
        "Task.run (0 parameters) is promised by no interface Task implements"), found);
  }
}
