package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.MissingModel;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;
import java.util.List;
import java.util.Set;

/**
 * The same text compared (with equals, or as a case) in two or more members of the project: a case of the domain
 * that has only a spelling, which every place has to write the same. Candidate from the plan of model smells.
 */
public final class NamedByText extends RepeatedRule<NamedByText.Site> {
  private static final int MEMBERS = 2;
  private static final Set<String> COMPARISONS = Set.of("equals", "equalsIgnoreCase");

  /** One comparison with a text: where, in which member, and the text. */
  public record Site(String file, int line, String member, String text) implements Place {
  }

  public NamedByText() {
    super(MEMBERS);
  }

  @Override
  public String id() {
    return "named-by-text";
  }

  @Override
  public String principle() {
    return "a case of the domain has a type: its name is written once, where it is declared";
  }

  @Override
  public String description() {
    return "the same text is compared (equals, a case) in " + MEMBERS + " or more members: it names a case of the "
        + "domain that has no type, and a misspelling in one place is a case nobody handles";
  }

  @Override
  public String remedy() {
    return "an enum or a type for what the text names, which owns what the comparisons decide; the text is read "
        + "into it once, at the edge";
  }

  @Override
  public Diagnosis diagnosis() {
    return new Diagnosis("a constant holding the same text, compared in the same places",
        new Hypothesis(MissingModel.VALUE_WITHOUT_A_TYPE,
            "a text names a case of the domain in several places",
            "an enum or a type for it, owning what the comparisons decide"));
  }

  @Override
  protected void gather(ParsedFile file, CompilationUnit unit, List<Site> sites) {
    unit.findAll(StringLiteralExpr.class, this::compared).stream().filter(text -> named(text.getValue()))
        .forEach(text -> sites.add(new Site(file.name(), line(text), enclosing.of(text), text.getValue())));
  }

  /** A literal that is an operand of equals, or the label of a case. */
  private boolean compared(StringLiteralExpr text) {
    boolean operand = enclosing.parentOf(text, MethodCallExpr.class)
        .filter(call -> COMPARISONS.contains(call.getNameAsString()) && call.getArguments().size() == 1).isPresent();
    return operand || enclosing.parentOf(text, SwitchEntry.class).isPresent();
  }

  /** A text long enough to be a name and made of letters: not a separator, a blank or a digit. */
  private boolean named(String text) {
    return text.length() >= 2 && text.chars().anyMatch(Character::isLetter);
  }

  @Override
  protected String what(Site place, Project project) {
    return "\"" + place.text() + "\"";
  }

  @Override
  protected Finding found(Site place, Shared<Site> shared, Project project) {
    return finding(place, shared.what(), clipped(shared.what()) + " is compared in " + place.member() + "; "
        + shared.members() + " members compare with it");
  }
}
