# Localization Layer — Phase 4C (SIH26042 / Vachak)

Single source of truth for multilingual rendering of all generated artifacts.
See [`../worksheet/README.md`](../worksheet/README.md) for usage and contract.

- `localization/layer.py` — `localize(triple, lang)`, `normalize_language(lang)`,
  `language_label(lang)`, `triple(hi, target, en)`.
- `localization/languages.json` — language config.

A **Triple** is `{"hi": ..., "target": ..., "en": ...}`. Modes: `hi`, `target`,
`bilingual`. The **target** language is set by `target_language_code` in
`languages.json` (default `sat` = Santali/Ol Chiki). To target **Mundari** set
it to `unr` and re-seed the curriculum — no code changes.

This keeps Hindi→Santali/Mundari switching purely config-driven and offline.
