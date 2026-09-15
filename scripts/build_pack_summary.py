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


def scan_chapters(g: int) -> dict:
    """Fallback when manifest.json is absent: scan converted chapter dirs.

    Reads curriculum/class/{g}/chapters/*/chapter.json (+ counts from the
    emitted worksheets/deck/assignments files). Deterministic (sorted).
    """
    ch_root = os.path.join(ROOT, "curriculum", "class", str(g), "chapters")
    chapters: dict = {}
    if not os.path.isdir(ch_root):
        return chapters
    for slug in sorted(os.listdir(ch_root)):
        cdir = os.path.join(ch_root, slug)
        cpath = os.path.join(cdir, "chapter.json")
        if not (os.path.isdir(cdir) and os.path.isfile(cpath)):
            continue
        c = json.load(open(cpath, encoding="utf-8"))
        ws_path = os.path.join(cdir, "worksheets", f"ws_{slug}_bilingual.json")
        deck_path = os.path.join(cdir, "flashcards", "deck_bilingual.json")
        as_path = os.path.join(cdir, "assignments.json")
        try:
            n_q = len(json.load(open(ws_path, encoding="utf-8")).get("questions", []))
        except (OSError, ValueError):
            n_q = 0
        try:
            n_c = len(json.load(open(deck_path, encoding="utf-8")).get("cards", []))
        except (OSError, ValueError):
            n_c = 0
        try:
            n_a = len(json.load(open(as_path, encoding="utf-8")).get("items", []))
        except (OSError, ValueError):
            n_a = 0
        n_img = len(c.get("image_refs", []) or [])
        title_hi = (c.get("title_hi") or "").strip()
        chapters[slug] = {
            "subject": c.get("subject", ""),
            "grade": c.get("grade", g),
            "pages": 0,
            "assignments": n_a,
            "worksheet_items": n_q,
            "flashcards": n_c,
            "images": n_img,
            "title": title_hi,
            "title_source": "authored" if title_hi else "slug",
            "has_worksheet_pdf": bool((c.get("worksheet_pdf") or "").strip()),
            "has_explicit": os.path.isdir(os.path.join(cdir, "source")),
            "status": c.get("status", "AUTO_EXTRACTED"),
        }
    return chapters


def main() -> int:
    grades = []
    for g in (1, 2, 3, 4, 5):
        m_path = os.path.join(ROOT, "curriculum", "class", str(g), "manifest.json")
        if os.path.exists(m_path):
            m = json.load(open(m_path, encoding="utf-8"))
            chapter_stats = m.get("chapters", {})
            skipped, note, books = m.get("skipped", []), m.get("note", ""), m.get("books", 0)
            trim = m.get("trim") or {}
        else:
            # Converted tree without a builder manifest (Phase 12): scan dirs.
            print(f"[scan] no manifest for grade {g} — scanning chapter dirs")
            chapter_stats = scan_chapters(g)
            if not chapter_stats:
                print(f"[skip] no chapters for grade {g}")
                continue
            skipped, note, books, trim = [], "scanned from converted chapter dirs", 0, {}
        # Demo-set scoping (Phase 12): exactly the wired slugs, in order.
        # Legacy chapter dirs stay on disk/in-pack; only the listing narrows.
        demo_path = os.path.join(ROOT, "curriculum", "class", str(g), "demo_set.json")
        if os.path.isfile(demo_path):
            wanted = json.load(open(demo_path, encoding="utf-8"))
            missing = [s for s in wanted if s not in chapter_stats]
            if missing:
                print(f"[warn] grade {g} demo_set slugs missing from tree: {missing}")
            ordered = [s for s in wanted if s in chapter_stats]
        else:
            ordered = sorted(chapter_stats)
        chapters = []
        for slug in ordered:
            s = chapter_stats[slug]
            title = (s.get("title") or "").strip() or humanize(slug)
            chapters.append({
                "slug": slug,
                "title": title,
                "title_source": s.get("title_source", "slug"),
                "subject": s.get("subject", ""),
                "grade": s.get("grade", g),
                "pages": s.get("pages", 0),
                "assignments": s.get("assignments", 0),
                "worksheets": s.get("worksheet_items", 0),
                "flashcards": s.get("flashcards", 0),
                "images": s.get("images", 0),
                "has_worksheet_pdf": bool(s.get("has_worksheet_pdf", False)),
                "has_explicit": bool(s.get("has_explicit", False)),
                "status": s.get("status", "AUTO_EXTRACTED"),
            })
        tot = lambda k: sum(c[k] for c in chapters)
        # Class 3 was budget-trimmed after its manifest stats were written;
        # prefer the post-trim file count when present (manifest path only).
        trim = (m.get("trim") or {}) if os.path.exists(m_path) else {}
        images_total = trim.get("kept_files", tot("images"))
        pack_dir = os.path.join(ROOT, "curriculum", "class", str(g))
        pack_bytes = sum(
            os.path.getsize(os.path.join(dp, f))
            for dp, _, fns in os.walk(pack_dir) for f in fns
        )
        grades.append({
            "grade": g,
            "status": "AUTO_EXTRACTED",
            "books": books or len(chapters),
            "chapters": chapters,
            "totals": {
                "chapters": len(chapters),
                "pages": tot("pages"),
                "assignments": tot("assignments"),
                "worksheets": tot("worksheets"),
                "flashcards": tot("flashcards"),
                "images": images_total,
                "explicit": sum(1 for c in chapters if c["has_explicit"]),
                "pdf_chapters": sum(1 for c in chapters if c["has_worksheet_pdf"]),
            },
            "pack_bytes": pack_bytes,
            "skipped": skipped,
            "note": note,
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
