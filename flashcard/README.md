# Flashcard Engine — Phase 4B (SIH26042 / Vachak)

See [`../worksheet/README.md`](../worksheet/README.md) for the full API contract,
setup, tests, samples and limitations. This module provides:

- `FlashcardEngine.build_from_lesson(lesson_id, language)` — deck from curriculum flashcards
- `FlashcardEngine.build_card(spec, language)` — single card from a `FlashcardSpec`
- `FlashcardEngine.build_deck(specs, language, deck_id)` — custom deck

Deterministic and offline; no LLM. Output is JSON (`deck` shape in the main README).
Sample: `sample/deck_G2_Math_Counting_bilingual.json`.
Tests: `python3 -m unittest flashcard.tests.test_flashcard`.
