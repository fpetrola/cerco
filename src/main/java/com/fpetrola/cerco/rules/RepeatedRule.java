package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.project.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A rule about the same thing done in several members of the project: one place may be fair, the same in several is
 * something its owner lacks. It keeps every place, and each one is a finding once enough members share it.
 *
 * @param <S> one place where the thing is done
 */
public abstract class RepeatedRule<S extends Place> extends PlacesRule<S> {
  private final int members;

  /** How many members have to do the same before each place is a finding. */
  protected RepeatedRule(int members) {
    this.members = members;
  }

  /** The places that do one same thing, and in how many members they sit. */
  public record Shared<S>(String what, List<S> places, int members) {
  }

  /** What a place does, written so that the same thing done elsewhere reads the same. */
  protected abstract String what(S place, Project project);

  /** The finding of one place, given the others that share what it does. */
  protected abstract Finding found(S place, Shared<S> shared, Project project);

  /** Whether a place counts at all, now that the project is whole; by default every one does. */
  protected boolean counts(S place, Project project) {
    return true;
  }

  /** A finding at a place, keyed under its file and member as the baseline remembers it. */
  protected Finding finding(S place, String key, String detail) {
    return new Finding(id(), place.file(), place.line(), place.file() + "|" + place.member() + "|" + key, detail);
  }

  @Override
  protected final List<Finding> conclude(List<S> places, Project project) {
    Map<String, List<S>> same = new LinkedHashMap<>();
    places.stream().filter(place -> counts(place, project))
        .forEach(place -> same.computeIfAbsent(what(place, project), k -> new ArrayList<>()).add(place));
    List<Finding> findings = new ArrayList<>();
    same.forEach((what, sharing) -> {
      int asking = sharing.stream().map(Place::member).collect(Collectors.toSet()).size();
      if (asking >= members) {
        Shared<S> shared = new Shared<>(what, sharing, asking);
        sharing.forEach(place -> findings.add(found(place, shared, project)));
      }
    });
    return findings;
  }
}
