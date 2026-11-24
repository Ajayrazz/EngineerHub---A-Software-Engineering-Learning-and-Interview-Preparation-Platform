"""Compiles and runs every code block of a language lesson (where a toolchain exists) so lessons never ship broken code.
Usage: python3 verify_langs.py <module> <LESSON_VARIABLE>"""
import sys, os, subprocess, tempfile, importlib, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
INPUT = "4 9\n2 7 11 15\n"
JAVA = "/Library/Java/JavaVirtualMachines/jdk-21.0.7+6/Contents/Home/bin/java"

def run(lang, code):
    d = tempfile.mkdtemp()
    try:
        if lang == "java":
            open(f"{d}/Main.java", "w").write(code)
            r = subprocess.run([JAVA, f"{d}/Main.java"], input=INPUT, capture_output=True, text=True, timeout=90)
        elif lang == "python":
            open(f"{d}/m.py", "w").write(code)
            r = subprocess.run(["python3", f"{d}/m.py"], input=INPUT, capture_output=True, text=True, timeout=30)
        elif lang == "cpp":
            open(f"{d}/m.cpp", "w").write(code)
            c = subprocess.run(["clang++", "-std=c++17", "-Wall", f"{d}/m.cpp", "-o", f"{d}/m"], capture_output=True, text=True)
            if c.returncode: return c.stderr[:500]
            r = subprocess.run([f"{d}/m"], input=INPUT, capture_output=True, text=True, timeout=30)
        elif lang == "javascript":
            open(f"{d}/m.js", "w").write(code)
            r = subprocess.run(["node", f"{d}/m.js"], input=INPUT, capture_output=True, text=True, timeout=30)
        elif lang == "typescript":
            open(f"{d}/m.ts", "w").write(code)
            tsc = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../frontend/node_modules/.bin/tsc")
            ts_root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../frontend/node_modules/@types")
            c = subprocess.run([tsc, "--noEmit", "--strict", "--target", "es2022", "--module", "esnext", "--moduleResolution", "bundler", "--skipLibCheck", "--types", "node", "--typeRoots", ts_root, f"{d}/m.ts"], capture_output=True, text=True)
            if c.returncode: return "tsc: " + (c.stdout or c.stderr)[:500]
            r = subprocess.run(["node", "--experimental-strip-types", f"{d}/m.ts"], input=INPUT, capture_output=True, text=True, timeout=30)
        else:
            return "SKIP: no toolchain installed for " + lang
        return None if r.returncode == 0 else (r.stderr or r.stdout)[:500]
    finally:
        shutil.rmtree(d, ignore_errors=True)

if __name__ == "__main__":
    mod, attr = sys.argv[1], sys.argv[2]
    lesson = getattr(importlib.import_module(mod), attr)
    bad = n = skipped = 0
    for b in lesson["blocks"]:
        if b[0] == "code":
            n += 1
            err = run(b[1], b[2])
            if err and err.startswith("SKIP"): skipped += 1
            elif err: bad += 1; print("FAIL:", b[3], "\n", err)
    print(f"{attr}: {n} programs, {bad} failed, {skipped} not run")
    sys.exit(1 if bad else 0)
