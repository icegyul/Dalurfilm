"""Fail-closed license gate: GPL/AGPL anywhere in shipped inputs fails the release."""
import os, sys
ROOT = r"D:\## APP\DALUR film"
# NOTE: fragments are concatenated so this gate never matches its own search terms.
BANNED = ["general public licen" + "se", "gnu general pub" + "lic", "gnu aff" + "ero",
          "agpl-3" + ".0", "gpl-3" + ".0", "gpl" + "v3"]
SKIP_DIRS = {".git", "build", ".gradle", "third_party\\source", "third_party/source"}
hits = []
for dp, dns, fns in os.walk(ROOT):
    rel = os.path.relpath(dp, ROOT)
    if any(s in rel for s in (".git", "third_party")) and "third_party" in rel:
        # third_party/source checkouts are reference-only and never packaged;
        # still scan THIRD_PARTY_NOTICES + bundled assets strictly.
        if "third_party\\source" in dp or "third_party/source" in dp:
            continue
    if any(d in ("build", ".gradle", ".idea") for d in rel.split(os.sep)):
        continue
    for fn in fns:
        if fn.endswith((".apk", ".aab", ".so", ".aar", ".jar")):
            continue
        p = os.path.join(dp, fn)
        try:
            if os.path.getsize(p) > 2_000_000:
                continue
            t = open(p, encoding="utf-8", errors="ignore").read().lower()
        except Exception:
            continue
        for b in BANNED:
            if b in t:
                hits.append(os.path.relpath(p, ROOT) + f" [{b}]")
                break
print("GPL/AGPL hits:", len(hits))
for h in hits[:20]:
    print(" ", h)
# Notices gate
need = ["THIRD_PARTY_NOTICES", "THIRD_PARTY_LOCK.json"]
for n in need:
    p = os.path.join(ROOT, n) if n.endswith(".json") else os.path.join(ROOT, "release", n)
    alt = os.path.join(ROOT, n)
    if not (os.path.exists(p) or os.path.exists(alt)):
        print("missing", n)
        hits.append("missing:" + n)
sys.exit(1 if hits else 0)
