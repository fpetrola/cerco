package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.SourceFile;
import com.fpetrola.cerco.rules.Rule;
import com.fpetrola.cerco.rules.Rules;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What one run looks at. A full scope is the whole project: project-wide rules run, and an exemption that covers
 * nothing is stale. A partial scope is a module or the files just edited. The tests are never judged, only heard by
 * the rules that ask who uses what.
 */
public record Scope(String label, List<SourceFile> files, boolean full, List<SourceFile> tests) {

  /** A scope with no tests to hear. */
  public Scope(String label, List<SourceFile> files, boolean full) {
    this(label, files, full, List.of());
  }

  /** The rules that can answer over these files: every one over the whole project, the per-file ones over a part. */
  public List<Rule> rulesOf(Rules rules) {
    return full ? rules.all() : rules.perFile();
  }

  /** The names of the files looked at, as the baseline knows them. */
  public Set<String> names() {
    return files.stream().map(SourceFile::name).collect(Collectors.toSet());
  }
}
