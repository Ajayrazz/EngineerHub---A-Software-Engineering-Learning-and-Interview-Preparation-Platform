import { describe, expect, it } from "vitest";
import { isCsrfSafe, isUnsafeMethod, safeEqual } from "./csrf";
import { toApiPath } from "./proxy-path";
import { safeReturnTo } from "./return-to";

describe("safeReturnTo (open-redirect protection)", () => {
  it.each([
    ["/account", "/account"],
    ["/learn/graphs?x=1#top", "/learn/graphs?x=1#top"],
    ["/", "/"],
  ])("accepts %s", (input, expected) => expect(safeReturnTo(input)).toBe(expected));

  it.each([
    "https://evil.example",
    "http://evil.example/x",
    "//evil.example",
    "///evil.example",
    "/\\evil.example",
    "\\\\evil.example",
    "javascript:alert(1)",
    "data:text/html,x",
    "account",
    "",
    "/ok\nHost: evil",
    "/ok\r\nSet-Cookie: a=b",
    "/has space",
    "/a\u0000b",
    "/auth/login",
    "/auth/callback?code=1",
    "/" + "a".repeat(250),
  ])("rejects %j", (input) => expect(safeReturnTo(input)).toBe("/"));

  it("handles null/undefined", () => {
    expect(safeReturnTo(null)).toBe("/");
    expect(safeReturnTo(undefined)).toBe("/");
  });
});

describe("CSRF checks", () => {
  const base = { appOrigin: "http://localhost:3000", expectedToken: "tok-123" };
  it("requires matching Origin AND token", () => {
    expect(isCsrfSafe({ ...base, origin: "http://localhost:3000", providedToken: "tok-123" })).toBe(
      true,
    );
    expect(isCsrfSafe({ ...base, origin: "http://localhost:3000", providedToken: "nope" })).toBe(
      false,
    );
    expect(isCsrfSafe({ ...base, origin: "http://localhost:3000", providedToken: null })).toBe(
      false,
    );
    expect(isCsrfSafe({ ...base, origin: "https://evil.example", providedToken: "tok-123" })).toBe(
      false,
    );
    expect(isCsrfSafe({ ...base, origin: null, providedToken: "tok-123" })).toBe(false);
    expect(
      isCsrfSafe({
        ...base,
        origin: "http://localhost:3000.evil.example",
        providedToken: "tok-123",
      }),
    ).toBe(false);
  });
  it("compares in constant-time without throwing on length mismatch", () => {
    expect(safeEqual("a", "ab")).toBe(false);
    expect(safeEqual("abc", "abc")).toBe(true);
    expect(safeEqual(undefined, "abc")).toBe(false);
  });
  it("classifies methods", () => {
    expect(isUnsafeMethod("GET")).toBe(false);
    expect(isUnsafeMethod("head")).toBe(false);
    for (const m of ["POST", "PUT", "PATCH", "DELETE"]) expect(isUnsafeMethod(m)).toBe(true);
  });
});

describe("toApiPath (proxy traversal/SSRF protection)", () => {
  it("accepts plain API paths", () => {
    expect(toApiPath(["system", "whoami"])).toBe("system/whoami");
    expect(toApiPath(["courses", "dsa-101", "lessons", "arrays.v2"])).toBe(
      "courses/dsa-101/lessons/arrays.v2",
    );
  });
  it.each([
    [undefined],
    [[]],
    [[".."]],
    [["system", ".."]],
    [["."]],
    [["%2e%2e"]],
    [["%2E%2E", "actuator"]],
    [["a%2fb"]],
    [["a/b"]],
    [["a\\b"]],
    [["%252e%252e"]],
    [["a%00b"]],
    [[""]],
    [["-leading-dash"]],
    [["http:", "", "evil.example"]],
    [["@evil.example"]],
    [["a?b"]],
    [["a#b"]],
    [["a b"]],
    [Array(13).fill("a")],
  ])("rejects %j", (segments) => expect(toApiPath(segments as string[] | undefined)).toBeNull());
});
