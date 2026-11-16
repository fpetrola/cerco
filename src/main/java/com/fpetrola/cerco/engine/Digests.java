package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.exemptions.Digest;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.Map;
import java.util.TreeMap;

/** The digest of the code of each file a run read, by the file's name. */
final class Digests {
  private final Map<String, String> byFile = new TreeMap<>();

  private static final String GENERATED = "generated:";

  void keep(ParsedFile file) {
    byFile.put(file.name(), file.digest());
  }

  /** A file a tool wrote: kept apart, so that what it had frozen is not taken for fixed by hand. */
  void keepGenerated(ParsedFile file) {
    byFile.put(file.name(), GENERATED + file.digest());
  }

  boolean isGenerated(String file) {
    return byFile.getOrDefault(file, "").startsWith(GENERATED);
  }

  /** Whether a file reads now as it did when those digests were kept. */
  boolean sameAsIn(Map<String, String> before, String file) {
    return before.containsKey(file) && before.get(file).equals(byFile.get(file));
  }

  /** These digests over those kept before: the files read again replace their old ones. */
  Map<String, String> over(Map<String, String> before) {
    Map<String, String> files = new TreeMap<>(before);
    files.putAll(byFile);
    return files;
  }

  /** Whether a file kept in those digests was not read now: it left the project. */
  boolean lostSince(Map<String, String> before) {
    return !byFile.keySet().containsAll(before.keySet());
  }

  /** One digest of everything read. */
  String ofAll() {
    return Digest.of(byFile.toString()).hex();
  }
}
