package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.ParsedFile;
import java.util.Map;
import java.util.TreeMap;

/** The digest of the code of each file a run read, by the file's name. */
final class Digests {
  private final Map<String, String> byFile = new TreeMap<>();

  void keep(ParsedFile file) {
    byFile.put(file.name(), file.digest());
  }

  Map<String, String> byFile() {
    return byFile;
  }
}
