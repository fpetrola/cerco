package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Reading;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Clues about one finding, tried in order: each speaks or stays silent, and the first to speak is the reading. */
final class Clues {

  @SafeVarargs
  final Optional<Reading> first(Supplier<Optional<Reading>>... clues) {
    return Stream.of(clues).map(Supplier::get).flatMap(Optional::stream).findFirst();
  }
}
