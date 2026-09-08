# Vachak — SIH26042 Offline Vernacular Pedagogy & Real-Time Translation

**Target language:** Mundari · **Direction:** Hindi → Mundari · **Device:** Android 9+, ~2 GB RAM
**Hard requirement:** fully offline after initial sync · **Voice-to-voice latency ≤ 3 s** (measured on-device)

This is a *replaceable-adapter* architecture. The ML model is **not** the product — the offline
classroom system is. Mundari NMT/TTS are drop-in adapters; everything else works today with mocks /
baseline stand-ins (IndicTrans2 Hindi→Santali is used as a clearly-labeled **NOT Mundari** baseline).

## Repository layout

| Path | Phase | Owner | What it provides |
|---|---|---|---|
| `android/` | 1, 2 | A | Compose app shell, engine interfaces, audio pipeline, model registry client |
| `shared/schemas/` `shared/orchestrator/` | 1, 9 | A, E | Cross-language engine contracts + orchestrator state machine |
| `offline/model_registry/` `modelpacks/` | 2 | A | Install/uninstall/checksum/rollback of language packs |
| `curriculum/` `worksheet/` `flashcard/` `localization/` | 3, 4 | B | Curriculum DB, worksheet & flashcard generators, localization layer |
| `ml/pipeline/` | 5 | C | VAD → ASR → TTS streaming adapters + T0–T4 latency instrumentation |
| `backend/api/` | 6 | C | `POST /translate` + deterministic terminology validator |
| `ml/translation/` `ml/tts/` | 7, 8 | D | Mundari NMT/TTS adapters + training/export pipelines (DATA ACCESS PENDING) |
| `backend/sync/` | 10 | E | Teacher-correction queue + sync (never auto-trains unverified data) |
| `ml/benchmarks/` `docs/benchmarks/` | 11 | E | Offline + voice-pipeline benchmark harness & report template |
| `scripts/` `docs/DEMO.md` | 12 | E | 90–120 s demo flow + pack builder |

## Quick start (offline-capable)

```bash
# Python modules (orchestrator, translation service, curriculum, benchmarks)
/usr/bin/python3 -m unittest discover -s shared/orchestrator/tests
/usr/bin/python3 -m unittest discover -s backend/api/tests
/usr/bin/python3 -m unittest discover -s curriculum/tests
/usr/bin/python3 -m unittest discover -s ml/benchmarks/tests

# Translation service
cd backend/api && /usr/bin/python3 app.py          # POST /translate (mock/baseline by env)

# Android
cd android && ./gradlew assembleDebug              # NOTE: gradle absent on build host; sources valid
```

## Swapping a model without UI changes

Set the engine implementation in `android/.../engine/EngineProvider` (or the `ENGINE` env var in the
backend). `Mock*` → `Baseline*` (IndicTrans2) → `FinalMundari*` are all interface-compatible.

## Integration milestones

1. Android + Mock Translation + Mock TTS + offline DB
2. Android + Real ASR + Mock Translation + temporary TTS
3. Android + ASR + Baseline MT + TTS
4. Android + Mundari NMT + Mundari TTS + offline
5. Full classroom + curriculum + worksheets + flashcards + benchmark

## License & provenance

- Every reused repo's `LICENSE`/`NOTICE` is preserved. See `THIRD_PARTY_NOTICES.md` and
  `docs/MODEL_AND_DATA_PROVENANCE.md`.
- The Karya Hindi–Mundari corpus and Mundari TTS assets are **non-commercial (BY-NC-SA-FS)** and
  NOT bundled. Phases 7/8 are scaffolding that activates only when licensed access is resolved.
- No performance metrics are fabricated; benchmark fields are `PENDING REAL-DEVICE MEASUREMENT`.
