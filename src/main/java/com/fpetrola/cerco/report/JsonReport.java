package com.fpetrola.cerco.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpetrola.cerco.engine.Outcome;
import com.fpetrola.cerco.model.Finding;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The same outcome as a JSON file under target/cerco, for anything that reads reports rather than consoles. */
public final class JsonReport {
  private final ObjectMapper mapper = new ObjectMapper();

  public void write(Outcome outcome, Path file) {
    Map<String, Object> document = new LinkedHashMap<>();
    document.put("label", outcome.label());
    document.put("green", outcome.green());
    document.put("parsed", outcome.parsed());
    document.put("frozen", outcome.frozen());
    document.put("solved", outcome.solved());
    document.put("millis", outcome.millis());
    document.put("fresh", findings(outcome.fresh()));
    document.put("exempted", findings(outcome.exempted()));
    document.put("owed", outcome.owed().stream().map(owed -> Map.<String, Object>of("id", owed.id(), "rule",
        owed.ruleId(), "form", owed.form(), "missing", owed.missing())).toList());
    document.put("notes", outcome.notes());
    document.put("problems", outcome.problems());
    try {
      Files.createDirectories(file.getParent());
      mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), document);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write the report " + file, e);
    }
  }

  private List<Map<String, Object>> findings(List<Finding> findings) {
    return findings.stream().map(finding -> Map.<String, Object>of(
        "rule", finding.rule(), "file", finding.file(), "line", finding.line(), "key", finding.key(),
        "detail", finding.detail())).toList();
  }
}
