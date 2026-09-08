# Latency Instrumentation (Phase 5D)

## What is measured
Single monotonic clock per utterance (Python `time.monotonic()`, Android
`SystemClock.elapsedRealtimeNanos()`). Wall-clock is avoided to prevent skew.

| Mark | Meaning | Set when |
|------|---------|----------|
| `T0` | speech begins | first VAD speech frame (or segment start) |
| `T1` | ASR result available | `AsrAdapter.transcribe` returns |
| `T2` | translation available | translator callable returns |
| `T3` | TTS begins | `TtsAdapter.synthesize` called |
| `T4` | audio begins | first audio sample ready / playback issued |

```
end_to_end_latency = T4 - T0
stage_latency:  asr=T1-T0, translate=T2-T1, tts=T3-T2, render=T4-T3
```

## How to record
- Python: `LatencyTracker` marks T0–T4; `LatencyRecorder` appends JSONL
  (`end_to_end_ms`, `stage_ms`, backend, `isFixture`) to a file. See `ml/pipeline/latency.py`.
- Android/Kotlin: `LatencyTracker` + `LatencySample` in `android/ml/LatencyTracker.kt`.
  Persist `LatencySample.toMap()` (add a JSONL writer in-app) for device runs.

## CRITICAL — do not claim ≤3s
The `<3s` target in AGENTS.md is a **device-measurement goal**, not a code guarantee.
With `Mock*`/`Baseline`/eSpeak backends, all measured latency is **fixture latency** and
is NOT representative of the production pipeline. Real numbers require:
- the final Hindi ASR model (IndicConformer .onnx),
- the final Mundari MT model,
- the final Mundari TTS model (sherpa-onnx),
- on a 2GB RAM Android 9 device, WiFi OFF, cold from the language pack.

The harness reports `isFixture=true` whenever any stage used a non-production backend, so
fixture runs can be filtered out of any latency report.

## Device measurement procedure
1. Build with real models; set `TRANSLATION_BACKEND=final` (or `baseline` for stand-in).
2. Run the classroom flow offline; collect JSONL over N utterances.
3. Drop `isFixture=true` rows; report median/p95 `end_to_end_latency` and per-stage breakdown.
4. Only then compare against the 3s budget.
