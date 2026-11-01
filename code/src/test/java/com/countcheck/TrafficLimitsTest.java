package com.countcheck;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class TrafficLimitsTest {
  @Test
  void accountAttemptsAreLimitedPerClient() {
    var limits = new TrafficLimits(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    for (int i = 0; i < 12; i++) assertTrue(limits.allow("client-a", true, true));
    assertFalse(limits.allow("client-a", true, true));
    assertTrue(limits.allow("client-b", true, true));
  }

  @Test
  void ordinaryRequestsHaveIndependentBudget() {
    var limits = new TrafficLimits(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    for (int i = 0; i < 12; i++) limits.allow("client", true, true);
    assertTrue(limits.allow("client", false, false));
  }

  @Test
  void windowsReset() {
    var clock = new MutableClock();
    var limits = new TrafficLimits(clock);
    for (int i = 0; i < 12; i++) limits.allow("client", true, true);
    assertFalse(limits.allow("client", true, true));
    clock.now = 60000;
    assertTrue(limits.allow("client", true, true));
  }

  static class MutableClock extends Clock {
    long now;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return Instant.ofEpochMilli(now);
    }
  }
}
