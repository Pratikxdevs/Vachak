# Shared Schemas — API / Interface Contract (PHASE 1)

This package defines the **single vocabulary** shared by the Android app
(`android/app/.../engine/EngineContracts.kt`) and any Python backend/orchestration
tool. The Kotlin interfaces and these Python `Protocol`s are kept in lockstep.
Swapping a mock for a real adapter must not change caller (UI) code — proved by
`tests/test_engines.py` and `android/app/src/test/.../EngineSwapTest.kt`.

> All sample I/O in `samples/sample_io.json` is **DEV FIXTURE** data. It is not
> real inference and must not be mistaken for trained-model output.

## Common types

- `Result = Ok(value) | Err(code, message)` — uniform success/failure envelope.
- `EngineError` ∈ {UNSUPPORTED_LANGUAGE, MODEL_NOT_LOADED, MODEL_LOAD_FAILED,
  TIMEOUT, IO_ERROR, INVALID_INPUT, OFFLINE_REQUIRED}.
- `LanguagePair{source, target}` — e.g. `{hi, mund}`.

## Interface contracts

| Engine | Key method | Input → Output |
|--------|-----------|----------------|
| `TranslationEngine` | `translate(text, pair)` | Hindi text → Mundari (Ol Chiki) text |
| `ASREngine` | `transcribe(pcm16, sample_rate_hz)` | 16-bit PCM → Hindi text |
| `TTSEngine` | `synthesize(text, language)` | Mundari text → 16-bit PCM |
| `CurriculumEngine` | `get_lesson(id)` | lesson id → precomputed `Lesson` |
| `WorksheetEngine` | `generate(lesson_id, template)` | → template-based `Worksheet` |
| `FlashcardEngine` | `list_deck(lesson_id)` | → prebuilt `FlashcardDeck` |
| `LanguagePackManager` | `install/uninstall/validate/rollback` | manage offline packs |
| `SyncManager` | `install_package(path)` | side-loaded pack → `SyncReport` (no network) |
| `BenchmarkRunner` | `run(pair, sample)` | → `BenchmarkReport` (latency/memory) |

### Hard rules encoded in the contract

- **No network.** `SyncManager.is_network_allowed()` returns `False`. Curriculum
  translations and flashcards are `precomputed` / prebuilt, never generated
  on-device.
- **Sequential pipeline only** (RAM limit): ASR → MT → TTS. Never parallel.
- **Latency budget**: ASR ≤1000ms, MT ≤500ms, TTS ≤1000ms, total <3000ms.

## How to swap an implementation

1. Write a class implementing the relevant `Protocol` (see
   `mock_engines.py` + `FakeRealTranslationEngine` in the test for the pattern).
2. Inject it through the provider (`EngineProvider` on Android,
   a plain constructor on Python).
3. Caller code is unchanged. Tests assert identical interface behavior.

## Files

- `engines.py` — Protocol definitions (interfaces).
- `mock_engines.py` — DEV FIXTURE mock implementations.
- `tests/test_engines.py` — swapability proof.
- `samples/sample_io.json` — sample request/response per engine.
