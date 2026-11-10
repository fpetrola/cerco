package com.fpetrola.cerco.autopsy;

/** The form an autopsy starts as: its sections, each with guidance inside a comment that counts as nothing. */
final class Template {

  String text(String id, String ruleId) {
    return "# Autopsy " + id + " (" + ruleId + ")\n\n"
        + "<!-- Findings went away. That is the symptom gone, not the cause understood. Read the code around the fix\n"
        + "and answer: what was missing from the model that let this shape appear, where else it shows, and what\n"
        + "rule would stop it next time. cerco stays red until every section is filled; then it files this form in\n"
        + "autopsies/ by itself. Places are written path:line, relative to the project root, and must exist. -->\n\n"
        + "## " + Form.SYMPTOM + "\n\n"
        + "## " + Form.CAUSE + "\n\n"
        + "<!-- Not what the rule says, but why the code was written that way: the concept, responsibility or\n"
        + "abstraction that was missing or in the wrong place. In your own words, not copied from another autopsy. -->\n\n"
        + "## " + Form.CONTEXT + "\n\n"
        + "<!-- At least two places you read, path:line, one of them outside the fixed file(s): callers, the\n"
        + "hierarchy, the data that flows in, the code that forced the shape. Say what each one showed. -->\n\n"
        + "## " + Form.ELSEWHERE + "\n\n"
        + "<!-- Other places (path:line) where the same missing model shows, even under a different rule or no rule\n"
        + "at all; they are the next fixes. Or `none` and how you looked. -->\n\n"
        + "## " + Form.PRINCIPLE + "\n\n"
        + "<!-- The design principle at stake: tell don't ask, command-query separation, single owner of a concept,\n"
        + "polymorphism over type tests, information hiding... -->\n\n"
        + "## " + Form.CANDIDATE + "\n\n"
        + "<!-- One of:\n"
        + "rule: <new-rule-id>  then what it would detect, what it would tell the agent, and an example path:line\n"
        + "none: why no deterministic check could catch this cause; a person files it with mvn cerco:approve, and\n"
        + "      while one waits every other autopsy proposes a rule\n"
        + "same-as: <id of a filed autopsy>  when one already proposed the rule; the sections above still describe\n"
        + "this case -->\n";
  }
}
