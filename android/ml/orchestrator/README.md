# android/ml/orchestrator — Kotlin Wiring (PHASE 9)

Kotlin counterpart to `shared/orchestrator`, bound to the existing
`EngineContracts` types (`com.vachak.engine`):

- `ASREngine`, `TranslationEngine`, `TTSEngine` — real/mock engines via `EngineProvider`
- `LanguagePackManager` — drives the `PROMPT_LANGUAGE_PACK` action
- `EngineResult<T>` / `EngineError` — uniform success/failure
- `LatencyBudget` — the <3s per-stage budget

## Files
- `Orchestrator.kt` — request lifecycle: ASR→MT→TTS→play, sequential, with
  per-stage timing and the graceful-degradation policy.
- `ClassroomState.kt` — live session state (mirror of classroom_state.py).
- `ErrorPolicy.kt` — degradation rules (retry / show source / show translation /
  prompt language pack). Matches `shared/orchestrator/errors.py`.

## Integration notes
- `EngineError.MODEL_NOT_LOADED` from any engine triggers the language-pack prompt.
- VAD and `Speaker` (AudioTrack) are local interfaces here; wire to sherpa-onnx
  `VoiceActivityDetector` + `OfflineTts` in production.
- `TerminologyValidator` is a local interface (glossary-backed) — on failure the
  orchestrator shows the source text.

## Contract with shared/orchestrator
The Python state machine is the canonical spec; this Kotlin mirrors its stages,
error policy, and `ClassroomState` fields so behavior is identical across runs.

## Status
Reference wiring. Depends on the app shell (`android/app`) for `EngineProvider`
and Compose UI (owned by other phases) — not compiled standalone here.

## Known limitations
- Uses DEV-FIXTURE mocks until real engines are provided.
- On-device latency/battery are PENDING (see ml/benchmarks).
