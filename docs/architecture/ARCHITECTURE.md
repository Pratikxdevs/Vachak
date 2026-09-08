# Vachak — System Architecture (SIH26042)

Offline Hindi→Mundari (Ol Chiki) classroom translator. Fully offline after sync.
Android 9 (minSdk 28), 2GB RAM, ~500MB total. This document covers PHASE 1
(system contracts & skeleton) and PHASE 2 (offline runtime, model registry,
offline test harness).

## Hard constraints

- **No runtime network.** No inference over the network; the app carries no
  INTERNET permission. `SyncManager` side-loads packs locally.
- **Sequential ML only**: ASR → MT → TTS (never parallel — RAM limit).
- **Precomputed curriculum**; **template-based worksheets**; **prebuilt flashcards**.
  Nothing is generated on-device.
- **Latency budget**: ASR ≤1s, MT ≤0.5s, TTS ≤1s, total <3s.

## Component map

```
                 ┌──────────────── Android app (offline) ────────────────┐
                 │                                                        │
   Mic ─▶ AudioRecord ─▶ VAD ─▶ ASR ─▶ MT ─▶ TTS ─▶ AudioTrack           │
                 │        (SherpaAsrAdapter) (Mock/IndicTrans2) (SherpaTtsAdapter)
                 │                                                        │
                 │   EngineProvider ── injects 9 interface-typed engines │
                 │   CurriculumEngine ── Room (precomputed lessons)      │
                 │   WorksheetEngine ── template-based                   │
                 │   FlashcardEngine ── prebuilt assets                  │
                 │   LanguagePackManager ── offline/model_registry       │
                 │   SyncManager ── side-load only (no network)          │
                 │   BenchmarkRunner ── <3s pipeline budget              │
                 └───────────────────────────┬──────────────────────────┘
                                              │ consumes side-loaded packs
                                 offline/model_registry (manifest.json)
                                              ▲
                                 backend/sync (builds packs) — dev-time only
```

## PHASE 1 — Interfaces (swap-without-UI-change)

Nine engines defined as:
- **Kotlin interfaces** `android/app/.../engine/EngineContracts.kt`
- **Python `Protocol`s** `shared/schemas/engines.py` (lockstep)

Both sides expose mocks (`MockEngines.kt`, `mock_engines.py`). The UI depends
only on `EngineProvider` (interface-typed fields). Swapping a mock for a real
adapter changes exactly one injection site — proven by:
- `android/app/src/test/.../EngineSwapTest.kt`
- `shared/schemas/tests/test_engines.py`

Each engine ships: unit test (mock), API contract doc (`shared/schemas/README.md`
+ `samples/sample_io.json`), and sample I/O.

## PHASE 2A — Audio runtime

`android/ml/.../adapter/`:
- `VadAdapter.kt` — VAD abstraction (mock = whole-buffer segment).
- `SherpaAsrAdapter.kt` — sherpa-onnx `SherpaOnnxOfflineRecognizer` reference;
  mock by default (no final model trained).
- `SherpaTtsAdapter.kt` — sherpa-onnx `SherpaOnnxOfflineTts` reference; mock.
- `AudioPipeline.kt` — wires Mic→VAD→ASR and Text→TTS→AudioTrack, sequentially.

## PHASE 2B — Model / language-pack registry

`offline/model_registry/` (`registry.py` + `manifest_schema.py`):
install, uninstall, version check, checksum validation, rollback, storage report.
Example: `modelpacks/mundari/manifest.json`.
Tests: `offline/model_registry/tests/test_registry.py`.

## PHASE 2C — Offline test harness

`offline/sync_engine/offline_harness.py` (Python unittest) asserts:
1. Airplane mode — patches `socket`/`urllib` to fail; pipeline must still run.
2. App restart w/o network — re-init from local state.
3. Model loading from manifest.
4. Local DB (Room-equivalent JSON placeholder) serves curriculum.
5. Worksheet generation placeholder (template-based, deterministic).
6. Startup/memory STUBS recorded (DEV FIXTURE, not on-device measured).

Android instrumented equivalent: `android/app/src/androidTest/.../OfflineHarnessTest.kt`.

Run all: `bash scripts/run_offline_tests.sh`.

## Reused cloned repos (not re-cloned)

- `sherpa-onnx` — ASR/TTS runtime reference (Android API shape).
- `IndicTrans2` / `indictrans2-onnx-export` — MT base + ONNX export attempt.
- `models/` — IndicTrans2 checkpoints (PyTorch; ONNX decoder blocked).

See `THIRD_PARTY_NOTICES.md` for license provenance. All licenses preserved.

## Known limitations / blockers

- **No Mundari model trained** (skeleton build) — adapters are mocks.
- **MT ONNX decoder export blocked** (process.md) — ship PyTorch 4-bit until fixed.
- **gradle not installed** in this env — Android build is conceptual (valid sources).
- Piper (TTS) is GPL-3.0 — licensing must be resolved before redistribution.
- All sample/fixture data is marked **DEV FIXTURE**; no fabricated metrics.
