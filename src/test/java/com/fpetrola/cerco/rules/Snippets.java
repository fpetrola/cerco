package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.engine.Parser;
import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.SourceFile;
import java.nio.file.Path;
import java.util.List;

/** Parses Java given as text, as if it lived at a path under a pretend root. */
final class Snippets {
  private final Path root = Path.of("/project");
  private final Parser parser = new Parser();

  ParsedFile parse(String name, String code) {
    return parser.parse(new SourceFile(root, root.resolve(name)), code);
  }

  List<Finding> findings(Rule rule, String code) {
    return rule.inspect(List.of(parse("src/main/java/p/A.java", code)));
  }
}
