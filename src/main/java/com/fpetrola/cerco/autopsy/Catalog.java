package com.fpetrola.cerco.autopsy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The autopsies done, one markdown file each in the project's autopsies/ directory, and the forms still being
 * written in autopsies/pending. Kept in git: it is the record of why the code had the shape it had, and the list of
 * rules the project keeps asking for.
 */
public final class Catalog {
  private static final String MD = ".md";
  private final Path dir;

  public Catalog(Path dir) {
    this.dir = dir;
  }

  public Path pending(String id) {
    return dir.resolve("pending").resolve(id + MD);
  }

  public boolean holds(String id) {
    return Files.exists(dir.resolve(id + MD));
  }

  /** Every finished autopsy by id. */
  public Map<String, Form> done() {
    Map<String, Form> forms = new TreeMap<>();
    if (!Files.isDirectory(dir)) {
      return forms;
    }
    try (Stream<Path> files = Files.list(dir)) {
      files.filter(file -> file.getFileName().toString().endsWith(MD)).sorted()
          .forEach(file -> forms.put(idOf(file), Form.parse(read(file))));
    } catch (IOException e) {
      throw new UncheckedIOException("could not list the autopsies in " + dir, e);
    }
    return forms;
  }

  public List<String> causes() {
    return done().values().stream().map(form -> normalized(form.section(Form.CAUSE))).toList();
  }

  public String normalized(String text) {
    return text.toLowerCase().replaceAll("\\s+", " ").strip();
  }

  /** Moves a finished form out of pending into the catalog, where it stays. */
  public void file(String id) {
    try {
      Files.move(pending(id), dir.resolve(id + MD), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException("could not file the autopsy " + id, e);
    }
  }

  public String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the autopsy " + file, e);
    }
  }

  public void write(Path file, String text) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, text, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write the autopsy " + file, e);
    }
  }

  private String idOf(Path file) {
    String name = file.getFileName().toString();
    return name.substring(0, name.length() - MD.length());
  }
}
