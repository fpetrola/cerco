package com.fpetrola.cerco.rules;

/** Where a rule saw something: the file, the line, and the member it sits in, as the baseline names it. */
public interface Place {

  String file();

  int line();

  String member();
}
