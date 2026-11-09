package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.List;

/**
 * One run of one rule, fed the files one at a time so that only one tree is alive at once. A per-file rule answers
 * as it goes; a project-wide rule remembers what it needs and answers at the end.
 */
public interface Inspection {

  void visit(ParsedFile file);

  List<Finding> findings();
}
