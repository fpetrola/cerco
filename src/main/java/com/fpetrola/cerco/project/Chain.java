package com.fpetrola.cerco.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How an expression reaches what it names, for the project to follow once it is whole: the type it starts from when
 * the file shows one, the type it is written in otherwise, and the members asked one after another. `speccy.loop`
 * in a method taking a `Speccy` starts at `Speccy` and asks `loop`; a bare `machine` no file declares starts at the
 * type around it and asks `machine`, which that type may inherit.
 */
public record Chain(Optional<TypeName> start, String within, List<String> hops) {

  public Chain then(String member) {
    List<String> longer = new ArrayList<>(hops);
    longer.add(member);
    return new Chain(start, within, longer);
  }
}
