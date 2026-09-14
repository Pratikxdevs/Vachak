# CHECKLIST.md — Per-Chapter Gate

Copy this section into each review. Chapter passes only if every box ticks.

## Author self-check

- [ ] Folder is `chapters/{true-grade}/{slug}/` (grade corrected, slug matches folder).
- [ ] `chapter.json`: title/intro/teacher_note bilingual, 3-5 coded outcomes, 5-10 vocab, `source_pdf` exists, `estimated_minutes` in {30,40,45}.
- [ ] Every `sat_ol` shows Ol Chiki glyphs (᱐-᱙, ᱟ-᱿), no Devanagari-as-Santali, no Krishna soup, no `�`.
- [ ] `pages/`: 6-10 halves, named `pNN_top|bottom.webp`, borders kept, legible at phone width, each < 200KB.
- [ ] `worksheets.json`: 4-9 items, types from closed set only, every `answer` non-null, `image_ref`s exist.
- [ ] `flashcards.json`: 10-20 cards, chapter words (not 1-10 filler), no nulls, `image_ref`s exist or omitted.
- [ ] `status` set honestly: `DRAFT` if any Ol Chiki/answer unsure.

## Reviewer sign-off (second speaker for REVIEWED, SME for APPROVED)

- [ ] Read Hindi + Ol Chiki aloud — spelling/meaning correct, numerals are Ol Chiki digits.
- [ ] Outcomes match chapter content; vocab are real chapter words.
- [ ] Solved every worksheet item from the prompt alone; answers exact.
- [ ] Flipped every flashcard; back side unambiguous.
- [ ] Opened every `pages/` image; correct page, right half, border intact.

```
chapter_id: CH-G_-________   reviewer: ________   date: ________
verdict: REVIEWED | APPROVED | REJECTED   notes: _______________
```

REJECTED returns to author with notes — never downgrade to APPROVED-by-default. `curriculum/class/` content is never a reference for correctness.
