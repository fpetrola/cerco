package com.fpetrola.cerco.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fpetrola.cerco.engine.Parser;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.SourceFile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a module offers to others, at each reach, measured on small sources. */
class SurfacesTest {
  private final Path root = Path.of("/project");

  private ParsedFile parse(String name, String code) {
    return new Parser().parse(new SourceFile(root, root.resolve(name)), code);
  }

  private Surface surface(String... sources) {
    Surfaces surfaces = new Surfaces();
    for (int i = 0; i < sources.length; i++) {
      surfaces.visit("m", parse("m/src/main/java/p/F" + i + ".java", sources[i]));
    }
    return surfaces.of("m");
  }

  private List<String> signatures(Surface surface, Reach reach) {
    return surface.at(reach).stream().map(Member::signature).sorted().toList();
  }

  @Test
  void anInterfaceIsSurfaceAtEveryReach() {
    Surface surface = surface("package p; public interface Shown { void show(String text); }");
    List<String> expected = List.of("p.Shown interface", "p.Shown#show(String): void");
    assertEquals(expected, signatures(surface, Reach.INTERFACES));
    assertEquals(expected, signatures(surface, Reach.PUBLIC));
    assertEquals(expected, signatures(surface, Reach.ALL));
  }

  @Test
  void aClassIsSurfaceByHowOpenItIs() {
    Surface surface = surface("package p; public class Box implements Shown { public Box(int size) {}"
        + " @Override public void show(String text) {} public <T> T held(java.util.function.Supplier<T> s) { return null; }"
        + " void tidy() {} private void hidden() {} public static final int MAX = 3; }",
        "package p; class Helper { public void help() {} }");
    assertEquals(List.of(), signatures(surface, Reach.INTERFACES));
    assertEquals(List.of("p.Box#<init>(int)"), signatures(surface, Reach.PROMISES));
    assertEquals(List.of("<T> p.Box#held(java.util.function.Supplier<T>): T", "p.Box class", "p.Box#<init>(int)",
        "p.Box#MAX: int"), signatures(surface, Reach.PUBLIC));
    assertEquals(List.of("<T> p.Box#held(java.util.function.Supplier<T>): T", "p.Box class", "p.Box#<init>(int)",
        "p.Box#MAX: int", "p.Box#tidy(): void", "p.Helper class", "p.Helper#help(): void"),
        signatures(surface, Reach.ALL));
    assertEquals(1, surface.saying(Hint.FUNCTION));
  }

  @Test
  void recordsOfferTheirComponentsAndEnumsTheirConstants() {
    Surface surface = surface("package p; public record Point(int x, String label) { public int x() { return x; } }",
        "package p; public enum Mode { ON, OFF; Mode() {} }");
    assertEquals(List.of("p.Mode enum", "p.Mode#OFF", "p.Mode#ON", "p.Point record", "p.Point#label: String",
        "p.Point#x: int"), signatures(surface, Reach.ALL));
    assertEquals(1, surface.saying(Hint.TEXT));
  }

  @Test
  void thePartDeclaredByInterfacesAndWhatNobodyNames() {
    Surfaces surfaces = new Surfaces();
    surfaces.visit("m", parse("m/src/main/java/p/A.java",
        "package p; public interface Shown { void show(boolean loud); }"));
    surfaces.visit("m", parse("m/src/main/java/p/B.java",
        "package p; public class Use { public void run(Shown s) { s.show(true); } public void unused() {} }"));
    Surface surface = surfaces.of("m");
    assertEquals(1.0 / 3, surface.promised(), 1e-9);
    assertEquals(1, surface.saying(Hint.FLAG));
    assertEquals(List.of("p.Use#run(Shown): void", "p.Use#unused(): void"),
        surface.unnamed().stream().map(Member::signature).toList());
    surfaces.hear(parse("m/src/test/java/p/UseTest.java", "package p; class UseTest { void t() { new Use().run(null); } }"));
    assertEquals(List.of("p.Use#unused(): void"),
        surfaces.of("m").unnamed().stream().map(Member::signature).toList());
  }
}
