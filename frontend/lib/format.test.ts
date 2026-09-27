import { describe, expect, it } from "vitest";
import { distance, duration, money, secondsUntil } from "./format";

describe("format", () => {
  it("formats cents as dollars", () => {
    expect(money(2050)).toBe("$20.50");
    expect(money(null)).toBe("—");
  });

  it("formats distance in miles, or feet when very short", () => {
    expect(distance(16093)).toBe("10.0 mi");
    expect(distance(100)).toBe("328 ft");
  });

  it("formats duration in minutes and hours", () => {
    expect(duration(20)).toBe("1 min");
    expect(duration(1500)).toBe("25 min");
    expect(duration(4500)).toBe("1 h 15 min");
  });

  it("counts down to an ISO time and never goes negative", () => {
    const now = Date.parse("2026-01-01T00:00:00Z");
    expect(secondsUntil("2026-01-01T00:00:10Z", now)).toBe(10);
    expect(secondsUntil("2025-12-31T23:59:00Z", now)).toBe(0);
  });
});
