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
  private static final String DROPPED = "dropped.txt";
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
      Set<String> log = lines(directory.resolve(DROPPED));
      dropped.forEach(signature -> log.add(module + ": " + signature));
      write(directory.resolve(DROPPED), log);
    }
    return dropped;
  }

  /** Grows the module's seal by these signatures: what a person signed. It closes the list of what was dropped. */
  public void grow(String module, List<String> signatures) {
    Set<String> signed = read(module);
    signed.addAll(signatures);
    write(module, signed);
    try {
      Files.deleteIfExists(directory.resolve(DROPPED));
    } catch (IOException e) {
      throw new UncheckedIOException("could not clear " + directory.resolve(DROPPED), e);
    }
  }

  /** What left the seal since the last signature, `module: signature`, for the next one to list. */
  public List<String> dropped() {
    return List.copyOf(lines(directory.resolve(DROPPED)));
  }

  /** Whether the module's seal has no signature: never sealed, or sealed empty. */
  boolean holdsNothing(String module) {
    return read(module).isEmpty();
  }

  /** Whether some signature of the seal, of any module, says this: `Type#member`, or part of a signature. */
  boolean says(String fragment) {
    return sealedFiles().stream()
        .flatMap(file -> lines(file).stream()).anyMatch(signature -> signature.contains(fragment));
  }

  /** Whether this exact signature was signed before: in the seal of some module, or dropped since the last signature. */
  boolean had(String signature) {
    return sealedFiles().stream().anyMatch(file -> lines(file).contains(signature))
        || dropped().stream().anyMatch(gone -> gone.endsWith(": " + signature));
  }

  private Set<String> signatures(List<Member> offered) {
    return offered.stream().map(Member::signature).collect(Collectors.toCollection(TreeSet::new));
  }

  private Path fileOf(String module) {
    return directory.resolve((module.isEmpty() ? ROOT_MODULE : module.replace('/', '.')) + ".txt");
  }

  private Set<String> read(String module) {
    return lines(fileOf(module));
  }

  private void write(String module, Set<String> signatures) {
    write(fileOf(module), signatures);
  }

  private Set<String> lines(Path file) {
    if (!Files.isRegularFile(file)) {
      return new TreeSet<>();
    }
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank())
          .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the seal " + file, e);
    }
  }

  private void write(Path file, Set<String> lines) {
    try {
      Files.createDirectories(directory);
      Files.write(file, new ArrayList<>(lines), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write the seal " + file, e);
    }
  }

  private List<Path> sealedFiles() {
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files.filter(file -> file.getFileName().toString().endsWith(".txt")
          && !file.getFileName().toString().equals(DROPPED)).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("could not list the seal " + directory, e);
    }
  }
}
