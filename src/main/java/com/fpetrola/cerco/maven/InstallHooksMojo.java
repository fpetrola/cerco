package com.fpetrola.cerco.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Wires the gate into Claude Code: a guard before every shell command, a quick run after every edit, an audit before
 * the agent may stop, and permission rules that keep the gate's own files out of the agent's hands.
 */
@Mojo(name = "install-hooks", aggregator = true)
public final class InstallHooksMojo extends AbstractCercoMojo {

  @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
  private PluginDescriptor plugin;

  @Override
  protected void run() throws MojoExecutionException {
    String java = ProcessHandle.current().info().command().orElse("java");
    String jar = plugin.getPluginArtifact().getFile().getAbsolutePath();
    String launcher = quoted(java) + " -cp " + quoted(jar) + " com.fpetrola.cerco.hooks.HookMain ";
    String maven = Optional.ofNullable(System.getProperty("maven.home"))
        .map(home -> " --mvn " + quoted(home + "/bin/mvn")).orElse("");
    List<HookSpec> hooks = List.of(
        new HookSpec("PreToolUse", Optional.of("Bash"), launcher + "guard", 20, "HookMain guard"),
        new HookSpec("PostToolUse", Optional.of("Edit|Write|MultiEdit"), launcher + "quick" + maven, 240,
            "HookMain quick"),
        new HookSpec("Stop", Optional.empty(), launcher + "audit" + maven, 900, "HookMain audit"));
    List<String> denies = List.of("Edit(.cerco/**)", "Edit(.claude/settings.json)", "Bash(*-Dcerco.skip*)");
    Path settings = root().resolve(".claude").resolve("settings.json");
    try {
      String existing = Files.exists(settings) ? Files.readString(settings, StandardCharsets.UTF_8) : "";
      Files.createDirectories(settings.getParent());
      Files.writeString(settings, new ClaudeSettings().merged(existing, hooks, denies), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new MojoExecutionException("could not write " + settings, e);
    }
    getLog().info("cerco: hooks installed in " + settings + ": guard on every Bash call, quick after every edit, "
        + "audit before the agent stops; .cerco/ and the settings are denied to the agent's editor. Commit it.");
  }

  private String quoted(String text) {
    return "\"" + text + "\"";
  }
}
