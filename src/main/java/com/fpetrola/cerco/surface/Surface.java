package com.fpetrola.cerco.surface;

import java.util.List;
import java.util.Set;

/**
 * What one module offers to others, and which of those offers nothing in the project, its tests included, ever
 * names. Measured at each reach, it says how much of a module's design was written as interfaces.
 */
public record Surface(List<Member> members, Set<String> named) {

  public Surface {
    members = List.copyOf(members);
    named = Set.copyOf(named);
  }

  public List<Member> at(Reach reach) {
    return members.stream().filter(member -> member.within(reach)).toList();
  }

  public long types(Reach reach) {
    return at(reach).stream().filter(Member::isType).count();
  }

  public long operations(Reach reach) {
    return at(reach).stream().filter(member -> !member.isType()).count();
  }

  /** Of the public members a caller can name, the part an interface declares: what was designed as a promise. */
  public double promised() {
    long all = operations(Reach.PUBLIC);
    return all == 0 ? 0 : operations(Reach.INTERFACES) * 1.0 / all;
  }

  /** The public members whose signature says that much. */
  public long saying(Hint hint) {
    return at(Reach.PUBLIC).stream().filter(member -> !member.isType() && member.says(hint)).count();
  }

  /** The public members nothing in the project names, by their simple name: a floor, names are not resolved. */
  public List<Member> unnamed() {
    return at(Reach.PUBLIC).stream().filter(member -> !member.isType() && !named.contains(member.name())).toList();
  }
}
