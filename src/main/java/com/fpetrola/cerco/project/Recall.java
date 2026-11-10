package com.fpetrola.cerco.project;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What the last whole run learnt about the project, kept on disk so that a run over the few files just edited can
 * still tell whose a type is. It only feeds the readings, which are advice: no verdict depends on it, so a stale
 * memory can make a hint poorer and nothing else.
 */
public final class Recall {
  private final Path file;
  private final ObjectMapper mapper = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public Recall(Path file) {
    this.file = file;
  }

  /** Writes down everything a project that saw every file declares. */
  public void keep(Project project) {
    try {
      Files.createDirectories(file.getParent());
      mapper.writeValue(file.toFile(), project.everything());
    } catch (IOException e) {
      throw new UncheckedIOException("could not keep what the project declares in " + file, e);
    }
  }

  /** Gives a project what was kept, when something was; answers with what went wrong when it could not be read. */
  public Optional<String> remind(Project project) {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      List<Declared> kept = mapper.readValue(file.toFile(),
          mapper.getTypeFactory().constructCollectionType(List.class, Declared.class));
      project.recall(kept);
      return Optional.empty();
    } catch (IOException unreadable) {
      return Optional.of("what the last whole run learnt about the project (" + file + ") could not be read, so "
          + "findings may say less about what is missing until the next whole run: " + unreadable.getMessage());
    }
  }
}
