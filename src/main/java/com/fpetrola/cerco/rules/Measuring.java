package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Measure;
import com.fpetrola.cerco.model.ParsedFile;
import java.util.List;

/** One run of one budget rule, fed the files one at a time. */
public interface Measuring {

  void visit(ParsedFile file);

  List<Measure> measures();
}
