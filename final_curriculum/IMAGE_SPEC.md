# IMAGE_SPEC.md — Half-Page Images with Borders

Replaces the old 1541 unreadable tile-crops (avg 15-27KB, context-free). One `pages/` set serves chapter gallery + worksheet figures + flashcard art.

## What to export

* Split each chosen PDF page into **top half / bottom half**, cut at mid-page.
* KEEP print borders, captions, and page furniture inside the crop. Border = proof nothing was clipped.
* WebP, max-width 800px, quality ~70. Target ~80-120KB per half.
* 6-10 halves per chapter. Skip covers, publisher pages, blank backs.
* Prefer: key illustration spreads, worked examples, exercise pages referenced by worksheet items.

## Naming

```
pages/p01_top.webp
pages/p01_bottom.webp
pages/p02_top.webp
...
```

`pNN` = source PDF page number (1-indexed). Only `_top` / `_bottom` suffixes. Referenced exactly as `pages/p02_top.webp` in `worksheets.json` / `flashcards.json` / gallery order = filename sort.

To omit a boring half, just don't export it (e.g. keep `p04_top.webp`, drop `p04_bottom.webp`). Never ship placeholder/blank images.

## How to cut (any tool)

* `pdftoppm -png -r 150 <pdf> page` then halve each PNG at 50% height with 0% overlap, convert to WebP (`cwebp -q 70`); or
* Screenshot halves in any PDF reader at ~150% zoom, save WebP.
* Verify: open each `.webp` at phone width — text/figures legible, border visible, no half-cut lines of text at the split if avoidable (nudge cut to a whitespace gap ±5%).

## Budget check

~67 chapters x ~8 halves x ~100KB ≈ 54MB. Fits the 500MB device budget with room for APK + models + audio. If a chapter needs more than 10 halves, pick harder — device storage is the constraint.
