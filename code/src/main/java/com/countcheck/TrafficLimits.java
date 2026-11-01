package com.countcheck;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Coarse single-instance limits; never trust a client-supplied forwarding header. */
final class TrafficLimits {
  private final Clock clock;
  private long minute = -1;
  private int requests, writes;
  private final Map<String, Bucket> clients = new HashMap<>();

  TrafficLimits(Clock clock) {
    this.clock = clock;
  }

  synchronized boolean allow(String address, boolean write, boolean accountAccess) {
    long current = clock.millis() / 60000;
    if (current != minute) {
      minute = current;
      requests = writes = 0;
      clients.clear();
    }
    if (++requests > 1200 || (write && ++writes > 300)) return false;
    if (!clients.containsKey(address) && clients.size() >= 4096) return false;
    Bucket bucket = clients.computeIfAbsent(address, ignored -> new Bucket());
    return ++bucket.requests <= 300
        && (!write || ++bucket.writes <= 80)
        && (!accountAccess || ++bucket.accountAccess <= 12);
  }

  private static final class Bucket {
    int requests, writes, accountAccess;
  }
}
