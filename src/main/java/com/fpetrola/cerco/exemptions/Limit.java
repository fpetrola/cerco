package com.fpetrola.cerco.exemptions;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** The end an exemption carries with it: a date, a version, or a count. Every exemption has exactly one. */
public sealed interface Limit permits Limit.Until, Limit.UntilVersion, Limit.Budget {

  Verdict judge(Context context);

  String describe();

  record Until(LocalDate date) implements Limit {
    @Override
    public Verdict judge(Context context) {
      long days = ChronoUnit.DAYS.between(context.today(), date);
      if (days <= 0) {
        return new Verdict(true, "expired on " + date);
      }
      return new Verdict(false, "expires in " + days + " days (" + date + ")");
    }

    @Override
    public String describe() {
      return "until " + date;
    }
  }

  record UntilVersion(String version) implements Limit {
    @Override
    public Verdict judge(Context context) {
      if (new VersionOrder().compare(context.projectVersion(), version) >= 0) {
        return new Verdict(true, "expired at version " + version + " (project is " + context.projectVersion() + ")");
      }
      return new Verdict(false, "until version " + version + " (project is " + context.projectVersion() + ")");
    }

    @Override
    public String describe() {
      return "until version " + version;
    }
  }

  record Budget(int max) implements Limit {
    @Override
    public Verdict judge(Context context) {
      if (context.matched() > max) {
        return new Verdict(true, "over budget: covers " + context.matched() + " findings, allowed " + max);
      }
      return new Verdict(false, "budget " + context.matched() + " of " + max);
    }

    @Override
    public String describe() {
      return "budget " + max;
    }
  }
}
