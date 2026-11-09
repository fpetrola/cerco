package com.fpetrola.cerco.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fpetrola.cerco.engine.Parser;
import com.fpetrola.cerco.engine.Sources;
import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Settings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The gate's own sources pass every rule of the gate, with the defaults and no baseline. */
class SelfCheckTest {

  @Test
  void theGateObeysItsOwnRules() {
    Path root = Path.of("").toAbsolutePath();
    List<ParsedFile> parsed = new Parser().parseAll(
        new Sources(root, Settings.DEFAULTS.generatedGlobs()).under(List.of(root.resolve("src/main/java"))));
    List<Finding> findings = new Rules(Settings.DEFAULTS).all().stream()
        .flatMap(rule -> rule.inspect(parsed).stream()).sorted().toList();
    assertEquals(List.of(), findings.stream().map(f -> f.rule() + " " + f.file() + ":" + f.line() + " " + f.detail())
        .toList());
  }
}
