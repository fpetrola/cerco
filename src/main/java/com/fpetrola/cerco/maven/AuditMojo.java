package com.fpetrola.cerco.maven;

import com.fpetrola.cerco.engine.Engine;
import com.fpetrola.cerco.engine.Scope;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/** The whole reactor at once: every rule, including the project-wide ones, and stale exemptions. */
@Mojo(name = "audit", aggregator = true, threadSafe = true)
public final class AuditMojo extends AbstractCercoMojo {

  @Override
  protected void run() throws MojoFailureException {
    Engine engine = engine();
    failIfBaselineDeleted(engine);
    freezeIfAbsent(engine);
    report(engine, engine.run(new Scope("reactor", reactorSources(), true)));
  }
}
