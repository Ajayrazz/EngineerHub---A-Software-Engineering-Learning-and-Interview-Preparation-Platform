"""Writes content/courses.json from courses_a / courses_b (plain data for scripts/seed-content.mjs)."""
import json, pathlib
from courses_a import COURSES_A
from courses_b import COURSES_B

def clean(c):
    for sec in c["sections"]:
        pass
    return {**c, "sections": [{"title": t, "lessons": ls} for t, ls in c["sections"]]}

courses = [clean(c) for c in COURSES_A + COURSES_B]
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
