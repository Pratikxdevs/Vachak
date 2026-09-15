"""Convert user-supplied authored chapters (/1, /3) into the pack layout.

Reads the authored schema (chapter.json with title/intro/outcomes in
{hi, sat_ol}, worksheets.json items, flashcards.json cards, pages/*.webp)
and emits the EXISTING pack-schema files that PackContentReader already
understands — no reader rewrite needed:

  chapters/<slug>/chapter.json                        (pack schema)
  chapters/<slug>/assignments.json                    (honest {"items": []})
  chapters/<slug>/worksheets/ws_<slug>_bilingual.json (questions[])
  chapters/<slug>/flashcards/deck_bilingual.json      (cards[])
  chapters/<slug>/pages/*.webp                        (byte-identical)
  chapters/<slug>/source/*                            (verbatim inputs)

Rules (per Phase 12 CONTEXT):
  * Deterministic: same inputs => byte-identical outputs (sorted traversal,
    fixed key order, no timestamps).
  * Source `status` flows VERBATIM; anything outside {"DRAFT"} => exit 5.
  * Every flashcard back_sat_ol must carry Ol Chiki (U+1C50-U+1C7F) => exit 4.
  * Blank worksheet prompts => exit 4 naming the item id.
  * Missing chapter.json => exit 3 (caller reserves the slot, never invents).
  * NEVER touches /1 or /3 (read-only inputs). NEVER invents content.

Usage:
    python3 scripts/convert_authored_chapters.py --src /cz/Vachak/1 --grade 1
    python3 scripts/convert_authored_chapters.py --src /cz/Vachak/3 --grade 3
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_ROOT = os.path.join(ROOT, "curriculum", "class")
MAP_DIR = os.path.join(ROOT, ".planning", "phases", "12-pdf-worksheets")

ALLOWED_STATUS = {"DRAFT"}


def _ol_chiki_present(s: str) -> bool:
    return any("\u1c50" <= ch <= "\u1c7f" for ch in (s or ""))


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def convert_chapter(src_dir: str, grade: int, out_chapters: str) -> dict:
    slug = os.path.basename(os.path.normpath(src_dir))
    ch_path = os.path.join(src_dir, "chapter.json")
    ws_path = os.path.join(src_dir, "worksheets.json")
    fc_path = os.path.join(src_dir, "flashcards.json")
    pages_dir = os.path.join(src_dir, "pages")
    if not os.path.isfile(ch_path):
        print(f"[convert] {slug}: missing chapter.json", file=sys.stderr)
        raise SystemExit(3)

    with open(ch_path, encoding="utf-8") as f:
        ch = json.load(f)
    with open(ws_path, encoding="utf-8") as f:
        ws = json.load(f)
    with open(fc_path, encoding="utf-8") as f:
        fc = json.load(f)

    for name, doc in (("chapter", ch), ("worksheets", ws), ("flashcards", fc)):
        st = doc.get("status")
        if st not in ALLOWED_STATUS:
            print(f"[convert] {slug}: {name}.status={st!r} not in {sorted(ALLOWED_STATUS)}",
                  file=sys.stderr)
            raise SystemExit(5)

    intro = ch.get("intro", {}) or {}
    outcomes = ch.get("outcomes", []) or []
    sat_lines = [intro.get("sat_ol", "")] + [
        (o.get("text", {}) or {}).get("sat_ol", "") for o in outcomes
    ]
    hi_lines = [intro.get("hi", "")] + [
        (o.get("text", {}) or {}).get("hi", "") for o in outcomes
    ]
    text_sat = "\n".join(t for t in sat_lines if t)
    text_hi = "\n".join(t for t in hi_lines if t)
    title = ch.get("title", {}) or {}

    page_files = []
    if os.path.isdir(pages_dir):
        page_files = sorted(
            f for f in os.listdir(pages_dir)
            if os.path.isfile(os.path.join(pages_dir, f))
        )

    out_dir = os.path.join(out_chapters, slug)
    os.makedirs(os.path.join(out_dir, "worksheets"), exist_ok=True)
    os.makedirs(os.path.join(out_dir, "flashcards"), exist_ok=True)
    os.makedirs(os.path.join(out_dir, "pages"), exist_ok=True)
    os.makedirs(os.path.join(out_dir, "source"), exist_ok=True)

    for f in page_files:
        shutil.copyfile(os.path.join(pages_dir, f), os.path.join(out_dir, "pages", f))
    for name in ("chapter.json", "worksheets.json", "flashcards.json"):
        shutil.copyfile(os.path.join(src_dir, name), os.path.join(out_dir, "source", name))

    questions = []
    for it in ws.get("items", []):
        prompt = it.get("prompt", {}) or {}
        sat = prompt.get("sat_ol", "") or ""
        hi = prompt.get("hi", "") or ""
        if not sat.strip() and not hi.strip():
            print(f"[convert] {slug}: blank prompt in item {it.get('id')!r}",
                  file=sys.stderr)
            raise SystemExit(4)
        questions.append({
            "id": it.get("id", ""),
            "type": it.get("type", "practice"),
            "sequence": it.get("sequence", 0),
            "prompt_sat_deva": sat,
            "prompt_hi": hi,
            "answer": it.get("answer"),
            "image_ref": it.get("image_ref"),
            "render_count": 0,
            "needs_review": ["DRAFT"],
        })

    cards = []
    for c in fc.get("cards", []):
        back = c.get("back_sat_ol", "") or ""
        if not _ol_chiki_present(back):
            print(f"[convert] {slug}: no Ol Chiki in card {c.get('card_id')!r}",
                  file=sys.stderr)
            raise SystemExit(4)
        cards.append({
            "card_id": c.get("card_id", ""),
            "sequence": c.get("sequence", 0),
            "concept_hi": c.get("front_hi", "") or "",
            "concept_target": back,
            "image_ref": c.get("image_ref"),
            "needs_review": ["DRAFT"],
        })

    pack_chapter = {
        "chapter_id": ch.get("chapter_id", slug),
        "grade": grade,
        "subject": ch.get("subject_id", ""),
        "slug": slug,
        "status": ch.get("status"),
        "text_encoding": "ol-chiki",
        "text_sat_deva": text_sat,
        "text_hi": text_hi,
        "title_hi": title.get("hi", ""),
        "title_sat_ol": title.get("sat_ol", ""),
        "image_refs": [{"asset": "pages/" + f} for f in page_files],
        "notices": [] if page_files else ["No page art shipped for this chapter — text only."],
        "provenance": {
            "source_dir": os.path.abspath(src_dir),
            "source_pdf": ch.get("source_pdf", ""),
            "chapter_sha256": sha256_file(ch_path),
            "worksheets_sha256": sha256_file(ws_path),
            "flashcards_sha256": sha256_file(fc_path),
        },
    }

    def dump(obj: object, path: str) -> None:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=1, sort_keys=True)
            f.write("\n")

    dump(pack_chapter, os.path.join(out_dir, "chapter.json"))
    dump({"items": []}, os.path.join(out_dir, "assignments.json"))
    dump({"worksheet_id": ws.get("worksheet_id", ""),
          "chapter_id": ch.get("chapter_id", slug),
          "status": ws.get("status"),
          "questions": questions},
         os.path.join(out_dir, "worksheets", f"ws_{slug}_bilingual.json"))
    dump({"deck_id": fc.get("deck_id", ""),
          "chapter_id": ch.get("chapter_id", slug),
          "status": fc.get("status"),
          "cards": cards},
         os.path.join(out_dir, "flashcards", "deck_bilingual.json"))

    return {
        "slug": slug,
        "grade": grade,
        "source_dir": os.path.abspath(src_dir),
        "chapter_sha256": sha256_file(ch_path),
        "worksheets_sha256": sha256_file(ws_path),
        "flashcards_sha256": sha256_file(fc_path),
        "questions": len(questions),
        "cards": len(cards),
        "pages": len(page_files),
        "status": ch.get("status"),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="authored chapters dir (/1 or /3)")
    ap.add_argument("--grade", required=True, type=int)
    ap.add_argument("--map-name", default=None,
                    help="mapping json basename (default authored_mapping_g{G}.json)")
    ap.add_argument("--exclude", default="",
                    help="comma-separated slugs to skip (reserved separately)")
    args = ap.parse_args()

    src = os.path.abspath(args.src)
    out_chapters = os.path.join(OUT_ROOT, str(args.grade), "chapters")
    os.makedirs(out_chapters, exist_ok=True)
    os.makedirs(MAP_DIR, exist_ok=True)

    rows = []
    excluded = {s.strip() for s in args.exclude.split(",") if s.strip()}
    for slug in sorted(os.listdir(src)):
        if slug in excluded:
            print(f"[convert] g{args.grade}/{slug}: excluded (reserved slot)")
            continue
        sdir = os.path.join(src, slug)
        if not os.path.isdir(sdir):
            continue
        rows.append(convert_chapter(sdir, args.grade, out_chapters))
        print(f"[convert] g{args.grade}/{slug}: "
              f"{rows[-1]['questions']}q {rows[-1]['cards']}c {rows[-1]['pages']}p")

    map_name = args.map_name or f"authored_mapping_g{args.grade}.json"
    with open(os.path.join(MAP_DIR, map_name), "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")
    print(f"[convert] wrote {map_name} ({len(rows)} chapters)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
