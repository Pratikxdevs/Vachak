"""Extract Devanagari text layer from Santali textbook PDFs (no OCR — born-digital).

Produces per book:
  <name>_raw.txt    — straight text-layer dump (proof of what the PDF holds)
  <name>_clean.txt  — after safe cleanup (doubled matra/halant/nukta collapse)
  <name>_stats.json — quality metrics + unrecoverable-char inventory

Safe-collapse rule: dependent vowel signs, halant and nukta NEVER legitimately
double in Devanagari, so runs collapse to one. Independent vowels, consonants,
digits and the mystery glyphs (@ & ! 0 U+FFFD) are left untouched and counted
for human review.

Usage:
    python scripts/extract_book_text.py --src santali_pdfs --out samples \
        --books Class_2__Fun_at_the_Fair__Santhali_.pdf ...
"""
from __future__ import annotations

import argparse
import json
import os
import re
import unicodedata

try:
    import pymupdf
except ImportError:  # pragma: no cover
    raise SystemExit("need pymupdf: pip install pymupdf")

# Dependent vowel signs (matras) + halant + nukta: runs of these collapse to one.
_COLLAPSIBLE = (
    "\u093e\u093f\u0940\u0941\u0942\u0943\u0944\u0945\u0946\u0947\u0948"
    "\u0949\u094a\u094b\u094c\u094d\u093c\u0901\u0902\u0903"
)
_COLLAPSE_RE = re.compile(f"([{_COLLAPSIBLE}])\\1+")

# Glyphs with broken/missing ToUnicode mappings (vary by extractor): pymupdf
# renders them as @ & ! 0, poppler as U+FFFD. Never touch — flag for review.
MYSTERY_CHARS = ("@", "&", "!", "0", "\ufffd")


def cleanup(text: str) -> tuple[str, dict]:
    """Collapse doubled shaping artifacts. Returns (cleaned, counts)."""
    counts = {}
    counts["collapsed_runs"] = len(_COLLAPSE_RE.findall(text))
    cleaned = _COLLAPSE_RE.sub(r"\1", text)
    counts["mystery"] = {
        ch: cleaned.count(ch) for ch in MYSTERY_CHARS if cleaned.count(ch)
    }
    counts["ol_chiki"] = sum(1 for c in cleaned if "\u1c50" <= c <= "\u1c7f")
    counts["devanagari"] = sum(
        1 for c in cleaned if "\u0900" <= c <= "\u097f"
    )
    return cleaned, counts


def extract_pages(path: str) -> list[str]:
    doc = pymupdf.open(path)
    return [page.get_text() for page in doc]


def main() -> int:
    ap = argparse.ArgumentParser(description="Extract + clean textbook text layer")
    ap.add_argument("--src", default="santali_pdfs")
    ap.add_argument("--out", default="samples")
    ap.add_argument("--books", nargs="+", required=True)
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    for book in args.books:
        src = os.path.join(args.src, book)
        name = os.path.splitext(os.path.basename(book))[0]
        short = re.sub(r"__+", "_", name).strip("_")
        pages = extract_pages(src)
        raw = "\n".join(f"===== PAGE {i + 1} =====\n{p}" for i, p in enumerate(pages))
        cleaned, counts = cleanup(raw)
        # keep page markers aligned in cleaned file
        with open(os.path.join(args.out, f"{short}_raw.txt"), "w", encoding="utf-8") as f:
            f.write(raw)
        with open(os.path.join(args.out, f"{short}_clean.txt"), "w", encoding="utf-8") as f:
            f.write(cleaned)
        stats = {
            "book": book,
            "pages": len(pages),
            "raw_chars": len(raw),
            "cleaned_chars": len(cleaned),
            **counts,
        }
        with open(os.path.join(args.out, f"{short}_stats.json"), "w", encoding="utf-8") as f:
            json.dump(stats, f, ensure_ascii=False, indent=2)
        print(f"[extract] {short}: {len(pages)}p raw={len(raw)} clean={len(cleaned)} "
              f"collapsed={counts['collapsed_runs']} mystery={counts['mystery']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
