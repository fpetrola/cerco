package com.fpetrola.cerco.autopsy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An autopsy as markdown: a title, then sections under "## " headings. What sits inside HTML comments is the
 * template's guidance and counts as nothing, so a form left as it was given is empty.
 */
public final class Form {
  public static final String SYMPTOM = "Symptom";
  public static final String CAUSE = "Cause";
  public static final String CONTEXT = "Context";
  public static final String ELSEWHERE = "Same cause elsewhere";
  public static final String PRINCIPLE = "Principle";
  public static final String CANDIDATE = "Candidate rule";

  private final String title;
  private final Map<String, String> sections;

  private Form(String title, Map<String, String> sections) {
    this.title = title;
    this.sections = sections;
  }

  public static Form parse(String text) {
    String bare = text.replaceAll("(?s)<!--.*?-->", "");
    String title = bare.lines().filter(line -> line.startsWith("# ")).findFirst().orElse("# ").substring(2).strip();
    Map<String, String> sections = new LinkedHashMap<>();
    String current = "";
    StringBuilder body = new StringBuilder();
    for (String line : bare.split("\n", -1)) {
      if (line.startsWith("## ")) {
        sections.put(current, body.toString().strip());
        current = line.substring(3).strip();
        body.setLength(0);
      } else {
        body.append(line).append('\n');
      }
    }
    sections.put(current, body.toString().strip());
    return new Form(title, sections);
  }

  public String title() {
    return title;
  }

  /** What was written under a heading, guidance left out; empty when the heading is missing. */
  public String section(String heading) {
    return sections.getOrDefault(heading, "");
  }
}
