package com.fpetrola.cerco.maven;

import com.fpetrola.cerco.engine.Engine;
import com.fpetrola.cerco.engine.Scope;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The gate in the build: runs the per-file rules over this module at verify, freezing the whole reactor first if no
 * baseline exists yet. A single-module project at the root also gets the project-wide rules here.
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public final class CheckMojo extends AbstractCercoMojo {

  @Override
  protected void run() throws MojoFailureException {
    Engine engine = engine();
    failIfBaselineDeleted(engine);
    freezeIfAbsent(engine);
    if ("pom".equals(project.getPackaging())) {
      return;
    }
    boolean full = project.getBasedir().toPath().toAbsolutePath().equals(root()) && session.getProjects().size() == 1;
    report(engine, engine.run(new Scope(project.getArtifactId(), sourcesOf(project), full)));
  }
}
