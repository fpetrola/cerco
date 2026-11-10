package com.fpetrola.cerco.autopsy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The places an autopsy points at, written path:line with the path relative to the project root. Only places that
 * are really there count: a file that exists and a line it has, so a reference cannot be made up.
 */
public final class References {
  private static final Pattern PLACE = Pattern.compile("([\\w./-]+\\.[A-Za-z]+):(\\d+)");
  private final Path root;

  public References(Path root) {
    this.root = root;
  }

  /** One place in the code, as written in the form. */
  public record Place(String file, int line) {
  }

  public Set<Place> in(String text) {
    Set<Place> places = new LinkedHashSet<>();
    Matcher matcher = PLACE.matcher(text);
    while (matcher.find()) {
      Place place = new Place(matcher.group(1), Integer.parseInt(matcher.group(2)));
      if (exists(place)) {
        places.add(place);
      }
    }
    return places;
  }

  private boolean exists(Place place) {
    Path file = root.resolve(place.file()).normalize();
    if (!file.startsWith(root) || !Files.isRegularFile(file) || place.line() < 1) {
      return false;
    }
    // Latin-1 reads any byte, so a file that is not UTF-8 still has its lines counted rather than failing the gate.
    try (var lines = Files.lines(file, StandardCharsets.ISO_8859_1)) {
      return lines.limit(place.line()).count() == place.line();
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + file + ", named by an autopsy", e);
    }
  }
}
