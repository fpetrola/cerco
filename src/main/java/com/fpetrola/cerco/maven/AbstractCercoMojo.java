package com.fpetrola.cerco.maven;

import com.fpetrola.cerco.engine.Engine;
import com.fpetrola.cerco.engine.Outcome;
import com.fpetrola.cerco.engine.Sources;
import com.fpetrola.cerco.model.Settings;
import com.fpetrola.cerco.model.SourceFile;
import com.fpetrola.cerco.report.ConsoleReport;
import com.fpetrola.cerco.report.JsonReport;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** What every gate goal shares: the session, the settings, the root, the engine, and how a verdict is reported. */
public abstract class AbstractCercoMojo extends AbstractMojo {

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  protected MavenSession session;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  protected MavenProject project;

  @Parameter(property = "cerco.skip", defaultValue = "false")
  private boolean skip;

  @Parameter
  private Integer fileCeiling;

  @Parameter
  private Integer methodCeiling;

  @Parameter
  private List<String> utilitySuffixes;

  @Parameter
  private List<String> factoryPrefixes;

  @Parameter
  private List<String> generated;

  @Parameter
  private Integer maxExemptions;

  @Parameter
  private Boolean includeTests;

  @Parameter
  private List<String> disabledRules;

  @Override
  public final void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().warn("cerco: skipped by cerco.skip, the design gate did not run");
      return;
    }
    run();
  }

  protected abstract void run() throws MojoExecutionException, MojoFailureException;

  protected Settings settings() {
    Settings defaults = Settings.DEFAULTS;
    return new Settings(
        Optional.ofNullable(fileCeiling).orElse(defaults.fileCeiling()),
        Optional.ofNullable(methodCeiling).orElse(defaults.methodCeiling()),
        listOr(utilitySuffixes, defaults.utilitySuffixes()),
        listOr(factoryPrefixes, defaults.factoryPrefixes()),
        listOr(generated, defaults.generatedGlobs()),
        Optional.ofNullable(maxExemptions).orElse(defaults.maxExemptions()),
        Optional.ofNullable(includeTests).orElse(defaults.includeTests()),
        listOr(disabledRules, defaults.disabledRules()));
  }

  private List<String> listOr(List<String> configured, List<String> fallback) {
    return configured == null || configured.isEmpty() ? fallback : List.copyOf(configured);
  }

  protected Path root() {
    return new RootLocator().locate(project.getBasedir().toPath().toAbsolutePath(),
        Path.of(session.getExecutionRootDirectory()).toAbsolutePath());
  }

  protected Engine engine() {
    return new Engine(settings(), root(), session.getTopLevelProject().getVersion(), Clock.systemDefaultZone());
  }

  protected List<SourceFile> sourcesOf(MavenProject module) {
    Stream<String> roots = module.getCompileSourceRoots().stream();
    if (settings().includeTests()) {
      roots = Stream.concat(roots, module.getTestCompileSourceRoots().stream());
    }
    return new Sources(root(), settings().generatedGlobs()).under(roots.map(Path::of).toList());
  }

  protected List<SourceFile> reactorSources() {
    return session.getProjects().stream().flatMap(module -> sourcesOf(module).stream()).toList();
  }

  protected void failIfBaselineDeleted(Engine engine) throws MojoFailureException {
    if (!engine.store().exists() && new TrackedInHead().tracked(root(), ".cerco/baseline/frozen")) {
      throw new MojoFailureException("cerco: the baseline under .cerco/baseline is in git's HEAD but not on disk. "
          + "It was deleted, and a fresh freeze would silently accept everything that is wrong today. Restore it "
          + "with `git checkout -- .cerco/baseline` and fix the findings instead.");
    }
  }

  protected void freezeIfAbsent(Engine engine) {
    if (!engine.store().exists()) {
      int frozen = engine.freeze(reactorSources());
      getLog().warn("cerco: no baseline existed, so the project was frozen as it stands: " + frozen
          + " findings and oversize measures across the reactor are now the baseline under .cerco/baseline. "
          + "Commit that directory. From now on anything new is red, and the baseline only shrinks.");
    }
  }

  protected void report(Engine engine, Outcome outcome) throws MojoFailureException {
    new JsonReport().write(outcome, root().resolve("target").resolve("cerco").resolve(outcome.label() + ".json"));
    String text = new ConsoleReport(engine.rules()).render(outcome);
    if (outcome.green()) {
      getLog().info(text.strip());
      return;
    }
    getLog().error(text);
    throw new MojoFailureException("cerco: " + outcome.fresh().size() + " new finding(s) and "
        + outcome.problems().size() + " problem(s) in " + outcome.label() + "; the report is above");
  }
}
