package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.SourceFile;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.rules.Inspection;
import com.fpetrola.cerco.rules.Rule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules run over the reactor with no baseline and no verdict, only to see what they find, split by module: how a
 * candidate rule is measured before it gates anything. One pass over every file, so a project-wide rule sees the
 * whole project as it does in an audit. Budget rules are judged against their default ceilings.
 */
public final class Survey {
  private final Feed feed = new Feed(new Parser(), new Generated());
  private final List<Rule> rules;

  public Survey(List<Rule> rules) {
    this.rules = List.copyOf(rules);
  }

  /** Each module by name with its files, in the order to report them. */
  public List<Surveyed> take(Map<String, List<SourceFile>> modules) {
    Map<String, String> moduleOf = new HashMap<>();
    List<SourceFile> all = new ArrayList<>();
    modules.forEach((module, files) -> files.forEach(file -> {
      moduleOf.put(file.name(), module);
      all.add(file);
    }));
    Project project = new Project();
    Map<String, Inspection> inspections = new LinkedHashMap<>();
    rules.forEach(rule -> inspections.put(rule.id(), rule.inspection(project)));
    Map<String, Integer> lines = new HashMap<>();
    feed.each(all, file -> {
      project.learn(file);
      lines.merge(moduleOf.get(file.name()), file.lines(), Integer::sum);
      inspections.values().forEach(inspection -> inspection.visit(file));
    });
    project.markWhole();
    Map<String, Map<String, List<Finding>>> found = new HashMap<>();
    inspections.forEach((id, inspection) -> inspection.findings().forEach(finding ->
        found.computeIfAbsent(moduleOf.getOrDefault(finding.file(), ""), k -> new HashMap<>())
            .computeIfAbsent(id, k -> new ArrayList<>()).add(finding)));
    return modules.keySet().stream().map(module -> new Surveyed(module, lines.getOrDefault(module, 0),
        found.getOrDefault(module, Map.of()))).toList();
  }

  /** What the rules found in one module, and how long the module is. */
  public record Surveyed(String module, int lines, Map<String, List<Finding>> findings) {

    public List<Finding> of(String ruleId) {
      return findings.getOrDefault(ruleId, List.of());
    }

    /** How many of the module's findings, of every rule, read as that missing model. */
    public long readAs(MissingModel model) {
      return findings.values().stream().flatMap(List::stream)
          .filter(finding -> finding.reading().filter(reading -> reading.model() == model).isPresent()).count();
    }

    /** How many of the module's findings their rule could not read. */
    public long unread() {
      return findings.values().stream().flatMap(List::stream).filter(finding -> finding.reading().isEmpty()).count();
    }

    public double perThousandLines(String ruleId) {
      return lines == 0 ? 0 : of(ruleId).size() * 1000.0 / lines;
    }
  }
}
