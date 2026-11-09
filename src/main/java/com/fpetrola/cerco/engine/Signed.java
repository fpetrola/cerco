package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.exemptions.Approval;
import com.fpetrola.cerco.exemptions.Exemption;
import com.fpetrola.cerco.exemptions.ExemptionsFile;
import com.fpetrola.cerco.model.Finding;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The exemptions as they stand for this run: loaded, and valid only when a human's signature matches them and the
 * configuration. A valid exemption in a full run also releases what it covers from the baseline, so that a frozen
 * finding a human decided to exempt becomes an exempted one, visibly, and the baseline shrinks by it.
 */
final class Signed {
  private final List<Exemption> exemptions;
  private final List<String> problems;
  private final boolean releasing;

  Signed(ExemptionsFile file, Path approvalFile, String configDigest, boolean configIsDefault, boolean fullScope) {
    var loaded = file.load();
    List<String> found = new ArrayList<>(loaded.problems());
    found.addAll(Approval.read(approvalFile).problems(loaded.digest(), configDigest, configIsDefault));
    this.exemptions = loaded.exemptions();
    this.problems = List.copyOf(found);
    this.releasing = found.isEmpty() && fullScope;
  }

  List<Exemption> exemptions() {
    return exemptions;
  }

  List<String> problems() {
    return problems;
  }

  boolean valid() {
    return problems.isEmpty();
  }

  /** Whether a frozen entry of this rule in this file leaves the baseline to be covered by an exemption instead. */
  boolean releases(String ruleId, String file) {
    return releasing && exemptions.stream().anyMatch(exemption -> exemption.matches(new Finding(ruleId, file, 0,
        file, "")));
  }
}
