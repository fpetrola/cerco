package com.fpetrola.cerco.project;

/** A source file by its path from the gated root, which says which Maven module it belongs to. */
public record Location(String file) {

  /** What comes before the source root in the path; nothing for a project of one module. */
  public String module() {
    int sources = file.indexOf("/src/");
    return sources < 0 ? "" : file.substring(0, sources);
  }
}
