package com.fpetrola.cerco.rules;

import com.fpetrola.cerco.model.Finding;
import com.fpetrola.cerco.model.ParsedFile;
import com.fpetrola.cerco.project.Location;
import com.fpetrola.cerco.project.Project;
import com.fpetrola.cerco.surface.Member;
import com.fpetrola.cerco.surface.Offered;
import com.fpetrola.cerco.surface.Reach;
import com.fpetrola.cerco.surface.Seal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every member a module offers that its seal lacks: the design grew without a person's signature. A run that fed
 * every file of the project, and only such a run, also seals a project that never was, and drops from the seal what the code no longer offers.
 * Its findings are never frozen nor exempted: the way out is to take the member back or to have it signed.
 */
public final class SealedSurface implements Rule {
  /** Its id: only its findings are surface a person can sign into the seal. */
  public static final String ID = "sealed-surface";
  private final Seal seal;
  private final Reach reach;

  public SealedSurface(Seal seal, Reach reach) {
    this.seal = seal;
    this.reach = reach;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String principle() {
    return "design is agreed before it is built: what one object can ask of another was signed by a person";
  }

  @Override
  public String description() {
    return "a type or member that others can use (" + reach.word() + ") and that the sealed surface of its module "
        + "does not have";
  }

  @Override
  public String remedy() {
    return "if nothing outside this type needs it, make it private; if the sealed surface already says it, use "
        + "that; if the design really lacks it, stop and propose it: a person signs it with cerco:approve";
  }

  @Override
  public boolean baselined() {
    return false;
  }

  @Override
  public Inspection inspection(Project project) {
    Map<String, List<Member>> offered = new LinkedHashMap<>();
    return new Inspection() {
      @Override
      public void visit(ParsedFile file) {
        offered.computeIfAbsent(new Location(file.name()).module(), k -> new ArrayList<>()).addAll(
            new Offered(file).members().stream().filter(member -> member.within(reach)).toList());
      }

      @Override
      public List<Finding> findings() {
        if (project.sawEveryFile()) {
          offered.forEach((module, members) -> {
            seal.adopt(module, members);
            seal.shrink(module, members);
          });
        }
        List<Finding> found = new ArrayList<>();
        offered.forEach((module, members) -> seal.unsigned(module, members).forEach(member -> found.add(
            new Finding(id(), member.file(), member.line(), member.file() + "|" + member.signature(),
                member.signature() + " is not in the sealed surface of " + (module.isEmpty() ? "the project" : module)))));
        return found;
      }
    };
  }
}
