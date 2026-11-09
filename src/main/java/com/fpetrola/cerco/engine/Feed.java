package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.SourceFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parses files a small chunk at a time, in parallel within the chunk, and hands them over one by one in name order
 * so that memory holds a megabyte or so of trees, never the whole project. A chunk is bounded by bytes, not files:
 * sixteen tiny files share one, a generated giant travels alone.
 */
public final class Feed {
  private static final int MAX_FILES = 16;
  private static final long MAX_BYTES = 768 * 1024;
  private final Parser parser;
  private final Generated generated;

  public Feed(Parser parser, Generated generated) {
    this.parser = parser;
    this.generated = generated;
  }

  /** Feeds every non-generated file to the visitor and answers how many it fed. */
  public int each(List<SourceFile> files, Consumer<ParsedFile> visitor) {
    int fed = 0;
    List<SourceFile> chunk = new ArrayList<>();
    long bytes = 0;
    for (SourceFile file : files) {
      long size = sizeOf(file);
      if (!chunk.isEmpty() && (chunk.size() == MAX_FILES || bytes + size > MAX_BYTES)) {
        fed += flush(chunk, visitor);
        bytes = 0;
      }
      chunk.add(file);
      bytes += size;
    }
    return fed + flush(chunk, visitor);
  }

  private int flush(List<SourceFile> chunk, Consumer<ParsedFile> visitor) {
    int fed = 0;
    for (ParsedFile file : parser.parseAll(List.copyOf(chunk))) {
      if (!generated.marks(file)) {
        visitor.accept(file);
        fed++;
      }
    }
    chunk.clear();
    return fed;
  }

  private long sizeOf(SourceFile file) {
    try {
      return Files.size(file.absolute());
    } catch (IOException e) {
      throw new UncheckedIOException("could not size " + file.absolute(), e);
    }
  }
}
