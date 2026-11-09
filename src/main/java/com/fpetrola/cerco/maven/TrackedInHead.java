package com.fpetrola.cerco.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

/** Whether git's HEAD holds a path: a baseline that HEAD knows and the working tree lacks was deleted, not absent. */
public final class TrackedInHead {

  public boolean tracked(Path root, String relativePath) {
    FileRepositoryBuilder builder = new FileRepositoryBuilder().readEnvironment().findGitDir(root.toFile());
    if (builder.getGitDir() == null) {
      return false;
    }
    try (Repository repository = builder.build()) {
      ObjectId tree = repository.resolve("HEAD^{tree}");
      if (tree == null || repository.getWorkTree() == null) {
        return false;
      }
      String inRepo = repository.getWorkTree().toPath().toAbsolutePath().relativize(root.toAbsolutePath())
          .resolve(relativePath).toString().replace('\\', '/');
      try (TreeWalk walk = TreeWalk.forPath(repository, inRepo, tree)) {
        return walk != null;
      }
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the git history under " + root, e);
    }
  }
}
