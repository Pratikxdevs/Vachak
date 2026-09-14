"""Generate android asset pack_summary.json from curriculum/class manifests.

Reads curriculum/class/{1..5}/manifest.json (deterministic, offline) and emits
a SMALL (~10KB) summary for the app UI:
  android/app/src/main/assets/curriculum/pack_summary.json

Per grade: chapter list (title humanized from slug, subject, pages,
worksheets, flashcards, assignments, images, text encoding), totals,
pack bytes, review status, skipped notes.

Honesty rules (per AGENTS.md):
  * Titles are presentation-only derivations from slugs (title_source=slug).
  * status is AUTO_EXTRACTED everywhere — the UI must render these as
    review-pending source material, never as approved lessons.
  * No Ol Chiki / Hindi translations are invented here; counts only.

Usage:
    python scripts/build_pack_summary.py
"""
from __future__ import annotations

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "curriculum", "pack_summary.json")


def humanize(slug: str) -> str:
    return " ".join(w.capitalize() for w in slug.replace("_", "-").split("-") if w)


def main() -> int:
    grades = []
    for g in (1, 2, 3, 4, 5):
        m_path = os.path.join(ROOT, "curriculum", "class", str(g), "manifest.json")
        if not os.path.exists(m_path):
            print(f"[skip] no manifest for grade {g}")
            continue
        m = json.load(open(m_path, encoding="utf-8"))
        chapters = []
        for slug in sorted(m.get("chapters", {})):
            s = m["chapters"][slug]
            chapters.append({
                "slug": slug,
                "title": humanize(slug),
                "title_source": "slug",
                "subject": s.get("subject", ""),
                "grade": s.get("grade", g),
                "pages": s.get("pages", 0),
                "assignments": s.get("assignments", 0),
                "worksheets": s.get("worksheet_items", 0),
                "flashcards": s.get("flashcards", 0),
                "images": s.get("images", 0),
            })
        tot = lambda k: sum(c[k] for c in chapters)
        # Class 3 was budget-trimmed after its manifest stats were written;
        # prefer the post-trim file count when present.
        trim = m.get("trim") or {}
        images_total = trim.get("kept_files", tot("images"))
        pack_dir = os.path.join(ROOT, "curriculum", "class", str(g))
        pack_bytes = sum(
            os.path.getsize(os.path.join(dp, f))
            for dp, _, fns in os.walk(pack_dir) for f in fns
        )
        grades.append({
            "grade": g,
            "status": "AUTO_EXTRACTED",
            "books": m.get("books", len(chapters)),
            "chapters": chapters,
            "totals": {
                "chapters": len(chapters),
                "pages": tot("pages"),
                "assignments": tot("assignments"),
                "worksheets": tot("worksheets"),
                "flashcards": tot("flashcards"),
                "images": images_total,
            },
            "pack_bytes": pack_bytes,
            "skipped": m.get("skipped", []),
            "note": m.get("note", ""),
        })
    summary = {
        "generator": "scripts/build_pack_summary.py (deterministic, offline)",
        "status": "AUTO_EXTRACTED — speaker review required before APPROVED. Render as review-pending source material, never as approved lessons.",
        "grades": grades,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
    print(f"[done] {OUT} ({os.path.getsize(OUT)} bytes, {len(grades)} grades)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
