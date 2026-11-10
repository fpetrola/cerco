package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.SourceFile;
import com.fpetrola.cerco.rules.Inspection;
import com.fpetrola.cerco.rules.Rule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rules run over a module with no baseline and no verdict, only to see what they find: how a candidate rule is
 * measured before it gates anything. Budget rules are judged against their default ceilings.
 */
public final class Survey {
  private final Feed feed = new Feed(new Parser(), new Generated());
  private final List<Rule> rules;

  public Survey(List<Rule> rules) {
    this.rules = List.copyOf(rules);
  }

  public Surveyed take(String module, List<SourceFile> files) {
    Map<String, Inspection> inspections = new LinkedHashMap<>();
    rules.forEach(rule -> inspections.put(rule.id(), rule.inspection()));
    AtomicInteger lines = new AtomicInteger();
    feed.each(files, file -> {
      lines.addAndGet(file.lines());
      inspections.values().forEach(inspection -> inspection.visit(file));
    });
    Map<String, List<Finding>> findings = new LinkedHashMap<>();
    inspections.forEach((id, inspection) -> findings.put(id, inspection.findings()));
    return new Surveyed(module, lines.get(), findings);
  }

  /** What the rules found in one module, and how long the module is. */
  public record Surveyed(String module, int lines, Map<String, List<Finding>> findings) {

    public List<Finding> of(String ruleId) {
      return findings.getOrDefault(ruleId, List.of());
    }

    public double perThousandLines(String ruleId) {
      return lines == 0 ? 0 : of(ruleId).size() * 1000.0 / lines;
    }
  }
}
