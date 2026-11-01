package com.countcheck;

import java.util.UUID;

public final class Rules {
  private Rules() {}

  public static String text(String raw, int max, String field) {
    if (raw == null) throw new IllegalArgumentException(field + " is required.");
    String s = raw.strip();
    if (s.isEmpty()
        || s.codePointCount(0, s.length()) > max
        || s.codePoints().anyMatch(c -> Character.isISOControl(c) || (c >= 0xD800 && c <= 0xDFFF)))
      throw new IllegalArgumentException(
          field + " must contain 1–" + max + " readable characters.");
    return s;
  }

  public static int count(String raw) {
    if (raw == null || !raw.matches("0|[1-9][0-9]{0,5}"))
      throw new IllegalArgumentException(
          "Counts must be whole numbers from 0 to 100000, without spaces or leading zeros.");
    int n = Integer.parseInt(raw);
    if (n > 100000) throw new IllegalArgumentException("Counts cannot exceed 100000.");
    return n;
  }

  public static UUID key(String raw) {
    if (raw == null
        || !raw.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
      throw new IllegalArgumentException(
          "This form has an invalid request ID. Open a new count form.");
    return UUID.fromString(raw);
  }

  public static String difference(int n) {
    return n > 0 ? "+" + n : n < 0 ? "−" + Math.abs(n) : "0";
  }
}
