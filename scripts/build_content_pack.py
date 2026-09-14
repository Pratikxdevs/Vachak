"""Content pack builder — sat_Olck v0.2.0 (curriculum/class packs).

Bundles the deterministic curriculum/class/{1..5} output (chapters,
assignments, worksheets, flashcard decks, deduped images) + pack_summary.json
into a side-loadable .vachakpack for the offline SAF installer
(PackInstaller/PackManager — no network, ever).

Content-only pack: models array is empty; file integrity rides on
curriculum_files[] sha256 entries (PackInstaller accepts either).

Status honesty: every chapter ships AUTO_EXTRACTED; the manifest records
content_status accordingly. Never APPROVED.

Usage:
    python scripts/build_content_pack.py --version 0.2.0
    # output: packages/packs/sat_Olck-v0.2.0.vachakpack

Prereq: run scripts/build_pack_summary.py first so the asset copy is fresh.
"""
from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import os
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLASS_DIR = os.path.join(ROOT, "curriculum", "class")
SUMMARY_ASSET = os.path.join(
    ROOT, "android", "app", "src", "main", "assets", "curriculum", "pack_summary.json")
OUT_DIR = os.path.join(ROOT, "packages", "packs")


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def collect() -> list[tuple[str, str]]:
    """Returns [(arcname, realpath)] for everything shipped in the pack."""
    items: list[tuple[str, str]] = []
    for g in ("1", "2", "3", "4", "5"):
        gdir = os.path.join(CLASS_DIR, g)
        for dp, _, fns in os.walk(gdir):
            for fn in sorted(fns):
                real = os.path.join(dp, fn)
                arc = os.path.join("curriculum", "class", g, os.path.relpath(real, gdir))
                items.append((arc, real))
    if not os.path.exists(SUMMARY_ASSET):
        raise SystemExit("missing pack_summary.json — run scripts/build_pack_summary.py first")
    items.append(("curriculum/pack_summary.json", SUMMARY_ASSET))
    return items


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", default="0.2.0")
    ap.add_argument("--lang", default="sat_Olck")
    args = ap.parse_args()

    items = collect()
    entries = []
    total = 0
    for arc, real in items:
        size = os.path.getsize(real)
        total += size
        entries.append({"name": arc, "path": arc,
                        "sha256": sha256_file(real), "size": size})

    # Per-grade rollup from manifests (chapter counts only — no translations).
    grades = []
    for g in ("1", "2", "3", "4", "5"):
        m = json.load(open(os.path.join(CLASS_DIR, g, "manifest.json"), encoding="utf-8"))
        grades.append({"grade": int(g), "books": m.get("books", 0),
                       "chapters": len(m.get("chapters", {})),
                       "status": m.get("status", "AUTO_EXTRACTED")})

    manifest = {
        "packName": f"{args.lang}-v{args.version}.vachakpack",
        "language": args.lang,
        "version": args.version,
        "created": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d"),
        "kind": "curriculum-content",
        "content_status": "AUTO_EXTRACTED — speaker review required before APPROVED",
        "models": [],
        "curriculum_files": entries,
        "grades": grades,
        "licenses": [
            {"name": "Vachak curriculum/class packs (G1-G5)",
             "license": "CC BY 4.0 (textbook-derived, AUTO_EXTRACTED)",
             "source": "santali_organized textbooks"},
        ],
        "totalBytes": total,
    }

    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, manifest["packName"])
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for arc, real in items:
            z.write(real, arc)
        z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=1))

    print(f"[pack] {out} ({os.path.getsize(out)/1048576:.1f} MB zip, "
          f"{total/1048576:.1f} MB content, {len(entries)} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
