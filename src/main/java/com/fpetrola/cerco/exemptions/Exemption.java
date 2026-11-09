package com.fpetrola.cerco.exemptions;

import com.fpetrola.cerco.model.Finding;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/** One human decision: this rule does not apply to these files, for this reason, until this limit. */
public record Exemption(int ordinal, String rule, String target, String reason, String category, Limit limit) {

  public boolean matches(Finding finding) {
    return finding.rule().equals(rule) && matcher().matches(Path.of(finding.file()));
  }

  private PathMatcher matcher() {
    return FileSystems.getDefault().getPathMatcher("glob:" + target);
  }

  public String describe() {
    return "#" + ordinal + " " + rule + " on " + target + ", " + limit.describe() + " (" + category + ": " + reason + ")";
  }
}
