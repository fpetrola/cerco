package com.fpetrola.cerco.maven;

import com.fpetrola.cerco.engine.Survey;
import com.fpetrola.cerco.engine.Survey.Surveyed;
import com.fpetrola.cerco.report.SurveyReport;
import com.fpetrola.cerco.rules.Rule;
import com.fpetrola.cerco.rules.Rules;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Measures rules module by module without touching the baseline: the candidates by default, or the rules named with
 * -Dcerco.rules=a,b. It is how a rule earns its place: it has to find real cases, and tell modules apart.
 */
@Mojo(name = "survey", aggregator = true, threadSafe = true)
public final class SurveyMojo extends AbstractCercoMojo {

  /** Ids of the rules to survey, adopted or candidate; the candidates when empty. */
  @Parameter(property = "cerco.rules")
  private List<String> rules;

  /** How many findings per rule and module the survey shows, to be judged by hand. */
  @Parameter(property = "cerco.sample", defaultValue = "10")
  private int sample;

  @Override
  protected void run() throws MojoFailureException {
    Rules catalogue = new Rules(settings());
    List<Rule> chosen = rules == null || rules.isEmpty() ? catalogue.candidates()
        : Stream.concat(catalogue.all().stream(), catalogue.candidates().stream())
            .filter(rule -> rules.contains(rule.id())).toList();
    if (chosen.isEmpty()) {
      throw new MojoFailureException("cerco: no rule to survey; known are " + catalogue.known());
    }
    Survey survey = new Survey(chosen);
    List<Surveyed> modules = session.getProjects().stream()
        .map(module -> survey.take(root().relativize(module.getBasedir().toPath().toAbsolutePath()).toString(),
            sourcesOf(module)))
        .filter(module -> module.lines() > 0).toList();
    System.out.print(new SurveyReport(chosen.stream().map(Rule::id).toList(), sample).render(modules));
  }
}
