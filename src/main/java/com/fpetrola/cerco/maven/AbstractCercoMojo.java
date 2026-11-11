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

  /** Skips the gate. The agent cannot pass it: the guard hook refuses the property, and the build logs the skip. */
  @Parameter(property = "cerco.skip", defaultValue = "false")
  private boolean skip;

  /** Set by the Claude Code hooks. A red that only a human can clear (an unsigned exemptions file) then warns instead of blocking. */
  @Parameter(property = "cerco.hook", defaultValue = "false")
  private boolean hook;

  /** Lines a file may have before it is a finding (default 400). Changing it needs a signature (approve). */
  @Parameter
  private Integer fileCeiling;

  /** Lines a method or constructor may have (default 40). Changing it needs a signature (approve). */
  @Parameter
  private Integer methodCeiling;

  /** Type-name suffixes that name a utility class (default Util, Utils, Helper, Helpers, Manager, Impl, Common, Misc, Tools). */
  @Parameter
  private List<String> utilitySuffixes;

  /** Method-name prefixes that mark a static factory (default of, from, parse, create, valueOf, copyOf). */
  @Parameter
  private List<String> factoryPrefixes;

  /** Globs, relative to the root, of generated sources the gate ignores (default target, generated-sources, generated). */
  @Parameter
  private List<String> generated;

  /** How many exemptions .cerco/exemptions.yaml may hold at once (default 10). */
  @Parameter
  private Integer maxExemptions;

  /** Whether test sources are gated too (default false). */
  @Parameter
  private Boolean includeTests;

  /** Rule ids switched off for this project (default none). Needs a signature (approve), like every non-default. */
  @Parameter
  private List<String> disabledRules;

  /**
   * Whether a fixed finding owes an autopsy (default true): a form in autopsies/pending saying what model was missing
   * and which rule would catch it, without which the gate stays red. Switching it off needs a signature (approve).
   */
  @Parameter
  private Boolean autopsies;

  /**
   * Rules proposed by filed autopsies that a person decided not to build (default none): until a rule proposed is
   * in the running cerco or declined here, the gate stays red. Declining needs a signature (approve).
   */
  @Parameter
  private List<String> declinedRules;

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
        listOr(disabledRules, defaults.disabledRules()),
        Optional.ofNullable(autopsies).orElse(defaults.autopsies()),
        listOr(declinedRules, defaults.declinedRules()));
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

  /** The tests of the reactor: never judged, heard by the rules that ask who uses what. */
  protected List<SourceFile> reactorTests() {
    return new Sources(root(), settings().generatedGlobs()).under(session.getProjects().stream()
        .flatMap(module -> module.getTestCompileSourceRoots().stream()).map(Path::of).toList());
  }

  protected List<SourceFile> reactorSources() {
    return session.getProjects().stream().flatMap(module -> sourcesOf(module).stream()).toList();
  }

  protected void failIfBaselineDeleted(Engine engine) throws MojoFailureException {
    if (!engine.store().exists() && new GitTree(root()).tracksInHead(".cerco/baseline/frozen")) {
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
    String text = new ConsoleReport(engine.rules(), engine.autopsies().modelsByRule()).render(outcome);
    if (outcome.green()) {
      getLog().info(text.strip());
      return;
    }
    if (hook && outcome.fresh().isEmpty() && outcome.owed().isEmpty()) {
      getLog().warn(text.strip());
      getLog().warn("cerco: nothing here is the agent's to fix, so the hook lets this pass; the build will not "
          + "until a human signs with `mvn cerco:approve`");
      return;
    }
    getLog().error(text);
    throw new MojoFailureException("cerco: " + outcome.fresh().size() + " new finding(s), "
        + outcome.owed().size() + " autopsy(ies) owed and " + outcome.problems().size() + " problem(s) in "
        + outcome.label() + "; the report is above");
  }
}
