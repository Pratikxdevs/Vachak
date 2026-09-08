-- ============================================================================
-- Vachak Curriculum Knowledge Base — SQLite schema (Room-compatible)
-- Phase 3A — SIH26042 offline teacher app
-- ----------------------------------------------------------------------------
-- Conventions:
--  * All text fields store three parallel strings: _hi (Hindi), _target
--    (Mundari/Santali — see localization layer), _en (English, for dev/audit).
--  * `sequence` columns give a deterministic, stable ordering (no LLM, no
--    runtime sort-by-relevance).
--  * IDs are stable strings so the same seed always produces identical rows.
--  * This DDL is plain SQLite and is consumed directly by Room's
--    `createAllTables` migration; see schemas/entities.kt for the @Entity mirror.
-- ============================================================================

PRAGMA foreign_keys = ON;

CREATE TABLE grades (
    grade_id      TEXT PRIMARY KEY,
    grade_number  INTEGER NOT NULL,
    name_hi       TEXT NOT NULL,
    name_target   TEXT NOT NULL,
    name_en       TEXT NOT NULL
);

CREATE TABLE subjects (
    subject_id    TEXT PRIMARY KEY,
    grade_id      TEXT NOT NULL,
    name_hi       TEXT NOT NULL,
    name_target   TEXT NOT NULL,
    name_en       TEXT NOT NULL,
    FOREIGN KEY (grade_id) REFERENCES grades(grade_id)
);

CREATE TABLE chapters (
    chapter_id    TEXT PRIMARY KEY,
    subject_id    TEXT NOT NULL,
    name_hi       TEXT NOT NULL,
    name_target   TEXT NOT NULL,
    name_en       TEXT NOT NULL,
    sequence      INTEGER NOT NULL,
    FOREIGN KEY (subject_id) REFERENCES subjects(subject_id)
);

CREATE TABLE lessons (
    lesson_id            TEXT PRIMARY KEY,
    chapter_id           TEXT NOT NULL,
    title_hi             TEXT NOT NULL,
    title_target         TEXT NOT NULL,
    title_en             TEXT NOT NULL,
    sequence             INTEGER NOT NULL,
    estimated_minutes    INTEGER NOT NULL,
    FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id)
);

CREATE TABLE learning_outcomes (
    outcome_id         TEXT PRIMARY KEY,
    lesson_id          TEXT NOT NULL,
    code               TEXT NOT NULL,
    description_hi     TEXT NOT NULL,
    description_target TEXT NOT NULL,
    description_en     TEXT NOT NULL,
    sequence           INTEGER NOT NULL,
    FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
);

CREATE TABLE teacher_instructions (
    instruction_id   TEXT PRIMARY KEY,
    lesson_id        TEXT NOT NULL,
    text_hi          TEXT NOT NULL,
    text_target      TEXT NOT NULL,
    text_en          TEXT NOT NULL,
    sequence         INTEGER NOT NULL,
    FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
);

CREATE TABLE activities (
    activity_id       TEXT PRIMARY KEY,
    outcome_id        TEXT NOT NULL,
    title_hi          TEXT NOT NULL,
    title_target      TEXT NOT NULL,
    title_en          TEXT NOT NULL,
    activity_type     TEXT NOT NULL,
    instructions_hi   TEXT NOT NULL,
    instructions_target TEXT NOT NULL,
    instructions_en   TEXT NOT NULL,
    sequence          INTEGER NOT NULL,
    FOREIGN KEY (outcome_id) REFERENCES learning_outcomes(outcome_id)
);

CREATE TABLE assessments (
    assessment_id    TEXT PRIMARY KEY,
    outcome_id       TEXT NOT NULL,
    type             TEXT NOT NULL,
    prompt_hi        TEXT NOT NULL,
    prompt_target    TEXT NOT NULL,
    prompt_en        TEXT NOT NULL,
    answer_key       TEXT NOT NULL,
    difficulty       TEXT NOT NULL,
    sequence         INTEGER NOT NULL,
    FOREIGN KEY (outcome_id) REFERENCES learning_outcomes(outcome_id)
);

CREATE TABLE vocabulary_terms (
    term_id          TEXT PRIMARY KEY,
    lesson_id        TEXT NOT NULL,
    term_hi          TEXT NOT NULL,
    term_target      TEXT NOT NULL,
    term_en          TEXT NOT NULL,
    definition_hi    TEXT,
    definition_target TEXT,
    definition_en    TEXT,
    FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
);

CREATE TABLE flashcard_concepts (
    card_id          TEXT PRIMARY KEY,
    lesson_id        TEXT NOT NULL,
    concept_hi       TEXT NOT NULL,
    concept_target   TEXT NOT NULL,
    concept_en       TEXT NOT NULL,
    image_ref        TEXT,
    audio_ref        TEXT,
    sequence         INTEGER NOT NULL,
    FOREIGN KEY (lesson_id) REFERENCES lessons(lesson_id)
);

-- Indexes for offline-first navigation (teacher drills down grade→subject→...).
CREATE INDEX idx_subjects_grade       ON subjects(grade_id);
CREATE INDEX idx_chapters_subject     ON chapters(subject_id);
CREATE INDEX idx_lessons_chapter      ON lessons(chapter_id);
CREATE INDEX idx_outcomes_lesson      ON learning_outcomes(lesson_id);
CREATE INDEX idx_instructions_lesson  ON teacher_instructions(lesson_id);
CREATE INDEX idx_activities_outcome   ON activities(outcome_id);
CREATE INDEX idx_assessments_outcome  ON assessments(outcome_id);
CREATE INDEX idx_vocab_lesson         ON vocabulary_terms(lesson_id);
CREATE INDEX idx_flashcards_lesson    ON flashcard_concepts(lesson_id);
