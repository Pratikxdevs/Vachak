"""Select the smallest textbook PDFs and wire the 20 pool slots (Phase 12).

Deterministic: sorts santali_organized/**/*.pdf by bytes, takes --pool-size
smallest, validates with pymupdf, copies to curriculum/class/pdf_pool/ under
ORIGINAL basenames, creates/fills the 20 pool-slot chapter dirs
(G2x6, G4x6, G5x6, G1-6th, G3-6th) with worksheet_pdf refs, writes
demo_set.json for grades 2/4/5 and pdf_slot_mapping.json (audit trail).

Rules:
  * Pool total > --max-bytes (default 25 MB) => exit 2.
  * Unopenable / zero-page PDF => exit 3 naming the file.
  * Refs are relative pdf_pool/<basename>; '..'/absolute => exit 4.
  * Chapter dirs for genuinely-new slugs are CREATED; existing dirs get ONLY
    the worksheet_pdf/pdf_sha256/provenance keys added (nothing else touched).

Usage:
    python3 scripts/select_pdf_pool.py
"""
from __future__ import annotations

import argparse
import glob
import hashlib
import json
import os
import shutil
import sys

try:
    import pymupdf
except ImportError:
    raise SystemExit("need pymupdf")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CORPUS = os.path.join(ROOT, "santali_organized")
POOL_DIR = os.path.join(ROOT, "curriculum", "class", "pdf_pool")
MAP_DIR = os.path.join(ROOT, ".planning", "phases", "12-pdf-worksheets")

# (grade, slug, subject, pool-index-hint, reason)
# pool index resolved after size-sort; hint is informational only.
SLOTS = [
    # Grade 2 (Mathematics)
    (2, "playing-with-lines", "Mathematics", "same-grade"),
    (2, "grouping-and-sharing", "Mathematics", "smallest-fill"),
    (2, "fun-at-the-fair", "Mathematics", "smallest-fill"),
    (2, "shapes-around-us", "Mathematics", "smallest-fill"),
    (2, "shadow-story", "Mathematics", "smallest-fill"),
    (2, "decoration-for-festival", "Mathematics", "smallest-fill"),
    # Grade 4 (mixed)
    (4, "ticking-clocks-and-turning-calendar", "Mathematics", "same-grade"),
    (4, "elephants-tigers-and-leopards", "Environmental Studies", "same-grade"),
    (4, "equal-groups", "Mathematics", "smallest-fill"),
    (4, "measuring-length", "Mathematics", "smallest-fill"),
    (4, "the-transport-museum", "Mathematics", "smallest-fill"),
    (4, "the-cleanest-village", "Environmental Studies", "smallest-fill"),
    # Grade 5 (existing slugs + 1 new)
    (5, "music-around-me", "Arts", "smallest-fill"),
    (5, "objects-on-the-move", "Arts", "smallest-fill"),
    (5, "little-steps-g4", "Physical Education & Well Being", "smallest-fill"),
    (5, "strike-the-shuttlecock", "Physical Education & Well Being", "smallest-fill"),
    (5, "yoga-sadhana", "Physical Education & Well Being", "smallest-fill"),
    (5, "revision-practice", "Mathematics", "smallest-fill"),
    # 6th-slots for G1/G3 (dirs created in T2; refs filled here)
    (1, "fun-with-numbers-21-99", "Mathematics", "same-grade"),
    (3, "whats-in-a-name", "Mathematics", "same-subject"),
]


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pool-size", type=int, default=5)
    ap.add_argument("--max-bytes", type=int, default=25 * 1024 * 1024)
    args = ap.parse_args()

    all_pdfs = [(os.path.getsize(p), p) for p in
                glob.glob(os.path.join(CORPUS, "**", "*.pdf"), recursive=True)]
    if len(all_pdfs) < args.pool_size:
        print(f"[pool] only {len(all_pdfs)} PDFs found", file=sys.stderr)
        return 2
    all_pdfs.sort()
    pool = all_pdfs[: args.pool_size]
    total = sum(s for s, _ in pool)
    print(f"[pool] {len(pool)} files, {total / 1048576:.1f} MB")
    if total > args.max_bytes:
        print(f"[pool] OVER BUDGET: {total} > {args.max_bytes}", file=sys.stderr)
        return 2

    os.makedirs(POOL_DIR, exist_ok=True)
    os.makedirs(MAP_DIR, exist_ok=True)
    pool_meta = []
    for size, path in pool:
        base = os.path.basename(path)
        if ".." in base or os.path.isabs(base):
            print(f"[pool] bad basename {base!r}", file=sys.stderr)
            return 4
        try:
            doc = pymupdf.open(path)
            pages = doc.page_count
            doc.close()
        except Exception as e:  # noqa: BLE001
            print(f"[pool] unreadable {path}: {e}", file=sys.stderr)
            return 3
        if pages < 1:
            print(f"[pool] zero pages {path}", file=sys.stderr)
            return 3
        shutil.copyfile(path, os.path.join(POOL_DIR, base))
        pool_meta.append({"basename": base, "size": size,
                          "sha256": sha256_file(path), "pages": pages,
                          "source": os.path.relpath(path, ROOT)})
        print(f"[pool] {size / 1048576:.1f} MB {pages}p {base}")

    # Round-robin pool files over slots; same-grade/subject hints pick the
    # first pool member whose source grade matches, else smallest-first.
    def pick(grade: int, reason: str, i: int) -> dict:
        if reason in ("same-grade", "same-subject"):
            for pm in pool_meta:
                if f"Class-{grade}" in pm["source"].replace("_", "-") \
                        or f"Class_{grade}__" in pm["source"]:
                    return pm
        return pool_meta[i % len(pool_meta)]

    mapping = []
    for i, (grade, slug, subject, reason) in enumerate(SLOTS):
        pm = pick(grade, reason, i)
        cdir = os.path.join(ROOT, "curriculum", "class", str(grade), "chapters", slug)
        os.makedirs(os.path.join(cdir, "worksheets"), exist_ok=True)
        os.makedirs(os.path.join(cdir, "flashcards"), exist_ok=True)
        cpath = os.path.join(cdir, "chapter.json")
        if os.path.isfile(cpath):
            ch = json.load(open(cpath, encoding="utf-8"))
        else:
            ch = {"chapter_id": f"CH-G{grade}-" + slug.upper().replace("-", "_")[:24],
                  "grade": grade, "slug": slug, "status": "AUTO_EXTRACTED",
                  "subject": subject, "text_encoding": "ol-chiki",
                  "text_hi": "", "text_sat_deva": "", "title_hi": "",
                  "title_sat_ol": "", "image_refs": [],
                  "notices": ["Textbook PDF — speaker review pending."]}
            json.dump(ch, open(cpath, "w", encoding="utf-8"),
                      ensure_ascii=False, indent=1, sort_keys=True)
        ch["worksheet_pdf"] = "pdf_pool/" + pm["basename"]
        ch["pdf_sha256"] = pm["sha256"]
        ch.setdefault("provenance", {})["source_pdf"] = pm["source"]
        with open(cpath, "w", encoding="utf-8") as f:
            json.dump(ch, f, ensure_ascii=False, indent=1, sort_keys=True)
            f.write("\n")
        mapping.append({"grade": grade, "slug": slug, "subject": subject,
                        "pool_file": pm["basename"], "sha256": pm["sha256"],
                        "reason": reason})

    # demo_set.json for pool grades (exact 6, mapping order)
    for g in (2, 4, 5):
        slugs = [m["slug"] for m in mapping if m["grade"] == g]
        assert len(slugs) == 6, (g, slugs)
        p = os.path.join(ROOT, "curriculum", "class", str(g), "demo_set.json")
        json.dump(slugs, open(p, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        open(p, "a").write("\n")

    with open(os.path.join(MAP_DIR, "pdf_slot_mapping.json"), "w", encoding="utf-8") as f:
        json.dump({"pool": pool_meta, "slots": mapping}, f,
                  ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")
    print(f"[pool] wrote pdf_slot_mapping.json ({len(mapping)} slots)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
