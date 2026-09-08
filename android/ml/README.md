# android/ml — Kotlin reference adapters

Drop these into the Android module (`android/ml`, package `com.vachak.ml`). They mirror the
Python `ml/pipeline` adapters 1:1 so behavior is identical across platforms.

## Files
| File | Phase | Notes |
|------|-------|-------|
| `AsrAdapter.kt` | 5B | Interface + `AsrResult`. |
| `MockHindiAsrAdapter.kt` | 5B | DEV FIXTURE Hindi ASR (fixed utterance). |
| `IndicConformerAsrAdapter.kt` | 5B | ONNX Runtime Mobile stub (Indic-STT pattern). Throws until decode is implemented. |
| `TtsAdapter.kt` | 5C | Interface + `SynthAudio`. |
| `SherpaOnnxTtsAdapter.kt` | 5C | sherpa-onnx Offline TTS reference (VITS/Piper/Matcha). |
| `EspeakFallbackTtsAdapter.kt` | 5C | DEV-ONLY fallback. **Not the final Mundari voice** (labeled). |
| `MockTtsAdapter.kt` | 5C | Silent DEV FIXTURE. |
| `VadStream.kt` | 5A | `VadAnalyzer` + `SherpaOnnxVadAnalyzer` (Silero) + `MockVadAnalyzer` (fixture) + `runMicrophoneVad` (AudioRecord, 10–100ms chunks). |
| `LatencyTracker.kt` | 5D | `LatencyTracker`/`LatencySample` (T0–T4, monotonic nanos). |

## Dependencies (add to app/build.gradle)
- `com.k2fsa.sherpa:sherpa-onnx` (Apache-2.0) — VAD + TTS runtime.
- `com.microsoft.onnxruntime:onnxruntime-android` (MIT) — ASR runtime.
- `androidx.media` / `androidx.core` — AudioRecord helpers.

## Swap example
```kotlin
var asr: AsrAdapter = MockHindiAsrAdapter()                 // today
asr = IndicConformerAsrAdapter(context, "hi_asr.onnx")      // when trained
var tts: TtsAdapter = MockTtsAdapter()                      // today
tts = SherpaOnnxTtsAdapter(File("/models/mundari_tts"))     // when trained
```

## Known limitations
- No final model is bundled; `IndicConformerAsrAdapter`/`SherpaOnnxTtsAdapter` require trained exports.
- `MockVadAnalyzer`/`Mock*Adapter` are DEV FIXTURES; never ship as production.
- eSpeak fallback is not Mundari; Piper (GPL-3.0) licensing must be handled before redistribution.
- Latency from fixtures is not representative — measure on device (see docs/LATENCY_INSTRUMENTATION.md).
