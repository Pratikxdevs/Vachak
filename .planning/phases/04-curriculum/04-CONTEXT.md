# Phase 4: Curriculum Content — Context

**Gathered:** 2026-08-29
**Status:** Ready for planning
**Source:** AGENTS.md hard rules, docs/PHASES.md Phase 4

<domain>
## Phase Boundary

Real FLN lessons + NIPUN mapping + template worksheets + prebuilt flashcards in Room, replacing MockCurriculumEngine. Translations precomputed (not on-device MT). Covers authoring in curriculum/, DB in android/content/, UI binding. Does NOT include TTS voice (P2) or pack system (P5).
</domain>

<decisions>
## Implementation Decisions

### Content Rules (AGENTS.md hard)
- Translations precomputed, not on-device MT for curriculum store
- Worksheets template-based (not AI-generated) — fill-in/match/trace PDFs
- Flashcards prebuilt image + Ol Chiki + Hindi assets

### Storage
- Room/SQLite under android/content/ (Lesson, Outcome, Worksheet, Flashcard entities)
- Budgets: 10–30MB content + 20–50MB flashcard images
- curriculum/ is source of truth, android/content is consumer

### Claude's Discretion
- Exact Room schema (foreign keys Lesson→Outcome), authoring format (JSON/YAML under curriculum/)
- Worksheet template engine (Compose → PDF via Android PdfDocument vs prebaked PDFs)
</decisions>

<canonical_refs>
## Canonical References
- AGENTS.md — hard rules (precomputed, template, prebuilt)
- curriculum/ + worksheet/ + flashcard/ — authoring dirs
- android/content/ — Room module
- docs/PHASES.md Phase 4
</canonical_refs>

<specifics>
## Specific Ideas
- One lesson row = {id, titleHi, textHi, textSatOlChiki (precomputed), outcomeId, media}
- NIPUN descriptor per outcome; flashcard = {image, hi, satOlChiki}
</specifics>

<deferred>
## Deferred Ideas
- Pack builder (P5) — P4 seeds DB directly
</deferred>
