# final_curriculum — Authored Bilingual Curriculum (Hindi + Santali Ol Chiki)

Replacement for `curriculum/class/` (AUTO_EXTRACTED tile-crops, null targets, null answers — unusable as pedagogy).

## Source

Friend authors ONLY from `santali_organized/` — 67 PDFs:

| Folder | Subjects inside |
|--------|-----------------|
| `Class 1-2/` | General, Mathematics |
| `Class 3/` | Environmental Studies, Mathematics, Physical Education & Well Being |
| `Class 4/` | Mathematics, Physical Education & Well Being, The World Around Us |
| `Class 5/` | Arts, EVS, General, Physical Education & Well Being |

1 PDF = 1 chapter. `grade` = the TRUE grade (fix misfiles: G4 PE chapters sitting in Class 3/5 folders stay grade 4).

## Layout

```
final_curriculum/
  README.md           # this file — contract + gates
  STRUCTURE.md        # exact JSON schemas (copy-paste)
  AUTHORING_GUIDE.md  # PDF -> chapter workflow, Ol Chiki rules
  IMAGE_SPEC.md       # half-page export spec
  CHECKLIST.md        # per-chapter + reviewer sign-off
  grades.json         # [{grade, name_hi, name_sat_ol}]
  subjects.json       # [{subject_id, grade, name_hi, name_sat_ol}]
  chapters/{grade}/{slug}/
    chapter.json
    worksheets.json
    flashcards.json
    pages/pNN_top.webp | pNN_bottom.webp
```

## Language rule

Every human string is a bilingual pair: `{"hi": "...", "sat_ol": "�..."}`.

* Both sides required for `APPROVED`. No English field — dropped by decision.
* `sat_ol` MUST contain Ol Chiki block U+1C50–U+1C7F. Devanagari Santali, Krishna codepoints (`tks+ekd~`), `null`, `""` all fail validation.
* Fast path: `sat_ol: ""` allowed ONLY with `status: "DRAFT"`. Validator rejects `APPROVED` with empty `sat_ol`.

## Status gates

| Status | Meaning | Ships? |
|--------|---------|--------|
| `DRAFT` | Authored, Ol Chiki incomplete or unreviewed | Never as pedagogy. DRAFT banner in diagnostics. |
| `REVIEWED` | Second speaker checked Hindi + Ol Chiki + answers | Staging only. |
| `APPROVED` | Native-speaker SME sign-off, all checks pass | Only status that ships to device. |

Per AGENTS.md: never ship `MACHINE_TRANSLATED` / auto-extracted content as approved pedagogy. Old `curriculum/class/` stays `AUTO_EXTRACTED` forever — do not copy text from it, author fresh from PDFs.

## Budgets (AGENTS.md ~500MB total)

* Curriculum text JSON: target < 5MB.
* Images: ~67 chapters x 6-10 halves x ~100KB = ~50MB max.
* No `.wav` authored here — TTS renders offline at pack time from `sat_ol`.

## Workflow per PDF

1. Read the PDF once. Decide `grade` (true), `subject`, `slug`.
2. Write `chapter.json` — title, intro (2-3 lines), 3-5 outcomes, teacher note, 5-10 vocab. All bilingual.
3. Export 6-10 half-page images to `pages/` (see IMAGE_SPEC.md).
4. Fill `worksheets.json` — 4-9 items, every item has a real `answer` (see STRUCTURE.md).
5. Fill `flashcards.json` — 10-20 cards, chapter words, not generic 1-10 (see STRUCTURE.md).
6. Self-check with CHECKLIST.md, set `status`, submit for review.

## Validation (pack-time, deterministic)

* JSON parses; required keys present.
* Every `sat_ol` non-empty (if APPROVED) + matches Ol Chiki regex.
* Every worksheet item has non-null `answer`; `image_ref` file exists.
* Every flashcard has `front_hi` + `back_sat_ol`; `image_ref` file exists or omitted.
* `source_pdf` path exists under `santali_organized/`.

UI is built later against this shape — do not shape content to fit the old `curriculum/class/` or old Android pack readers.
