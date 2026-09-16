"""Content-authoring tool: fill/verify each film recipe's posterUrl from TMDB.

Reads TMDB_API_KEY from the environment (never from a committed file). Not
shipped in the app — run locally whenever recipes are added or need a
poster refresh.

For each recipe JSON's "inspiredBy" title, searches TMDB (movie + tv) via
/search/multi, takes the best poster_path, and writes the w342 CDN URL into
"posterUrl". Recipes with no inspiredBy, or no TMDB match, are left untouched
and reported.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

ROOT = r"D:\## APP\DALUR film"
RECIPES_DIR = os.path.join(ROOT, "shared", "src", "main", "assets", "recipes")
API_KEY = os.environ.get("TMDB_API_KEY", "").strip()
SEARCH_URL = "https://api.themoviedb.org/3/search/multi"
IMAGE_BASE = "https://image.tmdb.org/t/p/w342"


def tmdb_search(title: str) -> dict | None:
    q = urllib.parse.urlencode({"api_key": API_KEY, "query": title, "language": "en-US"})
    req = urllib.request.Request(f"{SEARCH_URL}?{q}")
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = json.load(resp)
    for r in data.get("results", []):
        if r.get("media_type") in ("movie", "tv") and r.get("poster_path"):
            return r
    return None


def main() -> int:
    if not API_KEY:
        print("ERROR: set TMDB_API_KEY in the environment before running.", file=sys.stderr)
        return 1

    names = sorted(f for f in os.listdir(RECIPES_DIR) if f.endswith(".json"))
    updated, unchanged, not_found, skipped = [], [], [], []

    for name in names:
        path = os.path.join(RECIPES_DIR, name)
        with open(path, encoding="utf-8") as fh:
            recipe = json.load(fh)

        title = recipe.get("inspiredBy")
        if not title:
            skipped.append(name)
            continue

        try:
            match = tmdb_search(title)
        except Exception as e:
            not_found.append(f"{name} ({title}): request failed - {e}")
            continue
        time.sleep(0.25)  # stay well under TMDB's rate limit

        if not match:
            not_found.append(f"{name} ({title}): no TMDB match")
            continue

        new_url = IMAGE_BASE + match["poster_path"]
        old_url = recipe.get("posterUrl")
        matched_title = match.get("title") or match.get("name")
        if new_url == old_url:
            unchanged.append(name)
            continue

        recipe["posterUrl"] = new_url
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(recipe, fh, ensure_ascii=False, indent=2)
            fh.write("\n")
        updated.append(f"{name}: {title!r} -> matched {matched_title!r} | {old_url} -> {new_url}")

    print(f"\n=== TMDB poster sync: {len(names)} recipes ===")
    print(f"updated:    {len(updated)}")
    print(f"unchanged:  {len(unchanged)}")
    print(f"no match:   {len(not_found)}")
    print(f"skipped (no inspiredBy): {len(skipped)}")

    if updated:
        print("\n-- updated --")
        for line in updated:
            print(" ", line)
    if not_found:
        print("\n-- needs manual review (no TMDB match / request failed) --")
        for line in not_found:
            print(" ", line)
    if skipped:
        print("\n-- skipped (no inspiredBy field) --")
        for line in skipped:
            print(" ", line)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
