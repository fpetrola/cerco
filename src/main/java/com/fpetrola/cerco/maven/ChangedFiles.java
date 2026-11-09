package com.fpetrola.cerco.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/** The Java files git sees as changed, added or untracked: what a quick run looks at when no hook names a file. */
public final class ChangedFiles {

  public List<Path> under(Path root) {
    FileRepositoryBuilder builder = new FileRepositoryBuilder().readEnvironment().findGitDir(root.toFile());
    if (builder.getGitDir() == null) {
      return List.of();
    }
    try (Git git = Git.open(builder.getGitDir())) {
      Status status = git.status().call();
      Path workTree = git.getRepository().getWorkTree().toPath();
      Set<String> paths = new TreeSet<>();
      Stream.of(status.getModified(), status.getChanged(), status.getAdded(), status.getUntracked())
          .forEach(paths::addAll);
      return paths.stream().filter(path -> path.endsWith(".java"))
          .map(path -> workTree.resolve(path).toAbsolutePath()).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("could not open the git repository under " + root, e);
    } catch (GitAPIException e) {
      throw new IllegalStateException("could not ask git for the changed files under " + root, e);
    }
  }
}
