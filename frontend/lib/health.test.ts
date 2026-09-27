import { describe, expect, it } from "vitest";
import { summarizeHealth } from "./health";

describe("summarizeHealth", () => {
  it("marks the backend down when there is no response", () => {
    expect(summarizeHealth(null)).toEqual([{ name: "Backend", up: false }]);
  });

  it("maps known components and ignores unknown ones", () => {
    const result = summarizeHealth({
      status: "UP",
      components: {
        db: { status: "UP" },
        redis: { status: "DOWN" },
        somethingElse: { status: "UP" },
      },
    });
    expect(result).toEqual([
      { name: "PostgreSQL", up: true },
      { name: "Redis", up: false },
    ]);
  });
});
