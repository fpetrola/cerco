package com.fpetrola.cerco.exemptions;

import com.fpetrola.cerco.model.Finding;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies exemptions to fresh findings. An exemption past its limit is a problem, and in a full run so is one that
 * covers nothing: an exemption protecting nothing is only hiding the next violation.
 */
public final class Exempting {
  private final List<Exemption> exemptions;
  private final Context context;

  public Exempting(List<Exemption> exemptions, Context context) {
    this.exemptions = exemptions;
    this.context = context;
  }

  public Applied apply(List<Finding> fresh, boolean fullScope) {
    Map<Exemption, Integer> matched = new LinkedHashMap<>();
    exemptions.forEach(exemption -> matched.put(exemption, 0));
    List<Finding> remaining = new ArrayList<>();
    List<Finding> exempted = new ArrayList<>();
    for (Finding finding : fresh) {
      Optional<Exemption> cover = exemptions.stream().filter(exemption -> exemption.matches(finding)).findFirst();
      cover.ifPresentOrElse(exemption -> {
        matched.merge(exemption, 1, Integer::sum);
        exempted.add(finding);
      }, () -> remaining.add(finding));
    }
    List<String> notes = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    matched.forEach((exemption, count) -> judge(exemption, count, fullScope, notes, problems));
    return new Applied(remaining, exempted, notes, problems);
  }

  private void judge(Exemption exemption, int count, boolean fullScope, List<String> notes, List<String> problems) {
    Verdict verdict = exemption.limit().judge(context.withMatched(count));
    String line = exemption.describe() + ": covers " + count + ", " + verdict.note();
    if (verdict.expired()) {
      problems.add("exemption " + line + "; remove it or let a human renew it");
      return;
    }
    if (fullScope && count == 0) {
      problems.add("exemption " + exemption.describe() + " covers nothing any more; remove it");
      return;
    }
    notes.add(line);
  }
}
