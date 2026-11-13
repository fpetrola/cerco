package com.fpetrola.cerco.model;

import java.util.List;
import java.util.Optional;

/**
 * Everything a project may tune, with the defaults every project starts from. Any difference from the defaults is a
 * loosening a human has to approve, so the whole record has one canonical fingerprint. Sealing the surface is not
 * a loosening, but which reach is sealed is a design decision a person signs all the same.
 */
public record Settings(int fileCeiling, int methodCeiling, List<String> utilitySuffixes, List<String> factoryPrefixes,
    List<String> generatedGlobs, int maxExemptions, boolean includeTests, List<String> disabledRules,
    boolean autopsies, List<String> declinedRules, Optional<String> sealedSurface) {

  public static final Settings DEFAULTS = new Settings(400, 40,
      List.of("Util", "Utils", "Helper", "Helpers", "Manager", "Impl", "Common", "Misc", "Tools"),
      List.of("of", "from", "parse", "create", "valueOf", "copyOf"),
      List.of("**/target/**", "**/generated-sources/**", "**/generated/**"),
      10, false, List.of(), true, List.of(), Optional.empty());

  public boolean isDefault() {
    return equals(DEFAULTS);
  }

  public String fingerprint() {
    return "fileCeiling=" + fileCeiling + ";methodCeiling=" + methodCeiling + ";utilitySuffixes=" + utilitySuffixes
        + ";factoryPrefixes=" + factoryPrefixes + ";generatedGlobs=" + generatedGlobs + ";maxExemptions="
        + maxExemptions + ";includeTests=" + includeTests + ";disabledRules=" + disabledRules
        + (autopsies ? "" : ";autopsies=false") + (declinedRules.isEmpty() ? "" : ";declinedRules=" + declinedRules)
        + sealedSurface.map(reach -> ";sealedSurface=" + reach).orElse("");
  }
}
