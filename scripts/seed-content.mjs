#!/usr/bin/env node
// Seeds the LOCAL dev stack with the original courses and problems in content/*.json, through the real
// admin API (so every rule, snapshot and audit entry applies). Idempotent: existing slugs are skipped.
// Dev only. Problems are marked "verified" with a direct SQL update because the code judge (Phase 4) does
// not exist yet; their tests were machine-verified offline (reference == brute force, content/tools).
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { randomUUID } from "node:crypto";

const KC = process.env.KEYCLOAK_URL ?? "http://localhost:8081/realms/engineerhub";
const API = process.env.API_URL ?? "http://localhost:8080/api/v1";
const PASSWORD = process.env.DEV_PASSWORD ?? "Dev-Only-Pass-1!";
for (const u of [KC, API]) if (!/^https?:\/\/(localhost|127\.0\.0\.1)[:/]/.test(u)) throw new Error(`refusing non-local URL ${u}`);

const read = (f) => JSON.parse(readFileSync(new URL(`../content/${f}`, import.meta.url), "utf8"));

async function token(user) {
  const r = await fetch(`${KC}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ client_id: "engineerhub-dev-cli", grant_type: "password", username: user, password: PASSWORD }),
  });
  const j = await r.json();
  if (!j.access_token) throw new Error(`no token for ${user}: ${JSON.stringify(j)}`);
  return j.access_token;
}

function caller(tok) {
  return async (method, path, body, okStatuses = []) => {
    const r = await fetch(`${API}/${path}`, {
      method,
      headers: { authorization: `Bearer ${tok}`, "content-type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await r.text();
    if (r.status >= 300 && !okStatuses.includes(r.status)) throw new Error(`${method} ${path} -> ${r.status} ${text.slice(0, 300)}`);
    return text ? JSON.parse(text) : null;
  };
}
const listOf = (x) => (Array.isArray(x) ? x : (x?.content ?? x?.items ?? []));

function quizBlock(l) {
  return {
    type: "quiz",
    data: {
      quizId: randomUUID(),
      title: l.quiz_title,
      questions: l.quiz.map((q) => {
        const options = q.options.map((text) => ({ id: randomUUID(), text }));
        return {
          id: randomUUID(), type: q.type, prompt: q.prompt, explanation: q.explanation,
          options, correct: q.correct.map((i) => options[i].id),
        };
      }),
    },
  };
}
const LEGACY_LANGS = ["cpp", "java", "python"]; // what an API built before ADR-061 accepts
function blocksFor(l, legacy = false) {
  const lang = (x) => (legacy ? (LEGACY_LANGS.includes(x) ? x : "text") : x);
  if (l.blocks) {
    const out = l.blocks.map((b) => {
      if (b[0] === "md") return { type: "markdown", data: { text: b[1] } };
      if (b[0] === "tip") return { type: "callout", data: { kind: "tip", text: b[1] } };
      if (b[0] === "code") return { type: "code", data: { language: lang(b[1]), code: b[2], caption: b[3] } };
      if (b[0] === "viz") return { type: "viz", data: { algorithm: b[1], input: b[2], ...(b[3] ? { param: b[3] } : {}) } };
      throw new Error(`unknown block ${b[0]}`);
    });
    if (l.quiz) out.push(quizBlock(l));
    return out;
  }
  const b = [{ type: "markdown", data: { text: l.md } }];
  if (l.viz) b.push({ type: "viz", data: { algorithm: l.viz[0], input: l.viz[1], ...(l.viz[2] ? { param: l.viz[2] } : {}) } });
  if (l.code) b.push({ type: "code", data: { language: ["cpp", "java", "python"].includes(l.code[0]) ? l.code[0] : "text", code: l.code[1], caption: l.code[2] } });
  if (l.tip) b.push({ type: "callout", data: { kind: "tip", text: l.tip } });
  if (l.quiz) b.push(quizBlock(l));
  return b;
}

async function seedCourses(call, topics) {
  const existing = new Set(listOf(await call("GET", "admin/courses?size=500")).map((c) => c.slug));
  for (const c of read("courses.json")) {
    if (existing.has(c.slug)) { console.log(`course exists: ${c.slug}`); continue; }
    const topic = topics.find((t) => t.slug === c.topic);
    if (!topic) throw new Error(`unknown topic ${c.topic}`);
    let course = await call("POST", "admin/courses", { slug: c.slug, title: c.title, description: c.description, difficulty: c.difficulty, accessTier: c.tier });
    course = await call("PATCH", `admin/courses/${course.id}`, { topicId: topic.id, estMinutes: c.minutes, expectedVersion: course.version });
    const base = `admin/courses/${course.id}`;
    for (const s of c.sections) {
      const withSection = await call("POST", `${base}/sections`, { title: s.title });
      const section = withSection.sections[withSection.sections.length - 1].id;
      for (const l of s.lessons) {
        const made = await call("POST", `${base}/lessons`, { sectionId: section, slug: l.slug, title: l.title, accessTier: l.tier });
        try {
          await call("PUT", `${base}/lessons/${made.id}/blocks`, { expectedVersion: made.version, blocks: blocksFor(l) });
        } catch (e) {
          if (!String(e.message).includes("language must be one of")) throw e;
          // an API started before the code-language allowlist grew: store those blocks as plain text for now
          await call("PUT", `${base}/lessons/${made.id}/blocks`, { expectedVersion: made.version, blocks: blocksFor(l, true) });
        }
        await call("PUT", `${base}/lessons/${made.id}/source`, { kind: "original" });
        await call("POST", `${base}/lessons/${made.id}/publish`, { originalityConfirmed: true });
      }
    }
    await call("POST", `${base}/publish`);
    console.log(`course published: ${c.slug}`);
  }
}

function markVerified(problemId) {
  const user = process.env.APP_DB_USER ?? "engineerhub_app", db = process.env.APP_DB_NAME ?? "engineerhub";
  execFileSync("docker", ["compose", "exec", "-T", "postgres", "psql", "-U", user, "-d", db, "-v", "ON_ERROR_STOP=1", "-c",
    `update problems.problems set validation_status = 'verified' where id = '${problemId}'`], { stdio: "ignore" });
}

async function seedProblems(call, topics) {
  const existing = new Set(listOf(await call("GET", "admin/problems?size=500")).map((p) => p.slug));
  const patterns = new Map(listOf(await call("GET", "patterns")).map((p) => [p.slug, p]));
  const PATTERN_NAMES = { "two-pointers": "Two Pointers", "sliding-window": "Sliding Window", "binary-search": "Binary Search", hashing: "Hashing", "prefix-sums": "Prefix Sums", "monotonic-stack": "Monotonic Stack", intervals: "Intervals", "dynamic-programming": "Dynamic Programming", greedy: "Greedy", bfs: "Breadth-First Search", dfs: "Depth-First Search", "topological-sort": "Topological Sort", "shortest-path": "Shortest Path", "union-find": "Union-Find", heap: "Heap", backtracking: "Backtracking", stack: "Stack" };
  for (const p of read("problems.json")) {
    if (existing.has(p.slug)) { console.log(`problem exists: ${p.slug}`); continue; }
    for (const slug of p.patterns) {
      if (!patterns.has(slug)) patterns.set(slug, await call("POST", "admin/patterns", { slug, name: PATTERN_NAMES[slug] ?? slug }));
    }
    const created = await call("POST", "admin/problems", { slug: p.slug, title: p.title, difficulty: p.difficulty, accessTier: p.tier });
    const base = `admin/problems/${created.id}`;
    await call("PATCH", base, { statement: p.statement, inputFormat: p.inputFormat, outputFormat: p.outputFormat, constraints: p.constraints, hints: p.hints, expectedVersion: created.version });
    await call("PUT", `${base}/tests`, { tests: p.tests.map((t) => ({ input: t.input, expected: t.expected, sample: t.sample, ...(t.explanation ? { explanation: t.explanation } : {}) })), expectedTestcaseVersion: 0 });
    await call("PUT", `${base}/tags`, {
      topicIds: p.topics.map((s) => { const t = topics.find((x) => x.slug === s); if (!t) throw new Error(`unknown topic ${s}`); return t.id; }),
      patternIds: p.patterns.map((s) => patterns.get(s).id),
    });
    await call("PUT", `${base}/source`, { kind: "original" });
    markVerified(created.id);
    await call("POST", `${base}/publish`, { originalityConfirmed: true });
    console.log(`problem published: ${p.slug}`);
  }
}

const manager = caller(await token("contentmanager"));
const setter = caller(await token("admin"));
const topics = await setter("GET", "topics");
await seedCourses(manager, topics);
await seedProblems(setter, topics);
console.log("done");
