package com.fpetrola.cerco.rules;

/** Where a rule saw something: the file, the line, and the member it sits in, as the baseline names it. */
public interface Place {
  /** What a finding about something public adds: who uses it from outside is seen only through a test. */
  String FOR_OUTSIDERS = "; if a consumer outside this project uses it, write a test that uses it the same way";

  String file();

  int line();

  String member();
}
