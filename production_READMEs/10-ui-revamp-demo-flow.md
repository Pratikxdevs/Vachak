# 10 — UI Revamp & Demo Flow

## What the revamp changed

Old → new: static screens → Compose shell with live pipeline state.
- `MainScreen` + `LiveComponents` — push-to-talk, recognized Hindi text,
  Santali Ol Chiki translation, Santali audio button, per-item
  `ASR x • MT y • TTS z • Total` (measured via `LastPipelineRun`).
- `ModelStatus` dots — engines preloaded on IO, green/amber/red per model.
- `DiagnosticsScreen` — measured latency vs <3 s, measured bytes
  (APK + filesDir + packs + DB) vs 500 MB, `GIT_SHA` provenance, pack SHA,
  ASR fingerprint, `VachakLog` ring + Live Log.
- `ActiveLanguage` — `sat_Olck` default `StateFlow`, normalize
  (`sat→sat_Olck`, `mund/mun/unr→unr_Deva`).
- Fonts bundled offline: `noto_sans_ol_chiki`, `noto_sans_devanagari`, Lexend.
- Ol Chiki validation `[\u1C50-\u1C7F]`; TTS lexicon with `dataDir=""`
  (no espeak data in APK). Model failure → `[ASR:MODEL] <cause>`,
  never "no speech detected".

## 90–120 s judge script (WiFi OFF)

| Beat | Action |
|---|---|
| Problem | `शिक्षक हिंदी बोलते हैं, बच्चे संताली जानते हैं` |
| Offline proof | WiFi OFF + diagnostics, 0 network requests |
| Classroom | Open FLN lesson (precomputed bilingual) |
| Voice | Push-to-talk Hindi |
| Translation | Hindi → Santali Ol Chiki on screen |
| Speech | Santali audio (VITS; text is source of truth until trained voice ships) |
| Learning | Bilingual worksheet (template) + flashcards (prebuilt) |
| Performance | Diagnostics: measured latency vs <3 s, RAM, bytes vs 500 MB |
| Impact | Language-pack install path for other mother tongues |

Full script: `docs/architecture/DEMO.md:17-27`, `docs/phases/PHASES.md:138`.
Dev narration only: `scripts/demo_flow.py` (mocks; on-device latency pending hardware).

## Demo bar checklist (AGENTS.md)

WiFi OFF → lesson loads → translate → Santali audio → worksheet → flashcards →
push-to-talk Hindi → recognized text → Santali → audio → measured <3 s total → diagnostics.
