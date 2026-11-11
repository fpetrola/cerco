package com.fpetrola.cerco.maven;

import com.fpetrola.cerco.engine.Engine;
import com.fpetrola.cerco.exemptions.Approval;
import com.fpetrola.cerco.exemptions.Loaded;
import com.fpetrola.cerco.hooks.Terminal;
import java.io.Console;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * A person signs the exemptions and the configuration as they stand, and accepts what only a person can about cerco
 * itself (a rule gone, defaults changed, findings a changed rule stopped seeing). It insists on a terminal: an
 * agent's shell has none, which is the point.
 */
@Mojo(name = "approve", aggregator = true)
public final class ApproveMojo extends AbstractCercoMojo {

  @Override
  protected void run() throws MojoFailureException {
    if (!new Terminal().interactive()) {
      throw new MojoFailureException("cerco: approval needs a person at a terminal. This shell has none, so this is "
          + "not the place: fix the findings, and if an exemption is really warranted, ask a human to run "
          + "`mvn cerco:approve` themselves.");
    }
    Engine engine = engine();
    Loaded loaded = engine.exemptionsFile().load();
    if (!loaded.problems().isEmpty()) {
      throw new MojoFailureException("cerco: the exemptions cannot be approved as they are:\n  - "
          + String.join("\n  - ", loaded.problems()));
    }
    Console console = System.console();
    console.printf("%ncerco: you are about to sign%n");
    loaded.exemptions().forEach(exemption -> console.printf("  %s%n", exemption.describe()));
    if (loaded.exemptions().isEmpty()) {
      console.printf("  (no exemptions)%n");
    }
    console.printf("  configuration: %s%n", settings().isDefault() ? "defaults" : settings().fingerprint());
    engine.witness().problems().forEach(problem -> console.printf("  and accept: %s%n", problem));
    List<String> awaiting = engine.autopsies().settle().awaiting();
    awaiting.forEach(id -> console.printf("  and file, as no rule could catch it: autopsies/pending/%s.md%n", id));
    String answer = Optional.ofNullable(console.readLine("Type 'yes' to approve: ")).orElse("");
    if (!answer.strip().equalsIgnoreCase("yes")) {
      throw new MojoFailureException("cerco: not approved, nothing was written to " + engine.approvalFile()
          + " (the answer has to be exactly 'yes')");
    }
    Approval.of(loaded.digest(), Optional.of(engine.configDigest()).filter(digest -> !settings().isDefault()),
        approver(), LocalDate.now())
        .write(engine.approvalFile());
    engine.witness().accept();
    engine.autopsies().accept(awaiting);
    console.printf("cerco: approved and recorded in %s; commit it%n", engine.approvalFile());
  }

  private String approver() {
    return Optional.ofNullable(System.getenv("GIT_AUTHOR_NAME")).orElse(System.getProperty("user.name", "someone"));
  }
}
