import { describe, expect, it } from "vitest";
import { canEditRole, canEnterAdmin, holds, isUuid, pageParam, query } from "./policy";

describe("canEnterAdmin", () => {
  it("lets course editors in, globally or through a scoped grant", () => {
    expect(canEnterAdmin(["course.edit"])).toBe(true);
    expect(canEnterAdmin([], ["course.edit"])).toBe(true);
    expect(canEnterAdmin([], ["moderation.manage"])).toBe(false);
  });
  it("needs user.read or audit.read", () => {
    expect(canEnterAdmin(["user.read"])).toBe(true);
    expect(canEnterAdmin(["audit.read"])).toBe(true);
    expect(canEnterAdmin(["moderation.manage", "user.mute"])).toBe(false);
    expect(canEnterAdmin([])).toBe(false);
  });
});

describe("canEditRole", () => {
  it("lets standard granters manage ordinary staff roles only", () => {
    expect(canEditRole(["role.grant.standard"], "MODERATOR")).toBe(true);
    expect(canEditRole(["role.grant.standard"], "ADMIN")).toBe(false);
    expect(canEditRole(["role.grant.standard"], "SUPER_ADMIN")).toBe(false);
  });
  it("needs role.grant.all for administrator roles", () => {
    expect(canEditRole(["role.grant.standard", "role.grant.all"], "ADMIN")).toBe(true);
    expect(canEditRole([], "MODERATOR")).toBe(false);
  });
});

describe("holds", () => {
  it("treats only course.edit as satisfiable by a scoped capability", () => {
    expect(holds([], ["course.edit"], "course.edit")).toBe(true);
    expect(holds([], ["course.edit"], "user.read")).toBe(false);
    expect(holds([], ["problem.edit"], "problem.edit")).toBe(true);
    expect(holds([], ["problem.edit"], "course.edit")).toBe(false);
    expect(canEnterAdmin([], ["problem.edit"])).toBe(true);
    expect(holds(["user.read"], [], "user.read")).toBe(true);
  });
});

describe("helpers", () => {
  it("drops empty query values and encodes the rest", () => {
    expect(query({ q: "a b&c", status: "", page: 0 })).toBe("?q=a+b%26c&page=0");
    expect(query({ q: "  " })).toBe("");
  });
  it("accepts only UUIDs as path ids", () => {
    expect(isUuid("0e22f87f-34c1-4bd1-af95-9681025af928")).toBe(true);
    expect(isUuid("../../actuator/env")).toBe(false);
    expect(isUuid("0e22f87f-34c1-4bd1-af95-9681025af928/roles")).toBe(false);
  });
  it("clamps page numbers", () => {
    expect(pageParam("3")).toBe(3);
    expect(pageParam("-1")).toBe(0);
    expect(pageParam("abc")).toBe(0);
    expect(pageParam(undefined)).toBe(0);
    expect(pageParam("1e9")).toBe(0);
  });
});
