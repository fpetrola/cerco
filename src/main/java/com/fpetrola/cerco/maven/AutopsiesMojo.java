package com.fpetrola.cerco.maven;

import com.fpetrola.cerco.autopsy.Backlog;
import com.fpetrola.cerco.autopsy.Catalog;
import org.apache.maven.plugins.annotations.Mojo;

/** The rules the project's autopsies propose, most asked first: what a human reads to decide the gate's next rule. */
@Mojo(name = "autopsies", aggregator = true)
public final class AutopsiesMojo extends AbstractCercoMojo {

  @Override
  protected void run() {
    Catalog catalog = new Catalog(new com.fpetrola.cerco.model.Layout(root()).autopsies());
    StringBuilder out = new StringBuilder("cerco autopsies: " + catalog.done().size() + " filed in .cerco/autopsies/\n");
    new Backlog(catalog).wanted().forEach(wanted -> out.append("\n").append(wanted.ruleId()).append("  asked by ")
        .append(wanted.autopsies().size()).append(": ").append(String.join(", ", wanted.autopsies())));
    out.append("\n\nEach one is a cause found while fixing a finding. A rule asked for by unrelated fixes is the gate's "
        + "next rule.\n");
    System.out.print(out);
  }
}
