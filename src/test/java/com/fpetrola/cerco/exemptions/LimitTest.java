package com.fpetrola.cerco.exemptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LimitTest {
  private final Context context = new Context(LocalDate.of(2026, 11, 9), "1.3.9-SNAPSHOT", 3);

  @Test
  void datesExpireOnTheDay() {
    assertTrue(new Limit.Until(LocalDate.of(2026, 11, 9)).judge(context).expired());
    Verdict open = new Limit.Until(LocalDate.of(2026, 11, 19)).judge(context);
    assertFalse(open.expired());
    assertEquals("expires in 10 days (2026-11-19)", open.note());
  }

  @Test
  void versionsCompareNumerically() {
    assertFalse(new Limit.UntilVersion("1.4.0").judge(context).expired());
    assertTrue(new Limit.UntilVersion("1.3.9").judge(context).expired());
    assertTrue(new Limit.UntilVersion("1.3").judge(context).expired());
    assertFalse(new Limit.UntilVersion("1.10").judge(context).expired());
  }

  @Test
  void budgetsExpireWhenExceeded() {
    assertFalse(new Limit.Budget(3).judge(context).expired());
    Verdict over = new Limit.Budget(2).judge(context);
    assertTrue(over.expired());
    assertEquals("over budget: covers 3 findings, allowed 2", over.note());
  }
}
