"""Trim curriculum/class/3 assets to fit the ~20MB budget.

Deterministic, offline. Keeps the 30 largest illustrations per chapter
(plus any asset referenced by that chapter's flashcard deck, pinned so
decks never break), converts them to 480px WebP q65, deletes the rest,
and rewrites all references (chapter.json image_refs + deck image_ref).

Projected: 58.5MB JPEG -> ~14MB WebP + ~1.2MB JSON ~= ~15MB total.

Usage:
    python scripts/trim_class3_assets.py [--out curriculum/class/3]
"""
from __future__ import annotations

import argparse
import glob
import io
import json
import os

from PIL import Image

KEEP_PER_CHAPTER = 30
MAX_DIM = 480
WEBP_Q = 65


def finalize(out: str) -> dict:
    chapters = sorted(glob.glob(os.path.join(out, "chapters", "*", "chapter.json")))
    new_map: dict[str, str] = {}  # old asset path -> new asset path
    keep: set[str] = set()
    per_ch_kept: dict[str, list[str]] = {}

    for ch_path in chapters:
        ch = json.load(open(ch_path, encoding="utf-8"))
        refs = [r["asset"] for r in ch.get("image_refs", [])]
        uniq = list(dict.fromkeys(refs))
        deck_path = os.path.join(os.path.dirname(ch_path), "flashcards", "deck_bilingual.json")
        pinned: set[str] = set()
        if os.path.exists(deck_path):
            deck = json.load(open(deck_path, encoding="utf-8"))
            for c in deck.get("cards", []):
                ir = c.get("image_ref")
                if ir and ir.startswith("assets/"):
                    pinned.add(ir)
        # rank by current file size, largest first
        uniq.sort(key=lambda a: os.path.getsize(os.path.join(out, a))
                  if os.path.exists(os.path.join(out, a)) else 0, reverse=True)
        kept = uniq[:KEEP_PER_CHAPTER]
        for p in pinned:
            if p in uniq and p not in kept:
                kept.append(p)
        per_ch_kept[ch["slug"]] = kept
        keep.update(kept)

    # convert kept -> webp
    total_new = 0
    for old in sorted(keep):
        src = os.path.join(out, old)
        if not os.path.exists(src):
            continue
        digest = os.path.splitext(os.path.basename(old))[0].split("_", 1)[1]
        new_name = f"img_{digest}.webp"
        dst = os.path.join(out, "assets", new_name)
        if not os.path.exists(dst):
            im = Image.open(src).convert("RGB")
            if max(im.size) > MAX_DIM:
                im.thumbnail((MAX_DIM, MAX_DIM))
            buf = io.BytesIO()
            im.save(buf, "WEBP", quality=WEBP_Q, method=4)
            with open(dst, "wb") as f:
                f.write(buf.getvalue())
        total_new += os.path.getsize(dst)
        new_map[old] = f"assets/{new_name}"

    # rewrite chapter refs (drop unkept) + deck refs (null + flag if dropped)
    dropped_chapters = 0
    for ch_path in chapters:
        ch = json.load(open(ch_path, encoding="utf-8"))
        new_refs = []
        for r in ch.get("image_refs", []):
            if r["asset"] in new_map:
                r["asset"] = new_map[r["asset"]]
                new_refs.append(r)
            else:
                dropped_chapters += 1
        ch["image_refs"] = new_refs
        json.dump(ch, open(ch_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

        deck_path = os.path.join(os.path.dirname(ch_path), "flashcards", "deck_bilingual.json")
        if os.path.exists(deck_path):
            deck = json.load(open(deck_path, encoding="utf-8"))
            for c in deck.get("cards", []):
                ir = c.get("image_ref")
                if ir and ir.startswith("assets/"):
                    if ir in new_map:
                        c["image_ref"] = new_map[ir]
                    else:
                        c["image_ref"] = None
                        if "image_ref" not in c.get("needs_review", []):
                            c["needs_review"] = c.get("needs_review", []) + ["image_ref"]
            json.dump(deck, open(deck_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    # delete everything in assets/ not in the new set
    new_files = set(new_map.values())
    removed_n, removed_b = 0, 0
    for f in glob.glob(os.path.join(out, "assets", "*")):
        rel = f"assets/{os.path.basename(f)}"
        if rel not in new_files:
            removed_b += os.path.getsize(f)
            os.remove(f)
            removed_n += 1

    # manifest + review note
    m_path = os.path.join(out, "manifest.json")
    m = json.load(open(m_path, encoding="utf-8"))
    m["trim"] = {"tool": "scripts/trim_class3_assets.py (deterministic, offline)",
                 "keep_per_chapter": KEEP_PER_CHAPTER, "max_dim": MAX_DIM,
                 "webp_q": WEBP_Q, "kept_files": len(new_files),
                 "removed_files": removed_n,
                 "assets_bytes": total_new,
                 "dropped_chapter_refs": dropped_chapters}
    m["unique_assets"] = len(new_files)
    json.dump(m, open(m_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    with open(os.path.join(out, "REVIEW.md"), "a", encoding="utf-8") as f:
        f.write(f"\n## Asset trim (budget)\n- Kept {len(new_files)} files ({total_new / 1048576:.1f} MB WebP {MAX_DIM}px q{WEBP_Q}), "
                f"removed {removed_n} ({removed_b / 1048576:.1f} MB). Cap: {KEEP_PER_CHAPTER} largest/chapter + deck-pinned. "
                f"Dropped chapter refs: {dropped_chapters} (deck refs nulled + flagged, never guessed).\n")

    stats = {"kept_files": len(new_files), "removed_files": removed_n,
             "assets_mb": round(total_new / 1048576, 1),
             "removed_mb": round(removed_b / 1048576, 1)}
    print(f"[trim] kept={stats['kept_files']} ({stats['assets_mb']} MB) "
          f"removed={stats['removed_files']} ({stats['removed_mb']} MB)")
    return stats


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "curriculum", "class", "3"))
    args = ap.parse_args()
    finalize(args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
