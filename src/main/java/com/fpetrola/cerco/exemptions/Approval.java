package com.fpetrola.cerco.exemptions;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * A human's signature over the exemptions and over any configuration that differs from the defaults: the digests
 * of both, who approved, when. Without a matching signature neither loosening applies.
 */
public record Approval(Optional<String> exemptionsDigest, Optional<String> configDigest, String by, String on) {

  public static Approval read(Path file) {
    if (!Files.exists(file)) {
      return new Approval(Optional.empty(), Optional.empty(), "", "");
    }
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the approval file " + file, e);
    }
    return new Approval(Optional.ofNullable(properties.getProperty("exemptions")),
        Optional.ofNullable(properties.getProperty("config")), properties.getProperty("by", ""),
        properties.getProperty("on", ""));
  }

  /** A signature over the exemptions as they stand and, when the configuration is not the default, over it. */
  public static Approval of(Optional<String> exemptionsDigest, Optional<String> configDigest, String by,
      LocalDate on) {
    return new Approval(exemptionsDigest, configDigest, by, on.toString());
  }

  /** What stands in the way of applying the current exemptions and configuration. */
  public List<String> problems(Optional<String> currentExemptions, String currentConfig, boolean configIsDefault) {
    List<String> problems = new ArrayList<>();
    if (currentExemptions.isPresent() && !currentExemptions.equals(exemptionsDigest)) {
      problems.add("exemptions.yaml is not approved as it stands: a human runs `mvn cerco:approve` in a terminal "
          + "to sign it; until then no exemption applies");
    }
    if (!configIsDefault && !Optional.of(currentConfig).equals(configDigest)) {
      problems.add("the plugin configuration in the pom differs from the defaults and is not approved: a human runs "
          + "`mvn cerco:approve` in a terminal to sign it; until then the configuration is a finding");
    }
    return problems;
  }

  public void write(Path file) {
    Properties properties = new Properties();
    exemptionsDigest.ifPresent(digest -> properties.setProperty("exemptions", digest));
    configDigest.ifPresent(digest -> properties.setProperty("config", digest));
    properties.setProperty("by", by);
    properties.setProperty("on", on);
    try {
      Files.createDirectories(file.getParent());
      try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
        properties.store(writer, "cerco approval: a human signed the exemptions and the configuration as they stand");
      }
    } catch (IOException e) {
      throw new UncheckedIOException("could not write the approval file " + file, e);
    }
  }
}
