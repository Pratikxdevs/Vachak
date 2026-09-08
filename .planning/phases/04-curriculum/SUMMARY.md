# Phase 4 Summary — Curriculum Content (Santali FLN, NIPUN, Worksheets, Flashcards)

**Date:** 2026-08-29
**Wave:** 1 (single plan 04-01, autonomous, no dependencies except P1 shell)
**Status:** Complete — all must-haves verified

## What was delivered

### 1. Authored curriculum (precomputed, not on-device MT)
- `curriculum/lessons/sat_lessons.json` — 8 FLN lessons, grades 1–3, domains oral/reading/writing, each `{id, grade, domain, titleHi, textHi, textSatOlChiki, outcomeId, mediaRefs, precomputed=true}`. Ol Chiki validated U+1C50–U+1C7F (9 grep hits, 8 lessons).
- `curriculum/outcomes/nipun.json` — 8 NIPUN outcomes `{outcomeId, nipunCode, descriptor, grade, domain}` mapped 1:1 to lessons (codes G1-O-COM-01 … G3-R-COM-02).
- Both bundled as assets under `android/content/src/main/assets/curriculum/...` and `android/app/...` for Room prepopulate. Content is AUTHOR-DRAFT per provenance, CC BY 4.0, NOT MACHINE_TRANSLATED as approved pedagogy until SME sign-off.

### 2. Room + ContentEngine
- `android/content/build.gradle.kts` — added Room 2.6.1 + room-ktx, wiring to `:content`.
- `android/content/src/main/java/com/vachak/content/db/Entities.kt` — 4 `@Entity` tables: `lessons`, `outcomes`, `worksheets`, `flashcards` (with FKs, indices, budgets).
- `android/content/src/main/java/com/vachak/content/db/LessonDao.kt` — DAOs: `LessonDao`, `OutcomeDao`, `WorksheetDao`, `FlashcardDao`.
- `android/content/src/main/java/com/vachak/content/db/AppDatabase.kt` — `@Database` with `PrepopulateCallback` reading JSON assets via `org.json`, validating Ol Chiki, inserting deterministically, seeding 17 worksheets + 42 flashcards (2–3 per lesson, 4 + numerals for counting). Allows main-thread queries for demo; in-memory helper for tests.
- `android/content/src/main/java/com/vachak/content/ContentEngine.kt` — `class ContentEngine : CurriculumEngine, WorksheetEngine, FlashcardEngine` — `listLessons`, `getLesson`, `getOutcomes`, `getWorksheets`, `getFlashcards`, `getOutcomeEntity`, `getLessons`. Maps `LessonEntity→Lesson` (sourceTextHi→textHi, translatedText→textSatOlChiki, precomputed). `isOlChikiValid` validates U+1C50–U+1C7F.
- `android/app/src/main/java/com/vachak/engine/EngineProvider.kt` — `real()` now creates `ContentEngine(context)` and wires `curriculum=content, worksheet=content, flashcard=content` (drops MockCurriculumEngine). `mock()` retained for tests.
- `android/app/src/main/java/com/vachak/ui/MainActivity.kt` — default lesson `L-SAT-G1-ORAL-01`.
- `android/app/build.gradle.kts` — added `implementation(project(":content"))`.

### 3. Worksheets + flashcards (offline, template/prebuilt)
- `worksheet/templates/*.pdf` — 7 PDFs (trace_olchiki_G1, fill_numbers_G2, comprehension_G3, oral_fill_blank_G1, reading_match_G2, comprehension_G2, oral_match_G1) — template-based, deterministic PIL → PDF, not AI-generated. Budget 0.87MB.
- `flashcard/assets/*.png` — 46 PNGs (32 lesson-specific + 10 numerals 1–10 + 4 generic) with `conceptHi + conceptSatOlChiki` + placeholder icon, rendered with Noto Sans Ol Chiki (OFL, build-time only, not bundled). Prebuilt, not generated at runtime. Budget 1.0MB.
- Mirrored under `android/app/src/main/assets/worksheet/templates/` and `flashcard/assets/` + `android/content/...` for APK bundling.
- `android/app/src/main/java/com/vachak/ui/LessonScreen.kt` — Compose `LessonScreen` + `LessonListScreen` — reads from `ContentEngine` (verified `grep ContentEngine`), shows lesson `textHi + textSatOlChiki`, NIPUN badge (`nipunCode`), `precomputed` badge, Ol Chiki validation tick, Buttons `Worksheets (n)` / `Flashcards (n)` opening offline viewers (assetPath, no network, no generation), lists all Room lessons, lazy row for flashcards.

### 4. Licensing & budgets
- `THIRD_PARTY_NOTICES.md` — added Phase 4 section: curriculum/outcomes CC BY 4.0, worksheets/flashcards CC BY 4.0, Room DB, Noto Sans Ol Chiki OFL (build-time), Ol Chiki public domain, NIPUN framework public domain.
- Budgets verified: combined ~1.89MB well within 10–30MB content + 20–50MB flashcards (AGENTS.md). No INTERNET permission in manifest (OFFLINE-FIRST).

## Verification evidence

```
cat curriculum/lessons/sat_lessons.json | grep -c "textSatOlChiki"  # 9 (>=5)
cat curriculum/outcomes/nipun.json | grep -c "nipunCode"            # 9 (>=5)
grep -n "class ContentEngine" android/content/.../ContentEngine.kt  # 36:class ContentEngine
grep -n "@Entity" android/content/.../Entities.kt                   # 4 entities
ls worksheet/templates/ | wc -l                                      # 7 PDFs (>=3)
ls flashcard/assets/ | wc -l                                         # 46 PNGs (>=5)
grep -n "ContentEngine" android/app/.../LessonScreen.kt            # bound
sqlite in-memory: 8 lessons, 8 outcomes, 17 worksheets, 42 flashcards — each lesson has outcome + worksheet + flashcard
python curriculum/tests: 6 tests OK
no android.permission.INTERNET in manifest
```

## Links

`curriculum/lessons/sat_lessons.json + curriculum/outcomes/nipun.json` → `AppDatabase.PrepopulateCallback` (reads assets) → `ContentEngine` → `LessonScreen`/`LessonListScreen` (Compose) — offline, sequential, no MT at runtime.

## Deferred / not in scope (per PHASES.md)

- Pack builder (P5) — P4 seeds DB directly; pack will hash these assets.
- TTS voice (P2) + ASR (P3) — not affected.

## Next

Phase 5 can now package these assets (models + curriculum) as signed `.vachakpack` via `sync/` installer; Phase 6 will prove <3s sequential on 2GB.
