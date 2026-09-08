# Curriculum Knowledge Base — Phase 3 (SIH26042 / Vachak)

Offline-first knowledge base that lets a teacher drill down
**Grade → Subject → Chapter → Lesson** and receive a structured, multilingual
lesson package. No network, no LLM. Target language is configurable (default
**Santali / Ol Chiki**; the task brief names Mundari — switch `localization/
languages.json` `target_language_code` to `unr` and re-seed).

## Deliverables (3A / 3B / 3C)

| ID | What | Location |
|----|------|----------|
| 3A | SQLite schema (Room-compatible) | `schemas/schema.sql` |
| 3A | Room `@Entity` Kotlin classes | `schemas/entities.kt` |
| 3B | Deterministic lesson→LO→activity→assessment mapping | `lesson_map.py` |
| 3C | Authored Grade 2 Math "Counting" seed | `seed/grade2_math_counting.json` |
| 3C | Seed provenance | `seed/provenance.json` |
| 3C | Deterministic SQL seed export | `seed/export_sql.py` → `seed/seed.sql` |

## Acceptance

A teacher can select **Grade 2 → Mathematics → Counting → Lesson** and get a
structured offline lesson package (lesson + teacher instructions + learning
outcomes, each with activities & assessments + vocabulary + flashcards).

## Setup

Python 3.8+ (standard library only — no pip install required).

```bash
# Validate seed integrity and list the lesson path
python3 data.py

# Build a lesson package (3B)
python3 lesson_map.py

# Generate SQL INSERTs from the seed (3A, for Room pre-population)
python3 seed/export_sql.py

# End-to-end: load schema + seed into SQLite and count rows
python3 - <<'PY'
import sqlite3
con=sqlite3.connect(':memory:')
con.executescript(open('schemas/schema.sql').read())
con.executescript(open('seed/seed.sql').read())
print('lessons', con.execute('select count(*) from lessons').fetchone()[0])
print('flashcards', con.execute('select count(*) from flashcard_concepts').fetchone()[0])
PY
```

## API / Interface Contract

### `CurriculumDB` (`data.py`)
- `load(seed_path=...)` → `CurriculumDB`
- `db.grades()`, `db.subjects(grade_id)`, `db.chapters(subject_id)`, `db.lessons(chapter_id)`
- `db.lesson(lesson_id)`, `db.outcomes(lesson_id)`, `db.activities(outcome_id)`,
  `db.assessments(outcome_id)`, `db.vocabulary(lesson_id)`, `db.flashcards(lesson_id)`,
  `db.instructions(lesson_id)`
- `db.validate()` → `list[str]` (empty == valid; checks all FKs + NOT NULL)

All tables store three parallel strings per concept: `_hi` (Hindi),
`_target` (target lang), `_en` (English, dev/audit). Ordering is by `sequence`.

### `lesson_map.build_lesson_package(lesson_id, db=None)` → `dict`
Pure graph traversal (no LLM). Returns:
```json
{
  "package_id": "PKG-L-COUNT-G2",
  "lesson": {"lesson_id","title_hi","title_target","title_en","estimated_minutes"},
  "teacher_instructions": [ {"instruction_id","text_hi","text_target","text_en"} ],
  "outcomes": [ {"outcome_id","code","description_*","activities":[...],"assessments":[...]} ],
  "vocabulary": [ {"term_hi","term_target","term_en","definition_*"} ],
  "flashcards": [ {"card_id","concept_hi","concept_target","concept_en","image_ref","audio_ref"} ]
}
```
**Determinism:** identical `lesson_id` + identical seed ⇒ byte-identical package.

## Sample / Demo Data
- `seed/lesson_package_L-COUNT-G2.json` — full package dump
- `seed/seed.sql` — runnable INSERTs

## Known Limitations
- Seed content is an **author-draft DEV FIXTURE** aligned to public NIPUN Bharat
  FLN competency descriptors; **not SME-verified** and must not ship as approved
  pedagogy without sign-off (per AGENTS.md).
- Only Grade 2 / Mathematics / Counting is seeded. Adding content = appending
  JSON rows (see `grade2_math_counting.json` shape) + re-running `export_sql.py`.
- Target-language (Santali) abstract terms (before/after/more/less) are
  best-effort; numerals 1–10 are reliable. Mundari requires re-seeding.
- `entities.kt` is the canonical schema mirror; the `android/` module copies it
  (this phase does not edit `android/`).
