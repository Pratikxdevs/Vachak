# 08 — Content: Curriculum, Worksheets, Flashcards, Localization

## Curriculum (AUTHOR-DRAFT, never MT output)

- `curriculum/lessons/sat_lessons.json` — 15 FLN lessons, 8 NIPUN outcomes
  (`curriculum/outcomes/nipun.json`), `seed_version=2.0.0-flooded`, `precomputed:true`,
  CC BY 4.0 Vachak-authored. Per lesson: `id/titleHi/titleSatOlChiki/textHi/
  textSatOlChiki (U+1C50–U+1C7F)/outcomeId/estimatedMinutes/mediaRefs/
  translation_provenance: human-reviewed precomputed, not on-device MT`.
  Example `L-SAT-G1-ORAL-01`: `नमस्ते… → johAr…`, media `flashcard/assets/johar_greeting.png`.
- Seed/provenance: `curriculum/seed/` (`grade2_math_counting.json`, `seed_lessons.json`,
  `lesson_package_L-COUNT-G2.json`, `export_sql.py→seed.sql`), `seed/provenance.json`
  (`global_status: AUTHOR-DRAFT… NOT yet verified by native-speaker SME`).
- Rule: never ship `MACHINE_TRANSLATED`/synthetic `datasets/hin_sat` as approved pedagogy —
  SME sign-off required.

## Worksheets (template-based)

- `worksheet/engine.py` — `WorksheetRequest{grade,subject,outcome,difficulty,language}`
  → deterministic questions + answer key (`SEED_CONST=20240828 + sha256`, no LLM/network).
  Types: counting/multiple_choice/matching/fill_in_blank/image_based.
- `worksheet/pdf.py` — dep-free A4 writer (Helvetica; Devanagari/Ol Chiki needs
  font-embedding in prod). `to_markdown()` for preview.
- `worksheet/templates/*.pdf` (7: trace_olchiki_G1, oral_fill_blank_G1, oral_match_G1,
  fill_numbers_G2, reading_match_G2, comprehension_G2/G3) — PIL-rendered offline,
  CC BY 4.0, never AI-generated (`THIRD_PARTY_NOTICES.md:145`).

## Flashcards (prebuilt PNGs)

- `flashcard/engine.py` — `build_from_lesson/build_card/build_deck`, deterministic, offline.
- `flashcard/assets/*.png` (46: number_1..10, johar_greeting, olchiki_letters,
  village_reading, market_conversation, l-sat-g{1,2,3}-…_{1..4}) — prebuilt,
  each embeds Hindi + Ol Chiki, never generated at runtime.

## Localization

- `localization/layer.py` — `localize(triple{hi,target,en}, lang)`,
  modes `hi|target|bilingual` (`hi — target`).
- `localization/languages.json` — `sat` / `Santali (Ol Chiki)` default;
  Mundari = set `unr` + re-seed, no code change.
