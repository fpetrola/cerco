package com.fpetrola.cerco.surface;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What a person signed of each module's surface: .cerco/surface/<module>.txt, one signature per line, sorted, so a
 * diff of it reads as a change of design. A project that was never sealed adopts the seal as its modules stand; once
 * it has one, a module without its file starts empty, so a new module is new surface like any other.
 */
public final class Seal {
  private static final String ROOT_MODULE = "_root";
  private final Path directory;
  private boolean adopting;

  public Seal(Path directory) {
    this.directory = directory;
  }

  public boolean covers(String module) {
    return Files.isRegularFile(fileOf(module));
  }

  /**
   * The members offered that the seal lacks; none while the project waits for its first sealing. Asking it ends an
   * adoption: a module adopted from here on would be a module that dodged the seal.
   */
  public List<Member> unsigned(String module, List<Member> offered) {
    adopting = false;
    if (sealedFiles().isEmpty()) {
      return List.of();
    }
    Set<String> signed = read(module);
    return offered.stream().filter(member -> !signed.contains(member.signature())).toList();
  }

  /**
   * Seals the module as it stands, when the project was never sealed: how a project adopts the seal. Every module of
   * that first whole run is adopted, until the run asks what is unsigned.
   */
  public void adopt(String module, List<Member> offered) {
    if (!covers(module) && (adopting || sealedFiles().isEmpty())) {
      adopting = true;
      write(module, signatures(offered));
    }
  }

  /** Drops what the code no longer offers, and answers what was dropped, for the next signature to list. */
  public List<String> shrink(String module, List<Member> offered) {
    if (!covers(module)) {
      return List.of();
    }
    Set<String> signed = read(module);
    Set<String> kept = signatures(offered);
    List<String> dropped = signed.stream().filter(signature -> !kept.contains(signature)).toList();
    if (!dropped.isEmpty()) {
      signed.removeAll(dropped);
      write(module, signed);
    }
    return dropped;
  }

  private Set<String> signatures(List<Member> offered) {
    return offered.stream().map(Member::signature).collect(Collectors.toCollection(TreeSet::new));
  }

  private Path fileOf(String module) {
    return directory.resolve((module.isEmpty() ? ROOT_MODULE : module.replace('/', '.')) + ".txt");
  }

  private Set<String> read(String module) {
    if (!covers(module)) {
      return new TreeSet<>();
    }
    try {
      return Files.readAllLines(fileOf(module), StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank())
          .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the seal " + fileOf(module), e);
    }
  }

  private void write(String module, Set<String> signatures) {
    try {
      Files.createDirectories(directory);
      Files.write(fileOf(module), new ArrayList<>(signatures), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write the seal " + fileOf(module), e);
    }
  }

  private List<Path> sealedFiles() {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(file -> file.getFileName().toString().endsWith(".txt")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("could not list the seal " + directory, e);
    }
  }
}
