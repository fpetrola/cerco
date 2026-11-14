package com.fpetrola.cerco.surface;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * One module's seal on disk. It is written as YAML grouped by type, so a person reads what each type promises and in
 * which module; the one-signature-per-line .txt of earlier versions is still read, and gives way to the YAML on the
 * next write. Only the shape cerco writes is read back: this is not a YAML parser.
 */
final class SealFile {
  private final Path file;

  SealFile(Path file) {
    this.file = file;
  }

  /** The signatures it holds, as the surface writes them; none when the file is not there. */
  Set<String> signatures() {
    if (!Files.isRegularFile(file)) {
      return new TreeSet<>();
    }
    List<String> lines = read();
    return file.getFileName().toString().endsWith(".yaml") ? parsed(lines)
        : lines.stream().filter(line -> !line.isBlank()).collect(Collectors.toCollection(TreeSet::new));
  }

  /** Writes these signatures of the module, grouped by type. */
  void write(String module, Set<String> signatures) {
    Map<String, String> kinds = new TreeMap<>();
    Map<String, Set<String>> members = new TreeMap<>();
    for (String signature : signatures) {
      String generics = generics(signature);
      String bare = signature.substring(generics.length());
      int hash = bare.indexOf('#');
      if (hash < 0) {
        kinds.put(bare.substring(0, bare.lastIndexOf(' ')), bare.substring(bare.lastIndexOf(' ') + 1));
      } else {
        members.computeIfAbsent(bare.substring(0, hash), k -> new TreeSet<>()).add(generics + bare.substring(hash + 1));
      }
    }
    StringBuilder out = new StringBuilder("# What a person signed of this module's surface; cerco:approve grows it.\n");
    out.append("module: ").append(quoted(module.isEmpty() ? "the project" : module)).append('\n');
    Set<String> types = new TreeSet<>(kinds.keySet());
    types.addAll(members.keySet());
    out.append(types.isEmpty() ? "types: {}\n" : "types:\n");
    for (String type : types) {
      out.append("  ").append(quoted(type)).append(":\n");
      if (kinds.containsKey(type)) {
        out.append("    kind: ").append(kinds.get(type)).append('\n');
      }
      Set<String> own = members.getOrDefault(type, Set.of());
      if (!own.isEmpty()) {
        out.append("    members:\n");
        own.forEach(member -> out.append("      - ").append(quoted(member)).append('\n'));
      }
    }
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, out, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not write the seal " + file, e);
    }
  }

  private Set<String> parsed(List<String> lines) {
    Set<String> signatures = new TreeSet<>();
    String type = "";
    for (String line : lines) {
      String strip = line.strip();
      if (line.startsWith("  ") && !line.startsWith("   ") && strip.endsWith(":")) {
        type = unquoted(strip.substring(0, strip.length() - 1));
        continue;
      }
      signature(type, strip).ifPresent(signatures::add);
    }
    return signatures;
  }

  /** The signature a line under a type says: its kind, or one of its members; none for any other line. */
  private java.util.Optional<String> signature(String type, String strip) {
    if (strip.startsWith("kind: ")) {
      return java.util.Optional.of(type + " " + strip.substring("kind: ".length()).strip());
    }
    if (!strip.startsWith("- ")) {
      return java.util.Optional.empty();
    }
    String member = unquoted(strip.substring(2));
    String generics = generics(member);
    return java.util.Optional.of(generics + type + "#" + member.substring(generics.length()));
  }

  /** The type parameters a generic method's signature starts with, `<T> ` included; empty when it has none. */
  private String generics(String signature) {
    if (!signature.startsWith("<")) {
      return "";
    }
    int depth = 0;
    for (int at = 0; at < signature.length(); at++) {
      depth += signature.charAt(at) == '<' ? 1 : signature.charAt(at) == '>' ? -1 : 0;
      if (depth == 0) {
        return signature.startsWith(" ", at + 1) ? signature.substring(0, at + 2) : "";
      }
    }
    return "";
  }

  private String quoted(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private String unquoted(String text) {
    return text.startsWith("\"") && text.endsWith("\"") && text.length() > 1
        ? text.substring(1, text.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\") : text;
  }

  private List<String> read() {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the seal " + file, e);
    }
  }
}
