package com.fpetrola.cerco.maven;

import java.util.Optional;

/** One hook as .claude/settings.json declares it: the event, the tool matcher, the command, its timeout. */
public record HookSpec(String event, Optional<String> matcher, String command, int timeoutSeconds, String marker) {
}
