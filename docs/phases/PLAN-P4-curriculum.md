# PLAN — P4: Curriculum Content (FLN + NIPUN + Worksheets + Flashcards)

**Goal:** Replace `MockCurriculumEngine` with real FLN lessons, NIPUN outcome mapping,
template-based worksheets, and prebuilt flashcard assets stored in Room.

**Context:**
- AGENTS.md: curriculum translations precomputed (not on-device); worksheets template-based
  (not AI-generated); flashcards prebuilt assets. Content budget 10–30 MB + 20–50 MB images.
- Authoring lives in `curriculum/`; app consumes via `content/` (Room).

**Tasks:**
1. Author FLN lessons (Hindi source + Mundari translation + outcome) in `curriculum/`.
2. Map each lesson to NIPUN outcomes (outcome ID + descriptor).
3. Build worksheet templates (fill-in, match, trace) — no runtime generation.
4. Produce flashcard assets (image + Ol Chiki + Hindi) under `curriculum/flashcards/`.
5. Real `ContentEngine` (Room): lessons, outcomes, worksheets, flashcards; swap into UI.
6. Unit test: load a lesson, assert NIPUN mapping + asset references resolve.

**Verify:** Lesson list/detail render from Room; worksheets + flashcards open offline.

**Acceptance:** App shows real lessons + worksheets + flashcards, not `DEV-FIXTURE` placeholders.
