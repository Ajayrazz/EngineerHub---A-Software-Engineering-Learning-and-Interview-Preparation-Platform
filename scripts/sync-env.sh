#!/usr/bin/env bash
# Appends variables that exist in .env.example but are missing from .env (never overwrites or removes
# anything), so pulling a change that introduces a new setting does not break a developer's local .env.
set -u
cd "$(dirname "$0")/.." || exit 1
if [ ! -f .env ]; then
  cp .env.example .env
  echo "created .env from .env.example"
  exit 0
fi
added=0
while IFS= read -r line; do
  case "$line" in
    [A-Z_]*=*) key="${line%%=*}"
      if ! grep -q "^${key}=" .env; then
        printf '%s\n' "$line" >> .env
        echo "  + added missing $key to .env"
        added=$((added+1))
      fi ;;
  esac
done < .env.example
[ "$added" -eq 0 ] && echo ".env is up to date"
exit 0
