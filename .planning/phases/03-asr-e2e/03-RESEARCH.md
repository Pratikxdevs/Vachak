# Phase 3: Hindi ASR E2E — Research

**Researched:** 2026-08-29
**Domain:** Offline Hindi ASR on Android (sherpa-onnx OfflineRecognizer + Silero VAD, 16k mono PCM16, ≤1s, 30–80MB, sequential)
**Confidence:** HIGH — runtime already wired on-device; E2E verification + WAV fallback are the remaining unknowns

<user_constraints>
## User Constraints (from CONTEXT.md)

**CRITICAL:** No `.planning/phases/03-asr-e2e/03-CONTEXT.md` exists. Constraints below are reconstructed from authoritative planning docs and MUST be honored. If a future CONTEXT.md is added, it supersedes this section.

### Locked Decisions (from ROADMAP.md:48-59, REQUIREMENTS.md:14-17, docs/PHASES.md:72-89, AGENTS.md)

- **Goal:** `Push-to-talk -> real Hindi transcript on-device, verifiable on emulator without mic` — ROADMAP.md:49, PHASES.md:73
- **Depends on:** Phase 1 (MT) — ROADMAP.md:51; Phase 3 itself does NOT block P1 but is sequenced after P1 shell in dispatch wave 2 (`docs/PHASES.md:165`)
- **No runtime network calls.** All inference local. `AndroidManifest.xml:4` declares NO INTERNET permission — must NOT be added; audited by `OfflineHarnessTest.kt:27` — AGENTS.md:10, ROADMAP.md Phase 5 success #3
- **Sequential model execution only:** ASR -> MT -> TTS, never parallel (2GB RAM limit) — AGENTS.md:11, REQUIREMENTS.md PERF-01, `android/app/.../AudioPipeline.kt:16`
- **Audio contract:** 16k mono PCM16 — PHASES.md:88, `EngineContracts.kt:59`, `MainScreen.kt:183`
- **Budget:** Hindi ASR 30–80 MB slice within ~500 MB total — AGENTS.md:59, REQUIREMENTS.md ASR-01
- **Latency:** ASR ≤1s (of <3s total) — REQUIREMENTS.md ASR-01, `EngineContracts.kt:34-38`, `docs/LATENCY_INSTRUMENTATION.md:16`
- **VAD gated:** Silero VAD via sherpa-onnx must sit in front of ASR — REQUIREMENTS.md ASR-01, PHASES.md:77
- **Verifiable without mic:** WAV-fed debug path required for emulator/headless — REQUIREMENTS.md ASR-02, `docs/phases/PLAN-P3-asr-e2e.md:8`, ROADMAP.md Phase 3 success #2
- **Already WIRED state:** whisper-tiny Hindi + Silero VAD via null AssetManager; mic stable but E2E transcript NOT yet confirmed — task context + `PROJECT.md:15`, `STATE.md:14`, `MODEL_AND_DATA_PROVENANCE.md:20,22`
- **ABI:** arm64-v8a first, x86_64 for emulator — `android/app/build.gradle.kts:14`
- **minSdk 28** (Android 9) — `android/app/build.gradle.kts:11`, AGENTS.md:30
- **Language scope:** Santali-only (Mundari dropped) — ROADMAP.md:5; however existing mocks/interfaces still use `mund`/`hi->mund` — preserve `mund` language codes until Santali (`sat`) swap is explicit in P1/P2; do not rename to `sat` inside P3
- **Never train on IN22-Gen/Conv; license tracking** — AGENTS.md:79, `MODEL_AND_DATA_PROVENANCE.md:34`

### Claude's Discretion

- Which Hindi ASR checkpoint is the *production* pick after benchmarking whisper-tiny vs IndicConformer vs Vosk — provided final pick fits 30–80 MB and ≤1s on 2GB device
- Exact VAD aggressiveness (Silero threshold / chunk size) and whether to expose a tuning param
- WAV ingestion UX (asset-file debug button vs file picker vs `READ_EXTERNAL_STORAGE` debug path) and placement in `MainScreen.kt`
- Whether to keep dual VAD abstractions (`VadAnalyzer` in `:ml` vs `VadDetector` in `:app`) or unify them
- Which `LatencyTracker` to canonicalize (`android/ml/LatencyTracker.kt:45` nanosecond T0–T4 vs `android/app/engine/LatencyTracker.kt:10` millis marks) — pick one for Phase 3 leg and log consistently with `Vachak-ASR`/`Vachak-Latency`

### Deferred Ideas (OUT OF SCOPE — do NOT plan)

- Replacing `MockTranslationEngine` with IndicTrans2 ONNX — P1 — `ROADMAP.md:9`
- Training/fine-tuning Santali VITS voice — P2 — `ROADMAP.md:10`
- Curriculum / worksheets / flashcards — P4 — `ROADMAP.md:12`
- Pack builder + `sync/` installer, per-pack model swap, manifest/sha256 — P5 — `ROADMAP.md:13`
- Full sequential <3s proof on 2GB device, RSS/budget harness — P6 — `ROADMAP.md:14`
- Demo evidence capture (`docs/demo-acceptance.md`) — P7 — `ROADMAP.md:15`
</user_constraints>

<architectural_responsibility_map>
## Architectural Responsibility Map

Single-APK offline Android app with Python export/benchmark scaffolding. All P3 inference is on-device.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Mic capture (AudioRecord, 16k mono PCM16) | Android `:app` — `MainScreen.kt:183` `recordMic()` / `VadStream.kt:135` `runMicrophoneVad()` | — | Must request `RECORD_AUDIO` at runtime (Android 9+), feed VAD in 10–100 ms chunks; no network |
| VAD gating (Silero) | Android `:ml` (`VadStream.kt:43` `SherpaOnnxVadAnalyzer`) + `:app` (`adapter/VadAdapter.kt:15` `VadDetector`) | `sherpa-onnx` AAR 1.13.0 native lib | Saves RAM/latency on 2GB: decode only speech segments; silero_vad.onnx is 632 KB vs whisper 99 MB |
| ASR decode (Hindi) | Android `:ml` (`IndicConformerAsrAdapter.kt:23`) via sherpa-onnx `OfflineRecognizer` | `:app` (`adapter/SherpaAsrAdapter.kt:17`) `ASREngine` wrapper | AGENTS.md mandates sherpa-onnx/ONNX Runtime; `:ml` owns native config, `:app` owns `EngineContracts` surface |
| PCM conversion + WAV parsing | Android `:app` | — | Short PCM16 ↔ Float32 /32768.0f mismatch is the #1 wiring bug (`SherpaAsrAdapter.kt:49`, `IndicConformerAsrAdapter.kt:77`); WAV header strip is required for emulator path |
| Model asset extraction | Android `:ml` `SherpaAssets.kt:22` `prepare()` | — | Recursive copy to `filesDir/vachak_models/{asr,vad}` + `OfflineRecognizer(null, config)` — proven null AssetManager fix |
| UI binding (push-to-talk -> text) | Android `:app` (`ui/MainScreen.kt:92` Button, `MainActivity.kt:17`) | `engine/EngineProvider.kt:47` `real()` | Compose screen only sees `ASREngine` interface; swap happens in one file |
| Latency gate (ASR ≤1s) | Android `:ml` or `:app` `LatencyTracker` | `docs/LATENCY_INSTRUMENTATION.md` T0–T4 | Phase 3 must mark T0 (speech begin) → T1 (ASR result) and log `Vachak-ASR`/`Vachak-Latency`; do NOT claim ≤1s from fixture latency |
| Benchmark pick (whisper vs IndicConformer vs Vosk) | `ml/asr/README.md` + `ml/benchmarks/` | — | AGENTS.md explicitly says benchmark on 2GB device before choosing |

Sequential pipeline is non-negotiable: one model resident at a time, `numThreads=1` in both ASR (`IndicConformerAsrAdapter.kt:38`) and VAD (`VadStream.kt:60`), `AudioPipeline.kt:17` comment.
</architectural_responsibility_map>

<research_summary>
## Summary

Phase 3 completes the Hindi half of the voice pipeline: `Push-to-talk → 16k mono PCM16 → Silero VAD → sherpa-onnx OfflineRecognizer → Hindi text → UI + LatencyTracker`, plus a WAV-fed debug path so the same decode path is verifiable on an emulator with no mic or in headless CI.

The good news: the runtime is already wired. `IndicConformerAsrAdapter.kt:73` creates `OfflineRecognizer(null, config)` from `filesDir/vachak_models/asr` (encoder.onnx 13 MB + decoder.onnx 86 MB + tokens.txt 800 KB = 99 MB on disk, `scripts/fetch_android_models.sh:34-46`). `VadStream.kt:64` creates `Vad(null, cfg)` from `vachak_models/vad/silero_vad.onnx` (632 KB). Both use the vendored `android/ml/libs/sherpa-onnx-1.13.0.aar` (Apache-2.0), `numThreads=1`, `FeatureConfig(16000, 80)`, recursive `SherpaAssets.prepare()` — the null-AssetManager pattern from the cloned `sherpa-onnx/android/SherpaOnnxSimulateStreamingAsr` reference. `engine/EngineProvider.kt:47` already returns `SherpaAsrAdapter(context)` under `real()`, and `ui/MainScreen.kt:92` already captures mic and calls `runVoicePipeline()` → `engine.asr.transcribe(pcm, 16000)`. Mic capture itself is stable (STATE.md:8).

What is *not* yet confirmed is the end-to-end transcript: `SherpaAsrAdapter.kt:48` gates on `vad.detect()` then slices PCM and calls `engine.transcribe(FloatArray, 16000)` per segment (`SherpaAsrAdapter.kt:45-50`), but the screen only shows `tracker.report()` in `asrText` (`MainScreen.kt:99-101`), not the recognized Hindi string, and `AudioPipeline.kt:34` still synthesizes a silent buffer for headless. Three shape mismatches are load-bearing: (1) `:ml` `AsrAdapter.transcribe(FloatArray)` vs `:app` `ASREngine.transcribe(ShortArray, sampleRate)` — bridge is `Short→Float /32768.0f`; (2) two VAD interfaces (`:ml VadAnalyzer` streaming vs `:app VadDetector` whole-buffer) that PLANNER must reconcile; (3) two `LatencyTracker`s (nanosecond `T0–T4` in `:ml` vs millis `marks` map in `:app`) that must be unified for the ASR ≤1s gate. Emulators and CI hosts often have no usable mic source, so `PLAN-P3-asr-e2e.md:8` and REQUIREMENTS.md ASR-02 require a WAV path that strips the RIFF header, validates 16k/mono/PCM16, and reuses the exact `transcribe()` path.

The standard approach for offline Hindi on 2GB is `sherpa-onnx OfflineRecognizer` (whisper-tiny Hindi as the 99 MB baseline) fronted by Silero VAD, with a benchmark gate against IndicConformer (higher accuracy, typically larger/slower unless distilled — see `docs/deep-research-report.md` IndicConformer section) and Vosk (smaller ~50 MB, faster, lower accuracy on code-switched Hindi). whisper.cpp is the third reference but adds a separate JNI/whisper.cpp toolchain not in AGENTS.md and is redundant with sherpa's whisper port. For P3, keep whisper-tiny as the shippable baseline and prove the wiring; defer a production Hindi model swap to a benchmark follow-up if ≤1s/WER tradeoffs require it.

**Primary recommendation:** Keep the existing whisper-tiny DEV-FIXTURE + Silero VAD as the P3 shippable baseline (it is the only Hindi checkpoint already vendored and fits the budget with INT8); fix the E2E wiring (real Hindi text into `ASR result:` not just latency, `isFixture=false` awareness, `Short↔Float` normalization, VAD→segment→decode loop), add a WAV-fed debug path that feeds the identical `SherpaAsrAdapter.transcribe()` entry point, and gate ASR ≤1s with a single canonical `LatencyTracker` logging `Vachak-ASR`. Benchmark Vosk/IndicConformer in `ml/asr/` as a non-blocking P3 stretch — do not block the E2E demo on a model swap.
</research_summary>

<standard_stack>
## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `k2-fsa/sherpa-onnx` (vendored AAR) | 1.13.0 — `android/ml/libs/sherpa-onnx-1.13.0.aar` | Hindi ASR + Silero VAD runtime on Android | AGENTS.md:79, docs/PHASES.md:77, `MODEL_AND_DATA_PROVENANCE.md:20` — Apache-2.0; only runtime that satisfies offline + null AssetManager + OfflineRecognizer/Vad Kotlin API already imported in `IndicConformerAsrAdapter.kt:6-12` and `VadStream.kt:6-8`; proves `OfflineRecognizer(null, config)` pattern (`SherpaAsrAdapter` delegates to it) |
| `sherpa-onnx-whisper-tiny` (multilingual, supports Hindi) | whisper-tiny — `scripts/fetch_android_models.sh:35` → `sherpa-onnx-whisper-tiny.tar.bz2` (encoder.onnx 13M + decoder.onnx 86M + tokens.txt 800K, 99M total on disk) | Baseline Hindi ASR model for P3 | MIT/Apache-2.0 via sherpa releases; only Hindi-capable checkpoint already vendored and wired (`IndicConformerAsrAdapter.kt:38-47` `OfflineWhisperModelConfig(language="hi", task="transcribe")`); fits 30–80 MB budget only if INT8 variant is fetched (script already prefers `*encoder.int8.onnx`, line 38) — unquantized 99 MB is ~19 MB over |
| `silero_vad.onnx` | via `sherpa-onnx` releases — `scripts/fetch_android_models.sh:26` | Speech gating before ASR | Apache-2.0; 632 KB (`vad/silero_vad.onnx`); `VadStream.kt:58` `SileroVadModelConfig(model="$baseDir/silero_vad.onnx")`; saves ~30–70% decode work on 2GB by dropping silence |
| Android `AudioRecord` + `AudioTrack` | SDK 28+ (Android 9) | Mic capture / playback | Standard; `MainScreen.kt:183` `AudioRecord(MIC, 16000, CHANNEL_IN_MONO, PCM_16BIT)` and `VadStream.kt:136` `runMicrophoneVad()` chunk loop (10–100ms, `require(chunkMs in 10..100)`) |
| `androidx.core:core-ktx:1.13.1` + Compose `1.6.8` | as in `android/app/build.gradle.kts` | UI + permission handling | Already in build; `MainActivity.kt:17` injects `EngineProvider.real(this)` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `com.microsoft.onnxruntime:onnxruntime-android` | 1.17–1.20 (MIT) | Direct ONNX Runtime Mobile if IndicConformer export needs custom session options | Only if P3 picks IndicConformer `encoder.onnx + decoder.onnx + joiner.onnx` path (`IndicConformerAsrAdapter.kt:51-55` `TRANSDUCER` branch) that sherpa's transducer wrapper does not fully expose |
| `ml/pipeline/vad_stream.py` + `asr_adapter.py` + `latency.py` | Python scaffolding — `ml/pipeline/` | Parity reference for Kotlin behavior + offline harness | Use to keep Kotlin Vad/Asr/Latency semantics 1:1 with Python; `ml/pipeline/README` equivalent in `android/ml/README.md` |
| `ml/asr/` benchmark scripts | skeleton (`ml/asr/README.md:3`) | On-device WER/latency comparison before model swap | Required by `REQUIREMENTS.md:ASR-01` benchmark gate; run with `ml/asr/benchmark.py` pattern (not yet implemented) |
| WAV asset / file picker | Android SDK | Emulator/headless verification | REQUIREMENTS.md ASR-02; feed 16k mono PCM16 WAV via `ContentResolver` or `assets/test_wavs/hi_hello.wav` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| whisper-tiny via sherpa-onnx | **IndicConformerASR** (`AI4Bharat/IndicConformerASR`, MIT/Apache-2.0; `AI4Bharat/Indic-STT` reference) | Higher Hindi accuracy, streaming-capable, transducer architecture (`IndicConformerAsrAdapter.kt:51` `TRANSDUCER`/`ZIPFORMER_CTC` branches already prepared), but typically >80 MB quantized and requires NeMo/ONNX export + `joiner.onnx`; needs on-device benchmark to prove ≤1s on 2GB. Best as P3+ follow-up if whisper WER is unacceptable |
| whisper-tiny via sherpa-onnx | **Vosk** (`alphacep/vosk-api`, Apache-2.0; `vosk-model-small-hi-0.22` ~50 MB) | Smaller, faster (reported <0.7s on 2GB for short utterances), fully offline, small RAM; accuracy trails IndicConformer/whisper on code-switched Hindi + numbers; no `joiner` complexity; valid budget fallback if whisper 99 MB still over after INT8 |
| whisper-tiny via sherpa-onnx | **whisper.cpp** (`ggerganov/whisper.cpp`, MIT; `whisper-tiny` ggml ~75 MB) | Adds separate native toolchain/JNI beyond the single vendored `sherpa-onnx-1.13.0.aar`; sherpa already wraps whisper, so whisper.cpp is redundant unless sherpa's whisper decode proves too slow — not recommended for P3 |
| Sherpa `OfflineRecognizer` (non-streaming) | Sherpa `OnlineRecognizer` / streaming | Streaming reduces perceived latency but adds endpointing complexity; P3 goal is offline E2E verification with ≤1s *utterance* decode — non-streaming `OfflineRecognizer.decode(stream)` (`IndicConformerAsrAdapter.kt:80-82`) is simpler and budgeted; streaming is a P6 optimization |
| Silero VAD (native) | `MockVadAnalyzer` / `MockVadDetector` energy gate | Mock (`VadStream.kt:89` energy threshold, `VidAdapter.kt:26` whole-buffer) is wiring-only; not a neural detector — never ship; keep as `useReal=false` fallback for unit tests only (`SherpaAsrAdapter.kt:39`) |

**Installation:**

```bash
# Models — already fetched into assets by:
bash scripts/fetch_android_models.sh
#   -> android/app/src/main/assets/vachak_models/{asr/{encoder,decoder,tokens},vad/{silero_vad.onnx},tts/...}

# Android — already in build.gradle.kts:
#   android/ml:  implementation(files("libs/sherpa-onnx-1.13.0.aar"))
#   android/app: implementation(project(":ml"))

# Python benchmark env (when adding WER/latency harness):
pip install onnxruntime soundfile librosa jiwer  # jiwer for WER/CER, soundfile for WAV IO
```

</standard_stack>

<architecture_patterns>
## Architecture Patterns

### System Architecture Diagram

P3 data flow (offline, sequential — first leg only; later legs belong to P1/P2):

```
[Push-to-talk Button]  [WAV debug path (asset/file picker)]  [Mic permission]
        |                           |                              |
        v                           v                              v
  AudioRecord (16k mono PCM16)  RIFF strip -> ShortArray PCM16   RECORD_AUDIO grant
        |                           |                              |
        +-------------+-------------+------------------------------+
                      |
                      v
            SherpaAssets.prepare("vad"/"asr") -> filesDir/vachak_models/{vad,asr}
                      |
                      v
          VadDetector/SherpaOnnxVadAnalyzer (Silero, numThreads=1)
            10-100ms chunks via VadStream.runMicrophoneVad()
                      |
                      v
           detect(ShortArray PCM16) -> List<SpeechSegment> [startMs,endMs]
                      |
                 segments empty? --yes--> return "" (silence) + log
                      |
                      no
                      v
           For each segment: slice PCM16 -> FloatArray = short/32768.0f
                      |
                      v
           IndicConformerAsrAdapter / OfflineRecognizer
             OfflineRecognizerConfig(
               featConfig=FeatureConfig(16000, 80),
               modelConfig=OfflineModelConfig(
                 numThreads=1, tokens="$baseDir/tokens.txt",
                 whisper=OfflineWhisperModelConfig(encoder, decoder, language="hi"))
             )  -- null AssetManager (filesDir) --
                      |
                      v
           createStream() -> acceptWaveform(FloatArray, 16000) -> decode() -> getResult().text
                      |
                      v
           LatencyTracker: T0 (speech begin / first VAD frame) -> T1 (ASR text ready)
                      |
                      v
           UI: LessonTranslatorScreen "ASR result:" Text  +  Log.d("Vachak-ASR", ...) + tracker.report()
                      |
                      v
           (Phase 6 merges: ASR -> MT (P1) -> TTS (P2) sequential; Phase 3 measured alone must be ≤1s)
```

### Recommended Project Structure

```
android/
├── ml/                         # :ml owns native runtime + model IO
│   ├── libs/sherpa-onnx-1.13.0.aar
│   ├── src/main/java/com/vachak/ml/
│   │   ├── SherpaAssets.kt           # prepare("vad"/"asr") recursive copy
│   │   ├── IndicConformerAsrAdapter.kt  # OfflineRecognizer (WHISPER/TRANSDUCER/ZIPFORMER/NEMO)
│   │   ├── VadStream.kt              # SherpaOnnxVadAnalyzer + MockVadAnalyzer + runMicrophoneVad
│   │   ├── AsrAdapter.kt             # FloatArray API (internal)
│   │   ├── LatencyTracker.kt         # T0–T4 nanosecond tracker (ml variant)
│   │   └── orchestrator/             # future: EngineContracts bridge
│   └── src/main/assets/vachak_models/{asr,vad}  # actually under :app assets, extracted to filesDir
├── app/                        # :app owns EngineContracts surface + UI
│   ├── src/main/java/com/vachak/
│   │   ├── engine/
│   │   │   ├── EngineContracts.kt        # ASREngine: transcribe(ShortArray, sampleRate): EngineResult<String>
│   │   │   ├── EngineProvider.kt         # real() -> SherpaAsrAdapter(context)
│   │   │   └── LatencyTracker.kt         # millis marks variant (app variant)
│   │   ├── ml/adapter/
│   │   │   ├── SherpaAsrAdapter.kt       # ASREngine impl: VAD -> segment -> Float convert -> IndicConformerAsrAdapter
│   │   │   ├── VadAdapter.kt             # VadDetector + MockVadDetector (whole-buffer)
│   │   │   ├── AudioPipeline.kt          # captureAndRecognize() (currently silent buffer)
│   │   │   └── SherpaTtsAdapter.kt       # TTS (not in P3)
│   │   └── ui/
│   │       ├── MainActivity.kt           # injects EngineProvider.real(this)
│   │       └── MainScreen.kt             # recordMic() + runVoicePipeline() + Push-to-talk + ASR result + WAV fallback
│   └── src/androidTest/.../OfflineHarnessTest.kt
ml/
├── asr/                        # benchmark harness (skeleton)
├── pipeline/                   # Python parity: vad_stream.py, asr_adapter.py, latency.py
└── benchmarks/                 # P6 harness (do not use to claim P3 latency)
scripts/fetch_android_models.sh
```

### Pattern 1: Sherpa OfflineRecognizer with null AssetManager (filesDir path)

**What:** Extract `assets/vachak_models/asr/` to `context.filesDir/vachak_models/asr/` via `SherpaAssets.prepare()`, then construct `OfflineRecognizer(null, config)` pointing at filesystem paths.

**When to use:** Every sherpa-onnx Android adapter in this repo — ASR, VAD, TTS all follow it. The AssetManager variant crashes on nested asset subdirs; `SherpaAssets` recursive copy + null manager is the proven fix (STATE.md:7).

**Example:**
```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:68-74
@Synchronized
private fun ensureLoaded() {
    if (recognizer != null) return
    val baseDir = SherpaAssets.prepare(context, modelDir) // "asr" -> filesDir/vachak_models/asr
    Log.d(tag, "creating OfflineRecognizer (kind=$modelKind, dir=$baseDir)")
    // Models are extracted to the filesystem (filesDir), so pass null AssetManager.
    recognizer = OfflineRecognizer(null, buildConfig(baseDir))
    Log.d(tag, "OfflineRecognizer ready")
}

private fun buildConfig(baseDir: String): OfflineRecognizerConfig {
    // Source: android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:35-64
    val modelConfig = OfflineModelConfig(
        numThreads = 1,
        tokens = "$baseDir/tokens.txt",
        whisper = OfflineWhisperModelConfig(
            encoder = "$baseDir/encoder.onnx",
            decoder = "$baseDir/decoder.onnx",
            language = "hi",
            task = "transcribe",
            tailPaddings = 1000
        )
    )
    return OfflineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
        modelConfig = modelConfig
    )
}

override fun transcribe(samples: FloatArray, sampleRate: Int): AsrResult {
    ensureLoaded()
    val stream = recognizer!!.createStream()
    stream.acceptWaveform(samples, sampleRate)
    recognizer!!.decode(stream)
    val text = recognizer!!.getResult(stream).text
    stream.release()
    Log.d(tag, "transcribed ${samples.size} samples @ $sampleRate Hz -> \"$text\"")
    return AsrResult(text = text, confidence = 1.0f, isFixture = false) // flip isFixture when real model confirmed
}
```

### Pattern 2: VAD-gated segmentation before decode (saves RAM, enforces ≤1s)

**What:** Split raw PCM into speech segments with Silero VAD, decode each segment separately, join non-empty transcripts. Empty-segment -> return `""` (do not decode silence).

**When to use:** Every ASR utterance on 2GB — decoding silence wastes ~70% of the 1s budget.

**Example:**
```kotlin
// Source: android/app/src/main/java/com/vachak/ml/adapter/SherpaAsrAdapter.kt:41-53
override fun transcribe(pcm16: ShortArray, sampleRateHz: Int): EngineResult<String> {
    if (sampleRateHz <= 0) return EngineResult.Err(EngineError.INVALID_INPUT, "bad sample rate")
    if (pcm16.isEmpty()) return EngineResult.Err(EngineError.INVALID_INPUT, "empty pcm")
    if (context == null || !useReal) return EngineResult.Ok("[DEV-FIXTURE-asr] नमस्ते")

    val engine = real ?: IndicConformerAsrAdapter(context!!)
    val segments = vad.detect(pcm16, sampleRateHz)
    if (segments.isEmpty()) return EngineResult.Ok("") // silence -> no decode

    val texts = segments.mapNotNull { seg ->
        val s = (seg.startMs * sampleRateHz / 1000).coerceAtLeast(0)
        val e = (seg.endMs * sampleRateHz / 1000).coerceAtMost(pcm16.size)
        if (e <= s) return@mapNotNull null
        val floatSeg = FloatArray(e - s) { pcm16[s + it] / 32768.0f } // Short PCM16 -> Float32
        engine.transcribe(floatSeg, sampleRateHz).text
    }.filter { it.isNotBlank() }

    return EngineResult.Ok(texts.joinToString(" ").trim())
}
```

**Streaming mic variant (10–100 ms chunks, reference):**
```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/VadStream.kt:135-151
fun runMicrophoneVad(record: AudioRecord, analyzer: VadAnalyzer, chunkMs: Int = 100, onSegment: (VadSegment) -> Unit) {
    require(chunkMs in 10..100) { "chunkMs must be in [10, 100]" }
    val chunk = (chunkMs / 1000f * record.sampleRate).toInt()
    val buffer = FloatArray(chunk)
    while (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        val read = record.read(buffer, 0, chunk, AudioRecord.READ_BLOCKING)
        if (read <= 0) break
        analyzer.accept(buffer.copyOf(read))
        analyzer.popSegment()?.let(onSegment)
    }
    analyzer.flush()
    analyzer.popSegment()?.let(onSegment)
}
```

### Pattern 3: WAV-fed debug path (emulator/headless verification)

**What:** Load a 16k mono PCM16 WAV from `assets/test_wavs/` or via `ActivityResultContracts.GetContent()`, strip the 44-byte RIFF header (or use `android.media.MediaExtractor`-style parser), validate `sampleRate==16000 && channels==1 && bits==16`, convert to `ShortArray`, then call the *same* `ASREngine.transcribe()` entry point as the mic path. No second decode path.

**When to use:** Every CI/headless run and every Studio emulator without a usable mic — per `REQUIREMENTS.md:ASR-02`.

**Example:**
```kotlin
// Recommended for Phase 3 (not yet in repo — to be added in PLAN-P3):
fun loadWavPcm16Mono16k(context: Context, uri: Uri): ShortArray {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val bytes = input.readBytes()
        // Minimal RIFF/WAV header parse (canonical 44-byte header; handle JUNK/extra chunks robustly)
        require(bytes.size > 44 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) { "not a WAV" }
        val sampleRate = (bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or
                         ((bytes[26].toInt() and 0xFF) shl 16) or ((bytes[27].toInt() and 0xFF) shl 24)
        val channels   = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)
        val bits       = (bytes[34].toInt() and 0xFF) or ((bytes[35].toInt() and 0xFF) shl 8)
        require(sampleRate == 16000) { "WAV must be 16k (got $sampleRate)" }
        require(channels == 1)       { "WAV must be mono (got $channels ch)" }
        require(bits == 16)          { "WAV must be PCM16 (got $bits-bit)" }
        // Find "data" chunk (skip JUNK/fact)
        var dataOffset = 12
        while (dataOffset + 8 <= bytes.size) {
            val id = String(bytes.sliceArray(dataOffset until dataOffset+4))
            val sz = (bytes[dataOffset+4].toInt() and 0xFF) or ((bytes[dataOffset+5].toInt() and 0xFF) shl 8) or
                     ((bytes[dataOffset+6].toInt() and 0xFF) shl 16) or ((bytes[dataOffset+7].toInt() and 0xFF) shl 24)
            if (id == "data") { dataOffset += 8; break }
            dataOffset += 8 + sz
        }
        val pcmBytes = bytes.sliceArray(dataOffset until bytes.size)
        return ShortArray(pcmBytes.size / 2) { i ->
            ((pcmBytes[i*2].toInt() and 0xFF) or ((pcmBytes[i*2+1].toInt() and 0xFF) shl 8)).toShort()
        }
    } ?: error("cannot open WAV")
}

// Reuse the identical ASR path:
val pcm = loadWavPcm16Mono16k(context, wavUri)
val result = engine.asr.transcribe(pcm, 16000) // same entry point as mic
```

### Pattern 4: 16k mono PCM16 contract enforcement + Short↔Float normalization

**What:** Mic path produces `ShortArray` PCM16; sherpa `acceptWaveform` expects `FloatArray` in [-1, 1]. Normalize once at the segment boundary: `float = short / 32768.0f`. Validate `sampleRate==16000` at both entry points; reject or resample otherwise (do not silently mis-decode).

**When to use:** Always — the repo has two `transcribe` signatures (`AsrAdapter(FloatArray)` in `:ml` vs `ASREngine(ShortArray)` in `:app`) that are bridged by this conversion (`SherpaAsrAdapter.kt:49`).

### Anti-Patterns to Avoid

- **Decoding silence:** Calling `OfflineRecognizer.decode()` on an empty or long-silence buffer wastes the 1s budget and can return spurious hallucinations. Gate on VAD first (`SherpaAsrAdapter.kt:43`).
- **Two decode codepaths (mic vs WAV):** Duplicating `createStream/acceptWaveform/decode` for WAV invites drift — WAV must feed the same `ASREngine.transcribe(ShortArray)` entry point as mic.
- **Forgetting `stream.release()`:** Each `createStream()` allocates native memory; leak = OOM on 2GB after a few utterances (`IndicConformerAsrAdapter.kt:83` shows the correct release).
- **Parallel ASR + TTS:** Holding ASR and TTS native handles simultaneously exceeds 2GB; `AudioPipeline.kt:17` enforces sequential only, `numThreads=1`.
- **Hardcoding `isFixture=true` after E2E is verified:** `IndicConformerAsrAdapter.kt:85` currently returns `isFixture=true` because transcript is not yet confirmed; flip to `false` (or compute from model source) once P3 proves real Hindi text — otherwise latency harness falsely tags runs as fixtures.
- **Capturing wall-clock (`currentTimeMillis`) for latency:** Use monotonic `SystemClock.elapsedRealtimeNanos()` (`android/ml/LatencyTracker.kt:52`) — wall clock skews on sleep/NTP.
- **Adding `android.permission.INTERNET` for WAV picker:** The picker uses `ContentResolver`/`AssetManager` — no network permission needed; adding it breaks the offline audit (`OfflineHarnessTest.kt:27`).
</architecture_patterns>

<dont_hand_roll>
## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Speech detection / endpointing | Custom energy threshold or zero-crossing detector (`VadStream.kt:89` `MockVadAnalyzer` is wiring-only) | `sherpa-onnx` Silero VAD (`SherpaOnnxVadAnalyzer` `VadStream.kt:43`, `silero_vad.onnx` 632 KB) | Energy gate is fooled by fan noise, mouth clicks, silence-as-speech; Silero handles Hindi phonetics and has `Vad(empty())/front()/pop()` streaming API already in AAR |
| WAV header parsing | `bytes.sliceArray(44 until ...)` assuming 44-byte header | Proper RIFF chunk walk (find `"data"` chunk, skip `JUNK`/`fact`/`LIST`) | Many WAVs from recorders/editors have non-44 headers; hard-coded offset truncates or leaks header bytes into PCM, causing decode garbage |
| PCM16 ↔ Float32 conversion | Manual `byte -> short -> float` without clamping | `/ 32768.0f` with `coerceIn(-1f, 1f)` and little-endian `Short` read | Endianness, sign extension, and clipping are easy to get wrong; sherpa expects Float32 in [-1,1] derived from Short PCM16 |
| AudioRecord lifecycle | Raw `AudioRecord` read in UI thread | `VadStream.runMicrophoneVad()` background thread pattern (`VadStream.kt:135`) or `AudioPipeline.captureAndRecognize()` (`AudioPipeline.kt:28`) | UI thread capture janks Compose, misses permission edge cases, and needs `RECORDSTATE_RECORDING` guard |
| Resampling | Ad-hoc linear interpolation 8k/44.1k → 16k | Current P3 scope: *reject non-16k with `EngineError.INVALID_INPUT`*; resampling is a later stretch using `libsamplerate`/`Sonic` or sherpa's `FeatureConfig` | On-device resampling adds latency and CPU on 2GB; budget only covers 16k native capture |
| ASR confidence / N-best | Invent a confidence heuristic over transcripts | Return `AsrResult.confidence=1.0f` for whisper (no native confidence) and surface raw text; WER is the real gate (`docs/benchmarks/BENCHMARK_REPORT.md` ASR table) | Whisper-style models do not emit calibrated confidences; any hand-rolled score is misleading for pedagogy |
| Latency measurement | `System.currentTimeMillis()` deltas in UI code | Canonical `LatencyTracker` (`android/ml/LatencyTracker.kt:45` or `engine/LatencyTracker.kt:10` — pick one) with `Vachak-ASR`/`Vachak-Latency` Log tags | Wall-clock skew, missing T0 capture, and dual trackers double-count; `docs/LATENCY_INSTRUMENTATION.md:6-11` defines T0–T4 precisely |

**Key insight:** The hardest part of offline ASR on Android is not the model — it is the audio plumbing (PCM format, VAD gating, stream lifetime) and making the *same* path work for both mic and WAV without branching. The existing `SherpaAssets` + `OfflineRecognizer(null, ...)` + Silero VAD + `Short→Float` bridge is the proven glue; hand-rolling any of those pieces reintroduces the bugs already fixed in P0.
</dont_hand_roll>

<common_pitfalls>
## Common Pitfalls

### Pitfall 1: PCM type mismatch — Short PCM16 fed as Float, or Float fed as Short

**What goes wrong:** ASR returns empty or hallucinated text, or crashes with native buffer misread. Transcript is not plausible Hindi.

**Why it happens:** Repo has two `transcribe` surfaces: `:ml AsrAdapter.transcribe(samples: FloatArray, sampleRate: Int)` (`AsrAdapter.kt:14`) and `:app ASREngine.transcribe(pcm16: ShortArray, sampleRateHz: Int)` (`EngineContracts.kt:59`). `MainScreen.kt:183` captures `ShortArray`; `IndicConformerAsrAdapter.kt:77` decodes `FloatArray`. The bridge (`/32768.0f`) lives only in `SherpaAsrAdapter.kt:49`; bypassing that adapter or calling `:ml` directly with `ShortArray` bytes misinterprets PCM.

**How to avoid:** Always go through `SherpaAsrAdapter.transcribe(ShortArray)` — it does `FloatArray(e-s){ pcm16[s+it]/32768.0f }`. For WAV, produce `ShortArray` and reuse the same entry point. Add a debug assert: `pcm.size * sampleRate` sanity.

**Warning signs:** Log `transcribed ${samples.size} samples @ $sampleRate Hz -> ""` with non-zero samples; transcript length 0 despite audible speech; `Vachak-ASR` logs show samples fed but result empty.

### Pitfall 2: Asset path vs filesystem path — `AssetManager` crash or model not found

**What goes wrong:** `OfflineRecognizer` constructor throws `file not found` or native `cannot open tokens.txt: No such file or directory` even though assets exist.

**Why it happens:** Passing `context.assets` + asset-relative path (`"vachak_models/asr/tokens.txt"`) instead of filesystem path. sherpa-onnx Android API expects *filesystem* paths when given a `null` AssetManager — see `IndicConformerAsrAdapter.kt:73` comment and `SherpaOnnxTtsAdapter.kt:37`.

**How to avoid:** Always `val baseDir = SherpaAssets.prepare(context, "asr")` then `OfflineRecognizer(null, config)` with `"$baseDir/tokens.txt"` etc. Verify `SherpaAssets: copied asset:` logs appear (`SherpaAssets.kt:34`).

**Warning signs:** `asset copy failed: vachak_models/asr/...` in logcat; `Vachak-ASR: creating OfflineRecognizer` never reaches `OfflineRecognizer ready` (`IndicConformerAsrAdapter.kt:74`).

### Pitfall 3: Long mic capture without VAD — exceeds 1s / OOM on 2GB

**What goes wrong:** User holds push-to-talk for 4s (`MainScreen.kt:96` `recordMic(16000,4000)`), entire 64k samples decoded as one chunk, decode takes >1s or RSS spikes, <3s budget fails on P6 even though ASR alone is within budget on a short utterance.

**Why it happens:** `MainScreen.kt:96` captures fixed 4000 ms; `SherpaAsrAdapter.kt:42` segments via VAD, but `MainScreen.kt:96` currently bypasses `SherpaAsrAdapter` and calls `runVoicePipeline` which in turn calls `engine.asr.transcribe(pcm,16000)` as one buffer — so segmentation depends on which adapter is wired. Long silence before speech dominates decode time.

**How to avoid:** Keep VAD segmentation in the hot path (use `SherpaAsrAdapter` with `VadDetector`, not direct `IndicConformerAsrAdapter`). In `MainScreen.kt:97` mark `T0` at first VAD speech frame, not at `recordMic` start. For WAV, reuse the same segmentation.

**Warning signs:** `LatencyTracker` reports `asr > 1000ms` only on long recordings; short 1s utterance is <500ms; `Vachak-ASR: transcribed 64000 samples` vs `transcribed 16000 samples` variance.

### Pitfall 4: WAV header fed into PCM — decode garbage on emulator path

**What goes wrong:** WAV verification passes on device mic but fails on WAV: transcript is empty or contains random Latin characters even though WAV plays correctly in a media player.

**Why it happens:** Feeding the 44+ byte RIFF header as PCM, or assuming mono 16k when WAV is 44.1k stereo, or reading big-endian. Emulator WAVs often come from desktop recorders with `JUNK` chunks or 48k sample rate.

**How to avoid:** Proper RIFF parse (find `"data"` chunk), validate `sampleRate==16000`, `channels==1`, `bits==16`, then `ShortArray(data.size/2){ (data[i*2] & 0xFF) | ((data[i*2+1] & 0xFF) shl 8) }` little-endian. Reject non-conforming WAVs with a clear `Err(INVALID_INPUT, "WAV must be 16k mono PCM16")` rather than silently resampling.

**Warning signs:** WAV path `ASR result:` is always `""` or `"[hallucination]"`; mic path works for same speaker; WAV file size not equal to `44 + samples*2`.

### Pitfall 5: Dual LatencyTracker divergence — ASR ≤1s gate not enforceable

**What goes wrong:** One tracker reports ASR 600ms, the other 1200ms for the same utterance; P6 proof cannot decide if budget is green; `Vachak-Latency` logs are inconsistent across runs.

**Why it happens:** Two trackers coexist: `android/ml/LatencyTracker.kt:45` (`LatencyTracker(sample)` / nanosecond `T0–T4` + `isFixture` flag) and `android/app/engine/LatencyTracker.kt:10` (`marks: Map<String,Long>` / `currentTimeMillis`). `MainScreen.kt` uses the app variant (`engine.LatencyTracker`), `ml/pipeline/latency.py` mirrors the ml variant. They have different `start()/mark()/report()` semantics.

**How to avoid:** Choose one canonical tracker for Phase 3. Recommendation: use `android/ml/LatencyTracker.kt:45` style with `SystemClock.elapsedRealtimeNanos()` and explicit `markAsr(text)` at `T1`, log via `Log.d("Vachak-Latency", ...)` and `Log.d("Vachak-ASR", "transcribed ...")`. Make `MainScreen.kt:97` call `tracker.mark_speech_begin()` at T0 and `tracker.mark_asr(text)` at T1. Record `isFixture` correctly so P6 can filter.

**Warning signs:** `report(): total=620` but sum of marks ≠ total; `withinBudget` flips on same audio; `Vachak-Latency` tag never appears in `adb logcat -s Vachak-*`.

### Pitfall 6: Showing latency report instead of recognized Hindi text

**What goes wrong:** Acceptance criterion #1 fails even though ASR decoded correctly: UI shows `pcm 64000 samples (see logcat Vachak-ASR)` (`MainScreen.kt:99-101`) instead of Hindi like `आज हम ...`.

**Why it happens:** `MainScreen.kt:99` maps `runVoicePipeline` result to `asrText = r.fold(onOk = { "pcm ${it.size} samples ..." })` — `runVoicePipeline` returns `ShortArray` TTS PCM, not the intermediate ASR text. The ASR text is internal to `runVoicePipeline` and not surfaced.

**How to avoid:** In Phase 3, push-to-talk must capture and display the *ASR transcript* directly (call `engine.asr.transcribe(pcm,16000)` and assign `asrText = (asr as Ok).value`). The full `ASR->MT->TTS` chain is the P6 pipeline; P3's PTT button is the Hindi capture proof, not the E2E translate proof. Keep a separate `asrText` state from `ttsPcm`.

**Warning signs:** `ASR result: pcm 64000 samples` in screenshot review; `Vachak-ASR: transcribed ... -> "नमस्ते"` in logcat but UI never shows Devanagari.

### Pitfall 7: Budget overshoot — 99 MB whisper-tiny + 632 KB VAD + 54 MB TTS fixture leaves no headroom

**What goes wrong:** APK+pack exceeds ~500 MB; Phase 6 budget proof fails even though each model individually seems small; P5 pack cannot be versioned.

**Why it happens:** Unquantized whisper-tiny is 99 MB on disk; `scripts/fetch_android_models.sh:38` already looks for `*encoder.int8.onnx` but falls back to float. Combined with `tts/vits-zh-aishell3` 59 MB dev fixture (to be replaced) and `vad/silero_vad.onnx` 632 KB, the sum pushes the `AGENTS.md:54` 30–80 MB ASR + 20–80 MB TTS + 100–180 MB MT budget to the edge.

**How to avoid:** In Phase 3, ensure `scripts/fetch_android_models.sh` actually fetches INT8 whisper artifacts and re-measure (`du -ch asr/*`). Document the on-disk size in `docs/MODEL_AND_DATA_PROVENANCE.md` and `THIRD_PARTY_NOTICES.md`. For the final Hindi pick, require INT8 quantization as part of the `ml/asr/README.md` benchmark gate before promoting beyond whisper-tiny.

**Warning signs:** `du -ch android/app/src/main/assets/vachak_models/asr/*` reports 99M total; `ROADMAP.md` Phase 6 success #2 never greens; pack manifest `sizeBytes` cumulative >500M.

</common_pitfalls>

<code_examples>
## Code Examples

Verified patterns from official sources:

### Hindi ASR — sherpa-onnx OfflineRecognizer (whisper, Hindi)

```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:35-85
// Also: sherpa-onnx/android/SherpaOnnxSimulateStreamingAsr/app/src/main/java/.../SimulateStreamingAsr.kt
val baseDir = SherpaAssets.prepare(context, "asr") // filesDir/vachak_models/asr

val modelConfig = OfflineModelConfig(
    numThreads = 1,
    tokens = "$baseDir/tokens.txt",
    whisper = OfflineWhisperModelConfig(
        encoder = "$baseDir/encoder.onnx",
        decoder = "$baseDir/decoder.onnx",
        language = "hi",
        task = "transcribe",
        tailPaddings = 1000
    )
)
val config = OfflineRecognizerConfig(
    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
    modelConfig = modelConfig
)
val recognizer = OfflineRecognizer(null, config) // null AssetManager — filesDir, not assets

// Per-utterance decode (ensure sequential, one stream at a time):
val stream = recognizer.createStream()
stream.acceptWaveform(floatSamples, 16000)
recognizer.decode(stream)
val text = recognizer.getResult(stream).text // Hindi Devanagari string
stream.release() // MUST release — native handle
```

### Transducer/CTC swap (IndicConformer future — same adapter, different branches)

```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:50-59
OfflineModelConfig(numThreads = 1, tokens = "$baseDir/tokens.txt").apply {
    when (modelKind) {
        ModelKind.TRANSDUCER      -> transducer = OfflineTransducerModelConfig(
            encoder = "$baseDir/encoder.onnx", decoder = "$baseDir/decoder.onnx", joiner = "$baseDir/joiner.onnx")
        ModelKind.ZIPFORMER_CTC   -> zipformerCtc = OfflineZipformerCtcModelConfig(model = "$baseDir/model.onnx")
        ModelKind.NEMO            -> nemo = OfflineNemoEncDecCtcModelConfig(model = "$baseDir/model.onnx")
        ModelKind.WHISPER         -> whisper = OfflineWhisperModelConfig(/* as above */)
    }
}
```

### Mic capture (16k mono PCM16, 4 s max, background thread)

```kotlin
// Source: android/app/src/main/java/com/vachak/ui/MainScreen.kt:183-200
private fun recordMic(sampleRate: Int = 16000, maxMs: Int = 4000): ShortArray {
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, sampleRate * 2))
    val n = (sampleRate * maxMs) / 1000
    val buf = ShortArray(n)
    rec.startRecording()
    var off = 0
    while (off < n) {
        val read = rec.read(buf, off, minOf(n - off, minBuf / 2))
        if (read <= 0) break
        off += read
    }
    rec.stop(); rec.release()
    return buf // caller must request RECORD_AUDIO at runtime before this
}
```

### VAD-gated decode via ASREngine surface

```kotlin
// Source: android/app/src/main/java/com/vachak/ml/adapter/SherpaAsrAdapter.kt:36-53
override fun transcribe(pcm16: ShortArray, sampleRateHz: Int): EngineResult<String> {
    if (sampleRateHz <= 0) return EngineResult.Err(EngineError.INVALID_INPUT, "bad sample rate")
    if (pcm16.isEmpty())    return EngineResult.Err(EngineError.INVALID_INPUT, "empty pcm")
    if (context == null || !useReal) return EngineResult.Ok("[DEV-FIXTURE-asr] नमस्ते")

    val engine = real ?: IndicConformerAsrAdapter(context!!)
    val segments = vad.detect(pcm16, sampleRateHz)
    if (segments.isEmpty()) return EngineResult.Ok("")

    val texts = segments.mapNotNull { seg ->
        val s = (seg.startMs * sampleRateHz / 1000).coerceAtLeast(0)
        val e = (seg.endMs * sampleRateHz / 1000).coerceAtMost(pcm16.size)
        if (e <= s) return@mapNotNull null
        val floatSeg = FloatArray(e - s) { pcm16[s + it] / 32768.0f }
        engine.transcribe(floatSeg, sampleRateHz).text
    }.filter { it.isNotBlank() }
    return EngineResult.Ok(texts.joinToString(" ").trim())
}
```

### Silero VAD (sherpa-onnx) — real analyzer

```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/VadStream.kt:43-86
class SherpaOnnxVadAnalyzer(private val context: Context, private val modelDir: String = "vad") : VadAnalyzer {
    private var vad: Vad? = null
    @Synchronized private fun ensure(): Vad {
        if (vad != null) return vad!!
        val baseDir = SherpaAssets.prepare(context, modelDir)
        val cfg = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(model = "$baseDir/silero_vad.onnx"),
            sampleRate = 16000, numThreads = 1
        )
        vad = Vad(null, cfg) // null AssetManager, filesDir
        return vad!!
    }
    override fun accept(chunk: FloatArray) { ensure().acceptWaveform(chunk) }
    override fun isSpeech(): Boolean = ensure().isSpeechDetected()
    override fun popSegment(): VadSegment? {
        val v = ensure(); if (v.empty()) return null
        val seg = v.front(); v.pop()
        return VadSegment(seg.samples, 16000, 0f, seg.samples.size / 16000f)
    }
    override fun flush() = ensure().flush()
}
```

### Latency instrumentation (T0 speech begin → T1 ASR ready, sequential)

```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/LatencyTracker.kt:45-52
// Mirror: ml/pipeline/latency.py  and  docs/LATENCY_INSTRUMENTATION.md:6
class LatencyTracker(private val sample: LatencySample) {
    fun markSpeechBegin(text: String = "") { if (sample.t0SpeechBegin == null) sample.t0SpeechBegin = now(); sample.asrText = text }
    fun markAsr(text: String = "") { sample.t1Asr = now(); if (text.isNotEmpty()) sample.asrText = text }
    fun result(): LatencySample = sample
    private fun now(): Long = SystemClock.elapsedRealtimeNanos() // monotonic
}
// Usage in Push-to-talk:
val tracker = LatencyTracker(LatencySample(runId = "ptt-${System.currentTimeMillis()}"))
tracker.markSpeechBegin()
// ... vad + asr ...
tracker.markAsr(resultText)
Log.d("Vachak-ASR", "transcribed ${pcm.size} samples -> \"$resultText\"")
Log.d("Vachak-Latency", tracker.result().let { "${it.endToEndMs()}ms isFixture=${it.isFixture}" })
```

### Engine wiring (single injection point)

```kotlin
// Source: android/app/src/main/java/com/vachak/engine/EngineProvider.kt:47-55
fun real(context: Context): EngineProvider = EngineProvider(
    translation = MockTranslationEngine, // P1 replaces
    asr = SherpaAsrAdapter(context),     // P3 verifies
    tts = SherpaTtsAdapter(context),
    curriculum = MockCurriculumEngine,
    worksheet = MockWorksheetEngine, flashcard = MockFlashcardEngine,
    packs = MockLanguagePackManager, sync = MockSyncManager, benchmark = MockBenchmarkRunner
)
// Also: android/app/src/main/java/com/vachak/ui/MainActivity.kt:14-18
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LessonTranslatorScreen(engine = EngineProvider.real(this), lessonId = "L1") }
    }
}
```

### Offline audit (no INTERNET permission)

```kotlin
// Source: android/app/src/androidTest/java/com/vachak/offline/OfflineHarnessTest.kt:27-31
@Test fun app_has_no_internet_permission() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val hasInternet = ctx.checkSelfPermission(android.Manifest.permission.INTERNET) ==
        PackageManager.PERMISSION_GRANTED
    assertFalse("Offline app must NOT hold INTERNET permission", hasInternet)
}
```
</code_examples>

<sota_updates>
## State of the Art (2024-2025)

What's changed recently:

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Vosk` as primary Hindi ASR for low-end devices | `sherpa-onnx OfflineRecognizer` with `whisper-tiny`/`zipformer-ctc` is now the reference for offline Hindi on Android | 2023–2024 (sherpa-onnx 1.4→1.13) | sherpa gives a single AAR for ASR/TTS/VAD with `OfflineRecognizer`/`Vad`/`OfflineTts` Kotlin APIs; Vosk remains valid as a ~50 MB fallback but is no longer the default recommendation in docs/PHASES.md |
| Passing `AssetManager` (`context.assets`) to sherpa constructors | Passing `null` AssetManager + filesystem paths under `context.filesDir` | Fixed in P0 (STATE.md:7) | AssetManager variant fails on nested `assets/vachak_models/{asr,vad,tts}` subdirs; filesystem pattern is now proven (`IndicConformerAsrAdapter.kt:73` comment) |
| Whisper float32 encoder/decoder (~100 MB) | Whisper `encoder.int8.onnx` / `decoder.int8.onnx` INT8 quantized (sherpa releases) | 2023–2024 (sherpa INT8 artifacts) | Script already prefers `*encoder.int8.onnx` (`scripts/fetch_android_models.sh:38`); INT8 brings whisper-tiny from 99 MB toward 50–70 MB and is critical for staying in 30–80 MB ASR budget |
| Custom `AudioRecord` read loop on UI thread | `VadStream.runMicrophoneVad` chunked background loop (10–100ms) | Phase 5A reference (`VadStream.kt:135`) | UI-thread capture janks Compose and misses `READ_BLOCKING` semantics; background chunk loop is the WASM/Android reference |
| Wall-clock `System.currentTimeMillis()` for latency | Monotonic `SystemClock.elapsedRealtimeNanos()` | `docs/LATENCY_INSTRUMENTATION.md:6`, `android/ml/LatencyTracker.kt:52` | Wall clock skews on sleep/NTP; monotonic is required for <3s proof |
| Training a Hindi ASR from scratch | Benchmark `IndicConformer` (NeMo/Transducer) or distill to `Zipformer-CTC` for edge | 2024 (AI4Bharat IndicConformer, sherpa `zipformer-ctc` models) | IndicConformer is higher accuracy but larger; zipformer-ctc int8 (~30–60 MB) is the emerging edge sweet spot if whisper WER is high on Santali-accented Hindi — not yet benchmarked in this repo |

**New tools/patterns to consider:**

- **sherpa-onnx `Vad` streaming API (`empty()/front()/pop()/flush()`):** Already in AAR 1.13.0; enables true streaming VAD without buffering the whole utterance — use for the mic path if 4 s fixed capture proves too coarse. See `VadStream.kt:77-85` and `sherpa-onnx/android/SherpaOnnxSimulateStreamingAsr/.../Vad.kt`.
- **sherpa-onnx `OnlineRecognizer` (streaming ASR):** Could reduce *perceived* latency below 1s for long utterances, but adds endpointing complexity. Keep as P6 optimization, not P3 scope.
- **`Moonshine` / `Paraformer` / `SenseVoice` as multilingual VAD+ASR singletons (sherpa wasm examples):** Interesting for code-switched Hindi+Santali but not Hindi-specific and not budgeted. Stay with whisper-tiny for P3.
- **`jiwer` for WER/CER in `ml/asr/` benchmark harness:** Standard for Hindi WER; add alongside `soundfile` for WAV IO when implementing `REQUIREMENTS.md:ASR-01` benchmark script.

**Deprecated/outdated:**

- **`k2-fsa/sherpa-onnx` older AARs (<1.10) requiring `AssetManager`:** Do not follow old gist examples that pass `context.assets`; current vendored `1.13.0` (`android/ml/libs/sherpa-onnx-1.13.0.aar:18`) expects `null` for filesystem paths.
- **`IndicConformerAsrAdapter` ONNX Runtime Mobile direct session stub (`ml/pipeline/asr_adapter.py:56` `NotImplementedError`):** That Python stub is reference-only; Android path is sherpa-mediated. Do not add `onnxruntime-android` to `:ml` unless the transducer/zipformer branch truly needs it.
- **whisper.cpp standalone for Hindi on Android:** Superseded by sherpa's whisper port for this repo; adds a second native toolchain without the budget/WER justification.
</sota_updates>

<open_questions>
## Open Questions

Things that couldn't be fully resolved:

1. **Does whisper-tiny Hindi actually hit ≤1s on a real 2GB Android 9 arm64 device for a typical 3–4 s Hindi utterance?**

   - What we know: whisper-tiny decoder 86 MB + encoder 13 MB is the heaviest part of the pipeline; sherpa quant offers `int8` variant that script already prefers (`scripts/fetch_android_models.sh:38`) but on-disk 99 MB is float. TTS fixture latency was 286 samples synthetic, not representative. No `ml/asr` WER/latency numbers exist yet (`ml/asr/README.md:3` says benchmark skeleton). `docs/benchmarks/BENCHMARK_REPORT.md` is a PENDING template.
   - What's unclear: Real decode ms on 2GB/RK3326-class SoC, thermal throttling effect, and tailPaddings impact (`IndicConformerAsrAdapter.kt:45` `tailPaddings=1000`).
   - Recommendation: Do not block P3 E2E on the answer. Keep whisper-tiny as baseline, add `LatencyTracker` T0→T1 instrumentation behind PTT and WAV paths, and add a `ml/asr/benchmark_wer_latency.py` stretch that runs on-device `connectedCheck` with a 10-utterance Hindi fixture set (16k mono PCM16 WAVs) — report WER via `jiwer` and `stage_ms.asr` p50/p95; promote to Vosk or IndicConformer INT8 only if p95 >1000ms or WER >15%.

2. **Which VAD abstraction should P3 canonicalize — `:ml VadAnalyzer` (streaming `accept/popSegment`) or `:app VadDetector` (batch `detect(ShortArray) -> List<SpeechSegment>`)?**

   - What we know: Both exist. `:ml VadStream.kt:19` `VadAnalyzer` is streaming (10–100ms chunks, `SherpaOnnxVadAnalyzer` + `MockVadAnalyzer`). `:app VadAdapter.kt:15` `VadDetector` is batch (`SherpaAsrAdapter.kt:42` calls `vad.detect(pcm16, sr)`). They wrap the same `com.k2fsa.sherpa.onnx.Vad` but with different ergonomics. `AudioPipeline.kt:25` takes `VadDetector`; `VadStream.kt:135` `runMicrophoneVad` takes `VadAnalyzer`.
   - What's unclear: Whether P3's final mic path will be batch (record 4s then segment) or streaming (emit segments as they are detected) affects which interface survives.
   - Recommendation: Keep `SherpaAsrAdapter`'s `VadDetector` batch path for P3 (simplest, matches current `recordMic` 4s buffer). Add a `VadAnalyzer→VadDetector` bridge if `runMicrophoneVad` is adopted later; do not carry both as peer types — unify in PLAN-P3 task 1.

3. **Where should the WAV-fed ASR debug surface live — `assets/test_wavs/` bundled fixture vs `READ_EXTERNAL_STORAGE`/file picker vs `adb push` path?**

   - What we know: `AndroidManifest.xml:7` has `READ_EXTERNAL_STORAGE` already; `MainScreen.kt:183` `recordMic` is the only input today. No WAV loader exists. Emulator verification (`REQUIREMENTS.md:ASR-02`) needs at least bundled assets for CI, but manual QA benefits from picking arbitrary WAVs.
   - What's unclear: Whether `READ_EXTERNAL_STORAGE` is needed for P3 or if a `GetContent()` picker (no permission beyond `READ_EXTERNAL_STORAGE` on Android 9) is sufficient. `docs/phases/PLAN-P3-asr-e2e.md:8` describes a debug button but not where it lives.
   - Recommendation: Plan a two-tier surface: (a) `android/app/src/main/assets/test_wavs/hi_hello_16k_mono.wav` + a debug `Load WAV -> ASR` button that calls `assets.open()` (no permission, works headless), (b) optional `ActivityResultContracts.GetContent("audio/wav")` picker that uses `ContentResolver` (works on emulator with `READ_EXTERNAL_STORAGE`). Both must feed the same `engine.asr.transcribe(ShortArray,16000)` path.

4. **Should P3 produce a Hindi WER harness or defer to P6?**

   - What we know: `REQUIREMENTS.md:ASR-01` says "benchmark, 30–80MB" as part of ASR-01; `docs/PHASES.md:78` lists `Vosk` and `whisper.cpp` as benchmark candidates; `ml/asr/README.md:3` says "Benchmark on-device before choosing. This dir holds benchmarking scripts only". P6 is the formal <3s proof. P3's acceptance is "plausible Hindi" (ROADMAP.md Phase 3 success #1), not WER.
   - What's unclear: Whether P3 must ship a full WER loop or just prove E2E decode.
   - Recommendation: P3 ships *existence* proof (plausible Devanagari for a 2–3s Hindi WAV on emulator, no crash, VAD gated, ≤1s logged), plus a `ml/asr/benchmark_wer_latency.py` skeleton that can be run locally. Formal WER/latency report (target WER ≤15% per `BENCHMARK_REPORT.md:38`) stays in P6, but P3 must not claim WER without measuring.

5. **Is the `mund` language code correct for Hindi ASR in P3, or should it already be `sat` (Santali) / `hi` (Hindi)?**

   - What we know: `EngineContracts.kt:54` `supports(language=="hi")` is correct for ASR (Hindi input). But `MockEngines.kt:25` `MockAsrEngine` and `AudioPipeline.kt:51` hardcode `"mund"` for TTS target, which is historically Mundari — the project is now Santali-only (ROADMAP.md:5, PROJECT.md:4). `MainScreen.kt:71,99` still uses `LanguagePair("hi","mund")`. `docs/MODEL_AND_DATA_PROVENANCE.md` mentions `mund` phase labels but `docs/PHASES.md` dispatch map still shows `mund`.
   - What's unclear: Whether P3 should rename ASR/TTS language codes to `sat_Olck`/`sat` now or keep `mund` for backward compat with mocks until P1/P2 do the Santali swap.
   - Recommendation: Keep `supports("hi")` for ASR in P3; keep TTS/MT at `"mund"` in existing adapters (they are mocks/fixtures) and add a TODO referencing Santali-only pivot; do not rename to `sat` inside the P3 ASR plan — that rename belongs to P1 (MT) + P2 (TTS) and needs pack-manifest alignment.

</open_questions>

<sources>
## Sources

### Primary (HIGH confidence)

- `android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:1-87` — real Hindi ASR adapter (OfflineRecognizer, OfflineWhisperModelConfig language="hi", null AssetManager, FeatureConfig 16000/80, numThreads=1)
- `android/ml/src/main/java/com/vachak/ml/VadStream.kt:1-152` — SherpaOnnxVadAnalyzer (SileroVadModelConfig, Vad null manager) + MockVadAnalyzer + runMicrophoneVad (10–100ms chunks)
- `android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt:1-57` — recursive asset extraction to filesDir, null AssetManager fix, Vachak-Assets logging
- `android/ml/src/main/java/com/vachak/ml/AsrAdapter.kt:1-22` — FloatArray transcribe interface, AsrResult(isFixture)
- `android/ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt:1-54` — null AssetManager TTS pattern (proves same pattern for ASR/VAD)
- `android/ml/src/main/java/com/vachak/ml/LatencyTracker.kt:1-53` — nanosecond T0–T4 tracker (ml variant)
- `android/app/src/main/java/com/vachak/engine/EngineContracts.kt:1-159` — ASREngine(ShortArray) surface, LatencyBudget ASR_MS=1000, TOTAL_MS=3000
- `android/app/src/main/java/com/vachak/engine/LatencyTracker.kt:1-37` — millis marks variant + Vachak-Latency logs
- `android/app/src/main/java/com/vachak/engine/EngineProvider.kt:1-72` — single injection point; `real()` -> SherpaAsrAdapter/SherpaTtsAdapter
- `android/app/src/main/java/com/vachak/ml/adapter/SherpaAsrAdapter.kt:1-55` — ASREngine impl: VAD detect -> Short->Float /32768 -> IndicConformerAsrAdapter
- `android/app/src/main/java/com/vachak/ml/adapter/VadAdapter.kt:1-32` — VadDetector + MockVadDetector (whole-buffer)
- `android/app/src/main/java/com/vachak/ml/adapter/AudioPipeline.kt:1-54` — sequential VAD->ASR + TTS->play, AudioPipelineTest wiring
- `android/app/src/main/java/com/vachak/ui/MainScreen.kt:1-201` — LessonTranslatorScreen, recordMic(16000,4000), runVoicePipeline sequential, Push-to-talk Button, DebugPanel, playPcm
- `android/app/src/main/java/com/vachak/ui/MainActivity.kt:1-20` — entry point injection EngineProvider.real(this)
- `android/app/src/main/AndroidManifest.xml:1-21` — OFFLINE-FIRST, no INTERNET, RECORD_AUDIO only
- `android/app/build.gradle.kts:1-45` — minSdk 28, ABI arm64-v8a+x86_64, Compose 1.6.8
- `android/ml/build.gradle.kts:1-22` — vendored sherpa-onnx-1.13.0.aar dependency
- `scripts/fetch_android_models.sh:1-80` — ASR whisper-tiny + VAD silero + TTS vits fetch, asset layout, int8 preference
- `docs/PHASES.md:72-89` — Phase 3 goal, reusable repos table, interfaces (AsrEngine.recognize(pcm:ShortArray):String, Vachak-ASR logs), guard 16k mono PCM16
- `docs/phases/PLAN-P3-asr-e2e.md:1-19` — P3 tap flow, WAV debug path, ASR result + Vachak-ASR logging, ≤1s budget
- `docs/LATENCY_INSTRUMENTATION.md:1-80` — T0–T4 definitions, wall-clock warning, device measurement procedure, fixture filtering
- `docs/benchmarks/BENCHMARK_REPORT.md` — WER/CER/MOS/latency template, isFixture gating, <3s proof format
- `.planning/ROADMAP.md:48-59` — Phase 3 goal, depends, success criteria, single plan 03-01
- `.planning/REQUIREMENTS.md:14-17` — ASR-01 (whisper/IndicConformer/Vosk 30–80MB, Silero, ≤1s), ASR-02 (WAV verifiable)
- `AGENTS.md:1-122` — offline, sequential, 2GB/500MB budgets, tech stack, voice pipeline, external repo map
- `.planning/PROJECT.md:1-55` + `.planning/STATE.md:1-20` — current brownfield state: whisper-tiny+VAD WIRED, mic stable, E2E transcript not confirmed

### Secondary (MEDIUM confidence — WebSearch verified with official source)

- `k2-fsa/sherpa-onnx` GitHub README + `docs` — OfflineRecognizer/Vad/OfflineTts Android API shapes, offline + VAD + TTS support matrix, model releases (asr-models/tts-models/vad-models) — verified against vendored AAR API already imported in repo
- `alphacep/vosk-api` — Apache-2.0, `vosk-model-small-hi-0.22` ~50 MB, offline Hindi — verified as Vosk Hindi candidate per docs/PHASES.md
- `AI4Bharat/IndicConformerASR` + `shivsinghin/Indic-STT` — IndicConformer Hindi transducer/CTC via ONNX Runtime, MIT/Apache-2.0 — verified as IndicConformer reference (size/latency tradeoffs)
- `ggerganov/whisper.cpp` — MIT, tiny/base ggml — verified as whisper.cpp alternative; redundant with sherpa's whisper port for this repo

### Tertiary (LOW confidence — needs validation during planning/execution)

- whisper tiny Hindi accuracy on Santali-accented teacher Hindi and code-switched Hindi+Santali classroom utterances — no in-repo Hindi WER data; needs `ml/asr/` fixture set
- Exact whisper-tiny INT8 decode ms on 2GB Android 9 arm64 (RK3326-class) — no device measurement yet; `LatencyTracker` numbers from mocks are fixtures (`docs/LATENCY_INSTRUMENTATION.md:16`)
- IndicConformer INT8 on-device size after `quantize_dynamic` per-channel — depends on export variant (transducer 3 files vs zipformer-ctc single file); not measured in repo
</sources>

<metadata>
## Metadata

**Research scope:**

- Core technology: Offline Hindi ASR on Android (sherpa-onnx OfflineRecognizer + Silero VAD, AudioRecord 16k mono PCM16)
- Ecosystem: sherpa-onnx 1.13.0 AAR, whisper-tiny Hindi, silero_vad.onnx, AudioRecord, Compose UI, EngineContracts/EngineProvider/SherpaAssets/SherpaAsrAdapter/VadAdapter, LatencyTracker (dual), ml/pipeline parity
- Patterns: null-AssetManager filesystem decode, VAD-gated segmentation, Short->Float PCM normalization, mic capture (10–100ms chunks), WAV-fed debug path for emulator/headless, sequential pipeline ≤1s
- Pitfalls: PCM type mismatch, asset-vs-filesystem path, long-capture >1s, WAV header garbage, dual LatencyTracker divergence, isFixture semantics, budget overshoot, UI showing latency report not transcript, emulator mic absence

**Confidence breakdown:**

- Standard stack: **HIGH** — sherpa-onnx AAR already vendored and wired; whisper-tiny + silero assets present and measured on disk; null-AssetManager + numThreads=1 + FeatureConfig(16000,80) proven in code.
- Architecture: **HIGH** — data flow traced from AudioRecord/MainScreen.kt through SherpaAssets/SherpaAsrAdapter/IndicConformerAsrAdapter to OfflineRecognizer/Vad; both VAD abstractions and both LatencyTrackers located with file:line refs; verified against cloned sherpa-onnx Android examples.
- Pitfalls: **HIGH** — 7 pitfalls derived from live code mismatches (PCM types, isFixture=true hardcoding, tracker divergence, WAV header, 99M budget over, 4s fixed capture, UI transcript bug) with warning signs and prevention strategies; each anchored to file:line.
- Code examples: **HIGH** — all examples are verbatim repo patterns (IndicConformerAsrAdapter, SherpaAsrAdapter, VadStream, SherpaAssets, AudioPipeline) or canonical sherpa-onnx Android snippets; WAV parser is recommended for new code and flagged as such.
- Benchmark comparison (IndicConformer vs whisper.cpp vs Vosk): **MEDIUM** — docs/PHASES.md and ml/asr/README.md acknowledge the three-way comparison, but no in-repo on-device WER/latency data exists; recommendation is grounded in known model sizes and published performance, not 2GB device measurement (requires P3+ follow-up).

**Research date:** 2026-08-29

**Valid until:** 2026-09-28 (30 days — sherpa-onnx ASR/VAD APIs stable; AAR pinned at 1.13.0; re-validate if model checkpoint or Android minSdk changes)

</metadata>

---

*Phase: 03-asr-e2e*

*Research completed: 2026-08-29*

*Ready for planning: yes*

