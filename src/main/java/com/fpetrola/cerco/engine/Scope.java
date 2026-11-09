package com.fpetrola.cerco.engine;

import com.fpetrola.cerco.model.SourceFile;
import java.util.List;

/**
 * What one run looks at. A full scope is the whole project: project-wide rules run, and an exemption that covers
 * nothing is stale. A partial scope is a module or the files just edited.
 */
public record Scope(String label, List<SourceFile> files, boolean full) {
}
