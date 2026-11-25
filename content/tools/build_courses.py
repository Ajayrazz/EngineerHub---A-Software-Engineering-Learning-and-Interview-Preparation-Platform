"""Writes content/courses.json from courses_a / courses_b (plain data for scripts/seed-content.mjs)."""
import json, pathlib
from courses_a import COURSES_A
from courses_b import COURSES_B
from courses_c import COURSES_C
from courses_dsa_more import DSA_FULL as DSA

def clean(c):
    for sec in c["sections"]:
        pass
    secs = c["sections"]
    return {**c, "sections": secs if secs and isinstance(secs[0], dict) else [{"title": t, "lessons": ls} for t, ls in secs]}

courses = [clean(c) for c in [DSA] + COURSES_A + COURSES_B + COURSES_C]
for c in courses:
    for s in c["sections"]:
        for l in s["lessons"]:
            for q in l["quiz"] or []:
                assert max(q["correct"]) < len(q["options"]) and len(q["options"]) >= 2, (l["slug"], q["prompt"])
                if q["type"] == "true_false": assert q["options"] == ["True", "False"], l["slug"]
path = pathlib.Path(__file__).resolve().parent.parent / "courses.json"
path.write_text(json.dumps(courses, indent=1, ensure_ascii=False) + "\n")
n = sum(len(s["lessons"]) for c in courses for s in c["sections"])
print(f"{len(courses)} courses, {n} lessons -> {path}")

# The reader sidebar lists each lesson's practice problems under it. Derive that map from the content so it can never
# drift from the lesson text: lesson slug -> [{slug, title, difficulty}] (frontend/src/lib/reader/practice-outline.json).
import re
probs = {p["slug"]: p for p in json.loads((path.parent / "problems.json").read_text())}
outline = {}
for c in courses:
    for sec in c["sections"]:
        for les in sec["lessons"]:
            texts = [les.get("md") or ""] + [b[1] for b in (les.get("blocks") or []) if b[0] == "md"]
            seen, items = set(), []
            for t in texts:
                for title, slug in re.findall(r"\[([^\]]+)\]\(/problems/([a-z0-9-]+)\)", t):
                    if slug in probs and slug not in seen:
                        seen.add(slug); items.append({"slug": slug, "title": probs[slug]["title"], "difficulty": probs[slug]["difficulty"]})
            if items: outline[f"{c['slug']}/{les['slug']}"] = items
out = path.parent.parent / "frontend/src/lib/reader/practice-outline.json"
out.write_text(json.dumps(outline, indent=1, ensure_ascii=False) + "\n")
print(f"practice outline: {sum(len(v) for v in outline.values())} problem links in {len(outline)} lessons -> {out}")
