package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Project;
import com.github.javaparser.ast.CompilationUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A rule whose findings live each in one file, so it runs on a single edit, but whose reading needs the whole
 * project: it keeps a site per finding, with what it saw there, and reads them all when the run ends.
 *
 * @param <S> what the rule keeps of each place it found
 */
public abstract class SiteRule<S> extends GatheringRule<List<S>> {

  /** The places of one file this rule catches, each with what will be needed to read it. */
  protected abstract List<S> sites(ParsedFile file, CompilationUnit unit);

  /** The finding of a site, as the baseline knows it. */
  protected abstract Finding found(S site);

  /** How each site is read, given all of them and the whole project. */
  protected abstract Function<S, Optional<Reading>> reader(List<S> sites, Project project);

  @Override
  protected final List<S> start() {
    return new ArrayList<>();
  }

  @Override
  protected final void gather(ParsedFile file, CompilationUnit unit, List<S> sites) {
    sites.addAll(sites(file, unit));
  }

  @Override
  protected final List<Finding> conclude(List<S> sites, Project project) {
    Function<S, Optional<Reading>> reader = reader(sites, project);
    return sites.stream().map(site -> reader.apply(site).map(found(site)::read).orElse(found(site))).toList();
  }
}
