package com.fpetrola.cerco.maven;

import com.fpetrola.cerco.engine.Engine;
import com.fpetrola.cerco.engine.Scope;
import com.fpetrola.cerco.hooks.HookInput;
import com.fpetrola.cerco.hooks.StdinText;
import com.fpetrola.cerco.hooks.Terminal;
import com.fpetrola.cerco.model.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * The file just edited, or the files git sees as changed (after a shell command that wrote, or for a person at a
 * terminal): the per-file rules only, fast enough to run after every edit. The hook's JSON arrives on stdin.
 */
@Mojo(name = "quick", aggregator = true, threadSafe = true)
public final class QuickMojo extends AbstractCercoMojo {

  @Override
  protected void run() throws MojoFailureException {
    Engine engine = engine();
    failIfBaselineDeleted(engine);
    List<SourceFile> files = reactorSources().stream().filter(candidates()::contains).toList();
    if (files.isEmpty()) {
      getLog().info("cerco: nothing to check");
      return;
    }
    report(engine, engine.run(new Scope("quick", files, false)));
  }

  private Set<SourceFile> candidates() {
    List<Path> changed = new Terminal().interactive() ? new ChangedFiles().under(root()) : fromHook();
    return changed.stream().map(path -> new SourceFile(root(), path.toAbsolutePath().normalize()))
        .collect(Collectors.toSet());
  }

  private List<Path> fromHook() {
    HookInput input = HookInput.parse(new StdinText(System.in).text());
    if (input.blank() || input.filePath().isEmpty()) {
      return new ChangedFiles().under(root());
    }
    return List.of(Path.of(input.filePath()));
  }
}
