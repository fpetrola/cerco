package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.Reading;
import com.fpetrola.cerco.project.Project;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** A rule of one file whose sites are already its findings: nothing about the rest of the project changes them. */
public abstract class FoundInPlaceRule extends SiteRule<Finding> {

  @Override
  protected final Finding found(Finding site) {
    return site;
  }

  @Override
  protected final Function<Finding, Optional<Reading>> reader(List<Finding> sites, Project project) {
    return site -> Optional.empty();
  }
}
