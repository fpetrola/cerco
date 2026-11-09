package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.SourceFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** The Java files under some source roots, minus the generated ones, named relative to the gated root. */
public final class Sources {
  private final Path root;
  private final List<PathMatcher> generated;

  public Sources(Path root, List<String> generatedGlobs) {
    this.root = root;
    this.generated = generatedGlobs.stream()
        .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob)).toList();
  }

  public List<SourceFile> under(List<Path> sourceRoots) {
    return sourceRoots.stream().filter(Files::isDirectory).flatMap(this::walk)
        .map(path -> new SourceFile(root, path))
        .filter(file -> generated.stream().noneMatch(matcher -> matcher.matches(root.relativize(file.absolute()))))
        .sorted(Comparator.comparing(SourceFile::name))
        .toList();
  }

  private Stream<Path> walk(Path sourceRoot) {
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      return paths.filter(path -> path.toString().endsWith(".java") && Files.isRegularFile(path)).toList().stream();
    } catch (IOException e) {
      throw new UncheckedIOException("could not list the sources under " + sourceRoot, e);
    }
  }
}
