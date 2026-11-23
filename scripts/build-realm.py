#!/usr/bin/env python3
"""Builds the Keycloak realm import from the prod-safe base plus an environment overlay, and guards the base.

  build-realm.py build dev OUT_DIR   write OUT_DIR/engineerhub-realm.json = base + dev overlay
  build-realm.py check               fail if the base contains anything that must never reach production

Merge rules: objects merge recursively (overlay wins); `clients`, `users`, `roles.realm` and
`authenticationFlows` lists are appended (clients/roles/flows are identified by clientId/name/alias and may
not collide); other lists are replaced.
"""
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "infra" / "keycloak"
APPEND_KEYS = {"clients": "clientId", "users": "username", "authenticationFlows": "alias"}
FORBIDDEN_BASE_KEYS = ("users",)
SECRET_LIKE = re.compile(r"(secret|password)", re.I)
NOT_SECRETS = {"passwordPolicy"}


def load(p):
    return json.loads(Path(p).read_text())


def merge(base, over, path=""):
    out = dict(base)
    for k, v in over.items():
        if k.startswith("_"):
            continue
        if k in APPEND_KEYS and isinstance(v, list):
            ident = APPEND_KEYS[k]
            existing = {i[ident] for i in out.get(k, [])}
            clash = existing & {i[ident] for i in v}
            if clash:
                sys.exit(f"overlay redefines {k}: {sorted(clash)}")
            out[k] = list(out.get(k, [])) + v
        elif isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = merge(out[k], v, f"{path}/{k}")
        else:
            out[k] = v
    return out


def check_base(r):
    errs = []
    for k in FORBIDDEN_BASE_KEYS:
        if r.get(k):
            errs.append(f"base must not define '{k}' (seed users belong to the dev overlay)")
    if r.get("sslRequired") != "external":
        errs.append("sslRequired must be 'external' or stricter")
    if not r.get("bruteForceProtected"):
        errs.append("brute-force protection must be on")
    if not r.get("verifyEmail"):
        errs.append("verifyEmail must be on")
    for c in r.get("clients", []):
        cid = c["clientId"]
        if "dev" in cid.lower():
            errs.append(f"dev client '{cid}' in base")
        if c.get("directAccessGrantsEnabled"):
            errs.append(f"{cid}: password grant must be off in base")
        if c.get("implicitFlowEnabled"):
            errs.append(f"{cid}: implicit flow must be off")
        for u in c.get("redirectUris", []) + c.get("webOrigins", []):
            if "*" in u:
                errs.append(f"{cid}: wildcard redirect/origin '{u}'")
            if "localhost" in u or "127.0.0.1" in u:
                errs.append(f"{cid}: hard-coded localhost '{u}' (use ${{APP_URL}})")
        if not c.get("publicClient"):
            sec = c.get("secret", "")
            if sec and not re.fullmatch(r"\$\{[A-Z_]+\}", sec):
                errs.append(f"{cid}: client secret must be an env placeholder, not a literal")
    s = r.get("smtpServer", {})
    for k in ("host", "port", "from", "user", "password"):
        v = s.get(k, "")
        if v and not re.fullmatch(r"\$\{[A-Z_]+\}", v):
            errs.append(f"smtpServer.{k} must be an env placeholder in base")
    for k, v in r.items():
        if k not in NOT_SECRETS and SECRET_LIKE.search(k) and isinstance(v, str) and v and not v.startswith("${"):
            errs.append(f"literal secret-like value at '{k}'")
    return errs


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    base = load(ROOT / "base" / "realm.json")
    if cmd == "check":
        errs = check_base(base)
        merged = merge(base, load(ROOT / "dev" / "overlay.json"))
        names = [c["clientId"] for c in merged["clients"]]
        if len(names) != len(set(names)):
            errs.append("duplicate client ids after merge")
        if errs:
            print("realm check FAILED:\n  - " + "\n  - ".join(errs))
            sys.exit(1)
        print(f"realm check OK (base: {len(base['clients'])} client(s), 0 users; dev adds "
              f"{len(merged['clients']) - len(base['clients'])} client(s), {len(merged.get('users', []))} users)")
    elif cmd == "build" and len(sys.argv) == 4 and sys.argv[2] == "dev":
        errs = check_base(base)
        if errs:
            sys.exit("refusing to build: base failed checks:\n  - " + "\n  - ".join(errs))
        out = Path(sys.argv[3])
        out.mkdir(parents=True, exist_ok=True)
        (out / "engineerhub-realm.json").write_text(
            json.dumps(merge(base, load(ROOT / "dev" / "overlay.json")), indent=2) + "\n")
        print(f"wrote {out / 'engineerhub-realm.json'}")
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
