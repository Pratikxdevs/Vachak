# Phase 08: Live Wiring MVP — Research

**Researched:** 2026-08-29
**Domain:** Offline live Hindi capture → VAD → ASR → MT → TTS wired to overhauled Compose UI (Sequential, 2GB RAM, <3s)
**Confidence:** HIGH — runtime already shipped (P1-P5), UI overhaul broke wiring; fix is plumbing (AudioRecord/permission/VAD/ASR→MT→TTS) not model retraining
**Source:** LiveScreen mock audit + MainScreen P3 reference + VadStream/SherpaAsrAdapter + IndicTrans2Adapter + SherpaOnnxTtsAdapter + LatencyTracker + PackManager

<user_constraints>
## User Constraints (from CONTEXT.md — LOCKED)

- **Goal today: ship MVP that works:** audio capture → live Hindi transcription (not fixture) → live Santali translation (entire AI model) → play audio, all wired to updated UI. Currently "anything is not wired, device not wired up at all" — UI overhaul left LiveScreen as bullshit fixture.
- **UI overhauled:** `VachakApp.kt:18` `AdaptiveScaffold` + `NavDest` Home/Live/Curriculum/Tools/Settings/ManagePacks + Cupertino — Live elements are mocks. Must wire them; do not revert UI.
- **Audio contract:** 16k mono PCM16, 4s max `AudioRecord(MIC,16000,MONO,PCM16)` (`MainScreen.kt:220` `recordMic`, `VadStream.kt:135` `runMicrophoneVad` chunk 10–100ms) — LiveScreen currently `simulatedHindi="नमस्ते, कैसे हैं आप?"` + `delay(180)` (`LiveScreen.kt:52`), zero real capture.
- **Sequential only:** ASR→MT→TTS never parallel (2GB, `numThreads=1`, `ReentrantLock` `IndicTrans2Adapter.kt:25`, `Orchestrator.kt:??`, `AudioPipeline.kt:16` comment) — must enforce.
- **VAD gated:** Silero `VadStream.SherpaOnnxVadAnalyzer` + `VadAdapter.SherpaVadDetector` + `silero_vad.onnx` 632K (`VadStream.kt:43`, `VadAdapter.kt:42`) — LiveScreen bypasses `vad.detect()` entirely.
- **ASR:** `SherpaAsrAdapter` (app `:app/ml/adapter`) → `IndicConformerAsrAdapter` (`:ml`, `OfflineRecognizer(null,config)` whispertiny HI, `FeatureConfig(16000,80)`, `SherpaAssets.prepare` null AssetManager `IndicConformerAsrAdapter.kt:67`, pack-aware `resolveBaseDir` `PackManager.getActivePackFor("asr")`, logs `Vachak-ASR` `Vachak-VAD` `Vachak-Latency` + Short/32768 bridge `SherpaAsrAdapter.kt:60`) — LiveScreen never calls `engine.asr.transcribe(pcm,16000)`.
- **MT:** `IndicTrans2Adapter.kt` (3-graph ONNX int8 357M, ORT Mobile 1.18, `IndicProcessorPort`, greedy decode, Ol Chiki regex `[\u1C50-\u1C7F]`, `Vachak-MT` log, `LatencyTracker`, `ReentrantLock`, `supports hi/sat_Olck + mund alias`) — LiveScreen uses `LanguagePair("hi","mund")` (`LiveScreen.kt:74`) should be `sat_Olck` primary (Santali-only per ROADMAP) and must show Ol Chiki validation tick.
- **TTS:** `SherpaOnnxTtsAdapter.kt` (sherpa VITS 22.05k mono Ol Chiki `model.onnx 41M + tokens.txt 379B + lexicon.txt 1021B`, `dataDir=""` Ol Chiki char tokens `U+1C50–U+1C7F`, pack-aware `resolveBaseDir`, `OfflineTts(null,config)`) — LiveScreen `tryPlayPcm` hardcodes `16000` (`LiveScreen.kt:162`) but `audio.sampleRate` is `22050`; must play at model's rate and check `>0.2*sampleRate` audible.
- **Permission:** `RECORD_AUDIO` via `ContextCompat.checkSelfPermission` + `ActivityResultContracts.RequestPermission` (`MainScreen.kt:104` guard) — LiveScreen has zero permission check (`LiveScreen.kt` has no `Manifest.permission.RECORD_AUDIO`, no launcher).
- **Latency:** `ml/LatencyTracker.kt:45` (`LatencySample` T0 speechBegin T1 ASR T2 translate T3 TTS T4 audio, `elapsedRealtimeNanos`, `isFixture`) vs `core/engine/LatencyTracker.kt` (millis `marks` map) — canonical is `ml/LatencyTracker` (P3 decision). LiveScreen uses `System.currentTimeMillis()` total only (`LiveScreen.kt:67`) + never logs `Vachak-Latency` / `Vachak-ASR`/`Vachak-MT`/`Vachak-TTS`.
- **Pack-aware:** `PackManager.getActivePack`/`getActivePackFor` (`sync/PackManager.kt`) → adapters `resolveBaseDir` fallback to `SherpaAssets.prepare` + `closeSessions`/`reloadFromPack` on pack switch (P5) — LiveScreen must not break this.
- **Offline only, no INTERNET, WiFi OFF** — `AndroidManifest.xml:4` deliberately no `android.permission.INTERNET`, sync is installer not client — must not add network calls.
- **Language Santali-only:** `sat_Olck` primary, `mund` alias retained — UI should display `sat_Olck`.

### Claude's Discretion

- Reuse `AudioPipeline.kt` vs inline `recordMic()` in LiveScreen; expose `onPartial` streaming vs hold-to-talk 4s utterance; choose `Orchestrator.kt` vs inline `runPipeline` coroutine; FAB press-hold vs tap toggle; visualizer binding to PCM amplitude vs boolean; error UI for `RECORD_AUDIO` denied / `INVALID_INPUT` / silence (empty VAD).

### Deferred (OUT OF SCOPE — do NOT plan)

- Retrain MT/TTS (P1/P2 shipped), new curriculum (P4), pack builder (P5), P6 harness/report, P7 evidence, floating overlay/background service — foreground Activity only for MVP.
</user_constraints>

<architectural_responsibility_map>
## Architectural Responsibility Map

Single-APK offline Android (Kotlin/Compose/Room + sherpa-onnx AAR 1.13.0 + ONNX Runtime Mobile 1.18 + PackManager). All inference local on tablet, no network.

| Capability | Primary Tier | Secondary Tier | Rationale / Current Wiring |
|------------|--------------|----------------|----------------------------|
| Mic capture (AudioRecord 16k mono PCM16, 4s max, background thread) | `:app` `LiveScreen.kt` `recordMic()` (port from `MainScreen.kt:220`) / `AudioPipeline.captureAndRecognize` | `android.media.AudioRecord` + `VadStream.runMicrophoneVad` 10–100ms chunks | P3 `MainScreen.recordMic` stable (minBuf + `RECORDSTATE_RECORDING` guard); LiveScreen currently synthesizes silent buffer `AudioPipeline.kt:34` `ShortArray(frameCount)` fixture — replace with real read loop |
| Permission gate (RECORD_AUDIO) | `:app` UI `LiveScreen.kt` (`rememberLauncherForActivityResult(RequestPermission)`) + `ContextCompat.checkSelfPermission` | `AndroidManifest.xml` `RECORD_AUDIO` declared | `MainScreen.kt:104` pattern (`permissionLauncher.launch`) is canonical — port to LiveScreen; do NOT block on permission request in coroutine |
| VAD gating (Silero 632K) | `:ml` `VadStream.SherpaOnnxVadAnalyzer` (streaming `accept/pop`) + `:app` `VadAdapter.SherpaVadDetector` (whole-buffer `detect` unifies analyzer) via `SherpaAssets.prepare vad/silero_vad.onnx` `Vad(null,cfg)` + `VadModelConfig SileroVadModelConfig` | sherpa-onnx AAR `Vad` | Saves 30–70% decode on 2GB; `SherpaVadDetector.detect` chunks via `/32768.0f` → `analyzer.accept` 100ms — LiveScreen currently never calls it |
| ASR decode (Hindi whispertiny) | `:ml` `IndicConformerAsrAdapter` `OfflineRecognizer(null,config)` `FeatureConfig(16000,80)` `OfflineModelConfig whisper(encoder,decoder,language="hi")` | `:app` `SherpaAsrAdapter.transcribe(ShortArray)` VAD→segment→Short/32768→Float→IndicConformer | LiveScreen must call `engine.asr.transcribe(pcm,16000)` (currently calls `engine.translation.translate` with fixture string); engine returned via `EngineProvider.real(context).asr = SherpaAsrAdapter(context, vad=SherpaVadDetector)` |
| MT (Hin→Santali Ol Chiki) | `:ml` `IndicTrans2Adapter` (3-graph ONNX int8 357M, ORT Mobile, `IndicProcessorPort` pre/post, greedy decode loop, `OlChikiRegex` `U+1C50–U+1C7F`, `Vachak-MT` log, `ReentrantLock`, pack-aware `PackManager.getActivePackFor("mt")`) | `:core` `TranslationEngine` `translate(text, LanguagePair("hi","sat_Olck"))` | LiveScreen currently `LanguagePair("hi","mund")` + `delay(180)` mock — switch to `sat_Olck` + real `translate`, show `sat_Olck` badge + validation tick |
| TTS (Santali VITS) | `:ml` `SherpaOnnxTtsAdapter` `OfflineTts(null,config)` `OfflineTtsVitsModelConfig(model,lexicon,tokens,dataDir="")` + `SherpaTtsAdapter` sat family `supports` | `AudioTrack(MUSIC, sampleRate, MONO, PCM16, MODE_STATIC)` playback | LiveScreen `tryPlayPcm` hardcodes `16000` but TTS `SynthAudio.sampleRate` is `22050` — play at `pcm.sampleRate`, check `pcm.size > 0.2*22050`, log `Vachak-TTS` |
| Latency (ASR≤1s MT≤0.5 TTS≤1 <3s total) | `ml/LatencyTracker` `LatencySample` T0 speechBegin T1 ASR T2 translate T3 TTS T4 audio `elapsedRealtimeNanos` + `EngineProvider` packs | `core/engine/LatencyTracker` millis variant (deprecated for ASR, keep for budget `BudgetIndicator`) | LiveScreen must mark T0 on record start, T1 after ASR, T2 after MT, T3 before TTS, T4 after audio start; log `Vachak-Latency` + `Vachak-ASR/MT/TTS`, warn >budget, show per-stage badge as in `SettingsScreen` `Benchmark` section |
| Pack awareness | `:sync` `PackManager` `getActivePack` / `getActivePackFor(subdir)` → `filesDir/packs/<id>/vachak_models/<sub>` | `SherpaAssets.prepare` fallback | All adapters already pack-aware + `reloadFromPack` clears sessions; LiveScreen must not bypass via hardcoded asset path |
| UI binding (Compose) | `:app` `VachakApp.kt:18` `AdaptiveScaffold` + `NavDest` + `LiveScreen` `Scaffold` FAB `LargeFloatingActionButton` (orange→red `PalashOrange`→`ErrorRed`) + `BreathVisualizer` + `DualLangCard` `weight(1f)` | `HomeScreen` `FocusCard → Live` + `ToolsScreen` debug | Overhaul is `Cupertino` `VachakTheme` — keep `VachakCupertinoLight/Dark` + `CupertinoScaffold`; do not revert to old `MainScreen` |

Sequential pipeline non-negotiable: one model resident, `numThreads=1`, `Dispatchers.IO` single `withContext`, `ReentrantLock` held for MT, `Orchestrator` sequential state machine if used.
</architectural_responsibility_map>

<research_summary>
## Summary

Phase 8 ships the **working** live surface that was mocked. Good news: all runtimes are already wired and verified on device: `IndicConformerAsrAdapter.kt:73` `OfflineRecognizer(null,config)` whispertiny 99M, `VadStream.kt:64` `Vad(null,cfg)` Silero 632K, `IndicTrans2Adapter.kt:170` ORT 3-graph MT 357M int8 (`sat_Olck`), `SherpaOnnxTtsAdapter.kt:43` `OfflineTts(null,config)` VITS 40M 22.05k Ol Chiki, `PackManager` pack-aware (P5), `AndroidManifest` offline (`RECORD_AUDIO` only), `MainScreen.kt:220` `recordMic` proven. Bad news: overhaul (`VachakApp.kt` `AdaptiveScaffold` 6 dests, `LiveScreen.kt` mocked `runPipeline(simulatedHindi)+delay(180)+mund+16000` playback, no `AudioRecord`, no `RECORD_AUDIO` launcher, no VAD, no `Latencies`, `HomeScreen FocusCard→Live` not wired to real lesson, `ToolsScreen` not surfacing pack-aware translate) — user can't speak and see live transcription, entire AI model sits uncalled. The fix is **plumbing, not training**: port `MainScreen.recordMic` + `loadWavPcm16Mono16k` pattern (`ShortArray` PCM16 + RIFF `data`-chunk walk) to `LiveScreen`, add `rememberLauncherForActivityResult(RequestPermission)` + `hasRecordPermission` guard, gate `engine.asr.transcribe(pcm,16000)` via `SherpaVadDetector.detect` (whole-buffer) or `runMicrophoneVad` (streaming 10–100ms), surface Hindi in `DualLangCard(label="Hindi (ASR)")` + `asr: text (ms)` status, then `engine.translation.translate(hintText, LanguagePair("hi","sat_Olck"))` with Ol Chiki regex check + second `DualLangCard`, then `engine.tts.synthesize(sat, "sat_Olck").sampleRate` at model's rate via `AudioTrack`, mark `ml/LatencyTracker` T0→T4 (`elapsedRealtimeNanos`) and log `Vachak-ASR`/`Vachak-MT`/`Vachak-TTS`/`Vachak-Latency`/`Vachak-VAD` + warn >1000/500/1000/total3000. Keep `PackManager.getActivePackFor` fallback, keep `EngineProvider.real(this)` injection in `MainActivity.kt:17`, keep `numThreads=1` + `ReentrantLock` sequential, keep `INTERNET` absent. Budget 497M source Mt357+Tts41+Asr99+Vad0.6 already measured; <3s must be measured on target 2GB via `benchmarks/translation_benchmark.py` + `adb logcat -s Vachak-*` (P6), not claimed from dev host proxy `3604ms` (`BENCHMARK_REPORT.md:75` PENDING).

**Primary recommendation:** Keep `OfflineRecognizer` whispertiny + Silero VAD + `IndicTrans2Adapter` int8 + VITS Ol Chiki as MVP shippable baseline (no model swap in P8); fix LiveScreen wiring (real `AudioRecord` `ShortArray` + `VadDetector` + `Short/32768.0f` bridge + `transcribe` → UI, `sat_Olck` translate → UI, `synthesize` at 22050 → `AudioTrack` + audible `>0.2*sampleRate` check), add permission launcher + `isListening` vs `isTranslating` FAB orange→red + `BreathVisualizer` pulse tied to `isListening`, unify `ml/LatencyTracker` for per-stage, expose partial `onPartial` Hindi update if feasible via chunked streaming otherwise uttered 4s hold-to-talk, gate empty VAD → silence hint, surface `EngineError` messages, and wire `HomeScreen` continue → `Live` with pending lesson. Do not add streaming `OnlineRecognizer` complexity, floating overlay/background service, or waveform→visualizer amplitude binding — MVP is hold-to-talk utterance decode.
</research_summary>

<standard_stack>
## Standard Stack

### Core (already vendored, reuse)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `k2-fsa/sherpa-onnx` vendored AAR | 1.13.0 `android/ml/libs/sherpa-onnx-1.13.0.aar` | Hindi ASR + VAD + TTS runtime on Android | AGENTS.md mandated, `MODEL_AND_DATA_PROVENANCE.md:20`, Apache-2.0; `OfflineRecognizer(null,config)` pattern `IndicConformerAsrAdapter.kt:73` + `Vad(null,cfg)` `VadStream.kt:64` proven null AssetManager fix; single AAR for ASR/TTS/VAD, single `libonnxruntime.so` via `pickFirsts` |
| `whisper-tiny` via sherpa (`sherpa-onnx-whisper-tiny` multilingual) | `scripts/fetch_android_models.sh:35` encoder 13M + decoder 86M + tokens 800K | Hindi ASR baseline for live | Already wired `OfflineWhisperModelConfig(language="hi")`; 99M over 30–80 budget only if int8 not yet — P8 keeps as shippable baseline |
| `silero_vad.onnx` via sherpa | `fetch_android_models.sh:26` 632K `vachak_models/vad/silero_vad.onnx` | VAD gating | `VadStream.kt:58` `SileroVadModelConfig(model="$baseDir/silero_vad.onnx")` `VadModelConfig(sampleRate=16000,numThreads=1)` |
| `microsoft/onnxruntime:onnxruntime-android` | 1.18.0 MIT (also bundles `libonnxruntime.so` via AAR) | MT inference ORT Mobile | `IndicTrans2Adapter.kt:174` `OrtEnvironment.getEnvironment()` `SessionOptions numThreads=1` + 3 `OrtSession` encoder/decoder/decoderPast + `OnnxTensor` |
| `androidx.core:core-ktx` + `activity-compose` + `Compose 1.6.8 material3:m3 1.2.1 nav:2.7.7` | already in `app/build.gradle.kts` | UI + permission | `MainActivity.kt:17` `EngineProvider.real(this)` → `VachakApp`; `rememberLauncherForActivityResult(RequestPermission)` |
| `AudioRecord` + `AudioTrack` SDK 28+ | Android 9 minSdk 28 | Mic capture / playback | `MainScreen.kt:183` `AudioRecord(MIC,16000,MONO,PCM16)` `max 4000ms` + `AudioTrack(MUSIC, sampleRate, MONO, PCM16, MODE_STATIC)`; `tryPlayPcm` must use `audio.sampleRate` not hardcoded 16000 |
| `androidx.room 2.6.1` + `PackManager` | already `:content` + `:sync` | Pack-aware fallback | `PackManager.getActivePackFor(context,"mt"/"tts"/"asr")` → `filesDir/packs/<id>/vachak_models/<sub>` else `SherpaAssets.prepare` |

### Supporting (already in repo, reuse)
| Library | Purpose | When to Use |
|---------|---------|-------------|
| `VadStream.runMicrophoneVad(record,analyzer,chunkMs=100, onSegment)` + `SherpaVadDetector.detect(pcm,16000)` | Whole-buffer vs streaming VAD unification (`VadAdapter.kt:42` `SherpaVadDetector` wraps `SherpaOnnxVadAnalyzer` 100ms chunks via `/32768.0f`) | Live hold-to-talk can use `detect(ShortArray)` whole-buffer (simpler) or streaming `runMicrophoneVad` for incremental partials; unify via `SherpaVadDetector` |
| `ml/LatencyTracker` `LatencySample` `T0–T4` `elapsedRealtimeNanos` + `core/engine/LatencyTracker` millis `marks` (deprecated) | Latency gate <3s | Use `ml/LatencyTracker` for ASR≤1s MT≤0.5 TTS≤1 total<3000, log `Vachak-Latency`; `SettingsScreen` `BudgetIndicator` already shows total |
| `IndicProcessorPort` `preprocessBatch/postprocessBatch` + Ol Chiki regex `[\u1C50-\u1C7F]` | MT pre/post + validator | `IndicTrans2Adapter.kt:98` already validates `hasOlChiki` + warns — LiveScreen should show badge tick |
| `scripts/fetch_test_wav.py` + `hindi_sample.wav` 16k mono PCM16 24k samples `assets/test/hindi_sample.wav` | Emulator/headless ASR verification via `loadWavPcm16Mono16k` RIFF `data`-chunk walk | Keep for CI; LiveScreen mic path reuses same `engine.asr.transcribe` entry |
| `ml/orchestrator/Orchestrator.kt` sequential state machine | Orchestrate ASR→MT→TTS with single OffloadHandle | Optional centralizer vs inline `LiveScreen runPipeline`; either must keep `ReentrantLock` + sequential |
| `cupertino:0.1.0-alpha04` + `VachakTheme` `VachakColors.Forest #285943` | Overhaul styling | Keep `VachakTheme { CupertinoTheme }` + `CupertinoScaffold` haze; do not revert |

### Alternatives Considered (do NOT use for P8 MVP)
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `OfflineRecognizer` whole-utterance hold-to-talk (4s max) | `OnlineRecognizer` streaming (partial Hindi) | Streaming needs endpointing (`isEndpoint`/`isReady`/`reset`) `sherpa SimulatorStreamingAsr` complexity + tail paddings for paraformer; adds perceived latency tie-in but adds complexity; P8 MVP is offline utterance decode already ≤1s on short utterance — stream is P6 stretch |
| Inline `LiveScreen.recordMic` loop | `AudioPipeline.captureAndRecognize` (`AudioPipeline.kt:34` fixture silent buffer) | `AudioPipeline` currently fixture `ShortArray(frameCount)` not real `AudioRecord.read` — either flesh it out or keep inline `MainScreen.recordMic` port; do not duplicate capture loop |
| Wall-clock `System.currentTimeMillis` total only (`LiveScreen.kt:67`) | `ml/LatencyTracker` `elapsedRealtimeNanos` per-stage | Wall-clock skews on sleep/NTP; `Vachak-Latency` + `markSpeechBegin/markAsr/markTranslate/markTtsBegin/markAudioBegin` is canonical |
| Hardcoded `AudioTrack 16000` (`LiveScreen:162`) | `SynthAudio.sampleRate` 22050 from `SherpaOnnxTtsAdapter` | 16000 playback of 22050 PCM changes pitch/speed + audible check `>0.2*16000` is wrong threshold (4410 vs 4800) |
| `LanguagePair("hi","mund")` (`LiveScreen.kt:74`) | `LanguagePair("hi","sat_Olck")` | `mund` is alias kept for backward compat but Santali-only roadmap requires `sat_Olck` Ol Chiki primary; validator expects `sat_Olck` |
| Custom AudioRecord on UI thread | `runMicrophoneVad` background thread `VadStream.kt:135` `require(chunkMs in 10..100)` `READ_BLOCKING` | UI-thread capture janks Compose + misses `RECORDSTATE_RECORDING` guard |
| Resampling 8k/44k→16k ad-hoc linear | Reject non-16k with `EngineError.INVALID_INPUT` | P8 scope is 16k native capture; resampling is later stretch (libsamplerate/Sonic) — reject + hint |

**Installation:**
```bash
# Models already fetched:
bash scripts/fetch_android_models.sh
# -> assets/vachak_models/{asr/{encoder,decoder,tokens},vad/silero_vad.onnx,tts/model.onnx,mt/*}
# Build already:
# :ml implementation(files("libs/sherpa-onnx-1.13.0.aar")) + onnxruntime-android:1.18.0 (pickFirsts)
# :app implementation(project(":ml")) + project(":sync") + project(":content")
# Verify wiring without mic:
python scripts/fetch_test_wav.py --check # -> 16000 mono 16bit 24000 samples
```
</standard_stack>

<architecture_patterns>
## Architecture Patterns

### Live Data Flow (MVP hold-to-talk, offline, sequential — no network)

```
[LiveScreen FAB tapStart → hasRecordPermission?Request else→]  [Hindi wav asset debug]
              |                                        |                  |
              v                                        v                  v
   AudioRecord(MIC,16000,MONO,PCM16) 4s max    Permission launcher  loadWavPcm16Mono16k
              |                                        |                  |
              +--------------------+---------------------+------------------+
                                   |
                                   v
                 SherpaAssets.prepare("asr"/"vad") → filesDir/vachak_models (or PackManager activePack/vachak_models/<sub>) null AssetManager
                                   |
                                   v
                         VadDetector.SherpaVadDetector
                           whole-buffer detect(ShortArray) 100ms chunks via SherpaOnnxVadAnalyzer
                           632K silero_vad.onnx, numThreads=1, Vachak-VAD log
                                   |
                                   v
                         segments empty? —yes→ return "" silence hint (do not decode, save RAM/<1s)
                                   |
                                   no
                                   v
                         For each segment: slice PCM16 → FloatArray short/32768.0f (unifies :app ShortArray ↔ :ml FloatArray)
                                   |
                                   v
                         IndicConformerAsrAdapter OfflineRecognizer(null,config) FeatureConfig(16000,80) OfflineModelConfig whisper(encoder,decoder,tokens,language="hi")
                           createStream→acceptWaveform(Float,16000)→decode→getResult.text→release (MUST release, else OOM 2GB) log Vachak-ASR + latency isFixture=false
                                   |
                                   v
                         LatencyTracker T0 speechBegin → T1 ASR (≤1000ms warn Vachak-Latency) → UI DualLangCard Hindi (ASR) + asr: text (ms) badge
                                   |
                                   v
                         IndicTrans2Adapter (IndicProcessorPort preprocess hin_Deva→sat_Olck) → ORT 3-graph encode→greedy decode→batchDecode→postprocess → Ol Chiki regex U+1C50–U+1C7F validator log Vachak-MT → sat_Olck UI card
                                   |  ReentrantLock sequential, numThreads=1, pack-aware PackManager.getActivePackFor("mt"), T1→T2 MT ≤500ms
                                   v
                         SherpaOnnxTtsAdapter/SherpaTtsAdapter pack-aware resolveBaseDir → OfflineTts(null,config) Vits lexicon/tokens/dataDir="" → SynthAudio samples @22050 log Vachak-TTS audible >0.2*22050
                                   |
                                   v
                         LatencyTracker T2→T3 TTS≤1000 + T4 audioBegin → AudioTrack(MUSIC, sampleRate, MONO, PCM16, MODE_STATIC) write→play→sleep(pcm*1000/sampleRate) → stop/release (sequential only) → total <3000 log Vachak-Latency
                                   |
                                   v
                         UI Diagnostics + PackManager + Offline badge + No INTERNET perm check (OfflineHarnessTest) + бюдж. benchmark Pending for P6 (dev 3604ms ≠ Android)
```

### Recommended Project Structure (live slice)

```
android/
├── app/src/main/java/com/vachak/ui/screens/LiveScreen.kt  # FAB capture→VAD→ASR→MT→TTS wire (this phase)
├── app/src/main/java/com/vachak/ui/VachakApp.kt           # AdaptiveScaffold real routing
├── app/src/main/java/com/vachak/ml/adapter/AudioPipeline.kt # optional centralize captureAndRecognize (or keep inline recordMic)
├── ml/src/main/java/com/vachak/ml/VadStream.kt            # SherpaOnnxVadAnalyzer + runMicrophoneVad 10–100ms
├── app/src/main/java/com/vachak/ml/adapter/VadAdapter.kt  # SherpaVadDetector (whole-buffer unify)
├── ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt # OfflineRecognizer whisper HI pack-aware
├── app/src/main/java/com/vachak/ml/adapter/SherpaAsrAdapter.kt # ASREngine VAD→Short/32768→decode
├── ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt # TranslationEngine sat_Olck (pack-aware, lock)
├── ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt  # TTS VITS Ol Chiki 22050 pack-aware
└── sync/src/main/java/com/vachak/sync/PackManager.kt       # packs/<id> fallback
```

### Pattern 1: AudioRecord hold-to-talk (from P3 MainScreen, port to LiveScreen)

**What:** Single `AudioRecord.startRecording` → `while(RECORDSTATE_RECORDING && off<n)` `read(ShortArray)` → `stop/release` → `ShortArray` 16k mono PCM16 4s max, permission-guarded, on `Dispatchers.IO`.

**When:** Every live FAB tap-stop in MVP (no streaming).

**Example:**
```kotlin
// Source: android/app/src/main/java/com/vachak/ui/MainScreen.kt:220
private fun recordMic(sampleRate:Int=16000,maxMs:Int=4000): ShortArray {
  val minBuf = AudioRecord.getMinBufferSize(sampleRate,CHANNEL_IN_MONO,PCM_16BIT)
  val rec = AudioRecord(MIC,sampleRate,CHANNEL_IN_MONO,PCM_16BIT,maxOf(minBuf,sampleRate*2))
  val n=(sampleRate*maxMs)/1000; val buf=ShortArray(n); rec.startRecording(); var off=0
  while(off<n && rec.recordingState==RECORDSTATE_RECORDING){ val r=rec.read(buf,off,minOf(n-off,minBuf/2)); if(r<=0)break; off+=r }
  rec.stop(); rec.release(); return if(off<n) buf.copyOf(off) else buf
}
```

### Pattern 2: Permission gate before AudioRecord (ASVS L1)

**What:** `if(!hasRecordPermission(context)) { status="mic permission needed"; permissionLauncher.launch(RECORD_AUDIO); return }` before any `AudioRecord` ctor.

**Example:**
```kotlin
// Source: android/app/src/main/java/com/vachak/ui/MainScreen.kt:104
private fun hasRecordPermission(ctx:Context)=ContextCompat.checkSelfPermission(ctx,Manifest.permission.RECORD_AUDIO)==PERMISSION_GRANTED
val permissionLauncher=rememberLauncherForActivityResult(RequestPermission()){ granted-> permissionState=granted }
if(!hasRecordPermission(context)){ permissionLauncher.launch(RECORD_AUDIO); return@Button }
```

### Pattern 3: VAD-gated whole-buffer decode (saves RAM, ≤1s)

**What:** `vad.detect(pcm,16000)` → `segments empty → "" silence` else `FloatArray slice /32768.0f` per segment → `IndicConformer.transcribe(float,16000).text` join.

**Example:**
```kotlin
// Source: android/app/src/main/java/com/vachak/ml/adapter/SherpaAsrAdapter.kt:43
val segments=vad.detect(pcm,16000) // SherpaVadDetector wraps SherpaOnnxVadAnalyzer 100ms chunks
Log.d("Vachak-VAD","$segments")
if(segments.isEmpty()) return Ok("")
val texts=segments.mapNotNull{ seg-> val s=(seg.startMs*sr/1000).coerceAtLeast(0); val e=(seg.endMs*sr/1000).coerceAtMost(pcm.size); if(e<=s) null else { val floats=FloatArray(e-s){ pcm[s+it]/32768.0f }; engine.transcribe(floats,sr).text } }.filter{it.isNotBlank()}
return Ok(texts.joinToString(" ").trim())
```

### Pattern 4: MT sat_Olck via IndicTrans2Adapter (pack-aware sequential)

**What:** `IndicProcessorPort.preprocessBatch(listOf(hindi),"hin_Deva","sat_Olck")[0]` → encode+pad → ORT 3 `OrtSession` → greedy 128 max → clamp `<tgtDictSize` → `batchDecode` → `postprocessBatch` → Ol Chiki regex `[\u1C50-\u1C7F]` tick.

**Example:**
```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt:74
val preprocessed=IndicProcessorPort.preprocessBatch(listOf(hindi),"hin_Deva","sat_Olck")[0]
val mt=engine.translation.translate(hindiText, LanguagePair("hi","sat_Olck")) // not mund
```

### Pattern 5: TTS at model's sampleRate (not hardcoded 16000)

**What:** `val audio=engine.tts.synthesize(sat,"sat_Olck")` → `AudioTrack(MUSIC, audio.sampleRate, MONO, PCM16, MODE_STATIC)` `write→play`; check `audio.samples.size > 0.2*audio.sampleRate`.

**Example:**
```kotlin
// Source: android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt:162 fix
val pcm=engine.tts.synthesize(sat,"sat_Olck") // not mund
if(pcm is Ok){ val track=AudioTrack(MUSIC, pcm.value.sampleRate, CHANNEL_OUT_MONO, PCM_16BIT, pcm.value.size*2, MODE_STATIC); track.write(pcm.value,0,pcm.value.size); track.play() }
```

### Pattern 6: Latency T0→T4 monotonic (Vachak-Latency)

**What:** `val tracker=LatencyTracker(LatencySample("live-${elapsedNanos}"))` `markSpeechBegin()` at record start → `markAsr(hindi)` after ASR → `markTranslate(sat)` after MT → `markTtsBegin()` before TTS → `markAudioBegin()` after AudioTrack.play → `Log.d("Vachak-Latency", tracker.result().endToEndMs())` + stageMs warn >1000/500/1000/total3000.

**Example:**
```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/LatencyTracker.kt:45
val tracker=LatencyTracker(LatencySample("live-${SystemClock.elapsedRealtimeNanos()}"))
tracker.markSpeechBegin(); // before recordMic
tracker.markAsr(hindi); // after ASR
tracker.markTranslate(sat); // after MT
tracker.markTtsBegin(); // before TTS
tracker.markAudioBegin(); // after play
```

### Anti-Patterns to Avoid

- **Mock `runPipeline(simulatedHindi)+delay(180)` — live must call real `AudioRecord` + `engine.asr.transcribe` + real `translate` at `sat_Olck` + real `synthesize` at `22050`; delay + mund + 16000 are fixture leaks.
- **Two decode codepaths (mic vs wav) duplication** — wav `loadWavPcm16Mono16k` must feed same `ASREngine.transcribe(ShortArray)` as mic, not duplicate `createStream/acceptWaveform/decode`.
- **Forgetting `stream.release()`** — each `createStream()` is native; leak → OOM 2GB after few utterances (`IndicConformerAsrAdapter.kt:83` correct).
- **Parallel ASR+MT+TTS** — holding two native handles >2GB; `numThreads=1` + `ReentrantLock` + sequential coroutine.
- **Capturing on UI thread** — janks Compose; use `Dispatchers.IO` + `READ_BLOCKING`.
- **Wall-clock `currentTimeMillis` for latency** — skew on sleep/NTP; use `elapsedRealtimeNanos`.
- **Adding `INTERNET` for WAV picker / MT** — picker uses `ContentResolver`/`AssetManager`, MT uses ORT file paths; adding `INTERNET` breaks offline audit `OfflineHarnessTest.kt:27`.
- **Hardcoding `isFixture=true`** after real decode — `IndicConformerAsrAdapter` must return `false` for real whispertiny.
</architecture_patterns>

<dont_hand_roll>
## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Speech detection / endpointing | Custom energy threshold zero-cross (`MockVadAnalyzer` `VadStream.kt:89` is wiring-only) | `sherpa-onnx` Silero `SherpaVadDetector` (`VadAdapter.kt:42`) + `silero_vad.onnx` 632K | Energy fooled by fan/clicks; Silero handles Hindi phonetics + streaming `Vad(empty/pop/flush)` already in AAR |
| WAV header parse | `bytes.sliceArray(44 until ...)` assume 44 | RIFF walk find `"data"` skip `JUNK/fact/LIST` (`MainScreen.kt loadWavPcm16Mono16k`) | Many WAVs have non-44 headers; hard slice truncates/leaks header into PCM garbage |
| PCM16 ↔ Float32 | Manual `byte->short->float` without clamp | `/32768.0f` + `coerceIn(-1f,1f)` little-endian | Endianness/sign/clip easy wrong; sherpa expects `[-1,1]` float from Short PCM16 |
| AudioRecord lifecycle | Raw read on UI thread | `recordMic` IO thread + `RECORDSTATE_RECORDING` guard (`MainScreen.kt:220`) or `AudioPipeline.captureAndRecognize` fleshed | Janks + misses permission edge |
| Resampling 8k/44→16k | Ad-hoc lerp | Reject non-16k `INVALID_INPUT` (P8 scope: native 16k only, later Sonic/libsamplerate) | On-device resample adds latency/CPU 2GB; budget only native |
| Confidence / N-best | Invent heuristic over text | `AsrResult.confidence=1.0f` for whisper + raw text; WER is gate (`BENCHMARK_REPORT.md`) | Whisper no calibrated confidences; hand-rolled misleading |
| Latency measure | `currentTimeMillis` deltas in UI | Canonical `ml/LatencyTracker` `elapsedRealtimeNanos` + `Vachak-Latency` tags (`MainScreen.kt:104` pattern) | Wall-clock skew, double tracker double-count; `LATENCY_INSTRUMENTATION.md:6` T0–T4 precise |
| TTS espeak/ng-data shim | Bundle `espeak-ng-data` for Ol Chiki | Ol Chiki char tokens `dataDir=""` (`SherpaOnnxTtsAdapter.kt:83`) | espeak has no `sat`; char tokens avoid 10–15M GPL bloat |
</dont_hand_roll>

<common_pitfalls>
## Common Pitfalls

### Pitfall 1: LiveScreen mock leak (fixture not replaced)
**What:** Live still shows `नमस्ते, कैसे हैं आप?` fixture + `delay(180)` fake MT + `mund` + `16000` playback; user taps FAB and sees same Hindi every time, no mic read, no `Vachak-ASR` log.
**Why:** `LiveScreen.kt:52` `runPipeline(simulatedHindi)` `EngineResult.Ok` `mund` is fixture stand-in; `toggleListening` never calls `AudioRecord`/`transcribe` (`LiveScreen.kt:82`).
**Fix:** Port `MainScreen.recordMic` pattern + `hasRecordPermission` launcher + `SherpaAsrAdapter.transcribe(pcm,16000)` VAD-gated + `sat_Olck` translate + 22050 playback + per-stage `LatencyTracker`; delete `delay(180)` `simulatedHindi` default.
**Warning:** Logcat `Vachak-ASR: transcribed 64000 samples -> "नमस्ते"` never appears while UI shows fixture.

### Pitfall 2: PCM type mismatch (Short vs Float) hallucination
**What:** ASR returns empty/hallucinated Latin, crash native misread.
**Why:** `:ml AsrAdapter(FloatArray)` vs `:app ASREngine(ShortArray)`; bypassing `SherpaAsrAdapter` bridge `/32768.0f` misinterprets PCM.
**Fix:** Always go via `SherpaAsrAdapter.transcribe(ShortArray)` — it does `FloatArray(e-s){ pcm[s+it]/32768.0f }`; WAV `ShortArray` reuses same entry.
**Warning:** `transcribed 16000 samples -> ""` with audible speech.

### Pitfall 3: Asset vs filesystem path (tokens not found)
**What:** `OfflineRecognizer cannot open tokens.txt: No such file`.
**Why:** Passing `context.assets` + asset-relative `vachak_models/asr/tokens.txt` vs `null AssetManager` + `filesDir` `SherpaAssets.prepare`.
**Fix:** `val baseDir=SherpaAssets.prepare(context,"asr")` then `OfflineRecognizer(null,config)` with `"$baseDir/tokens.txt"` — same for mt/tts/vad; log `Vachak-Assets copied asset`.
**Warning:** `asset copy failed` logcat, `OfflineRecognizer ready` never.

### Pitfall 4: Long hold 4s decode OOM (>1s)
**What:** Hold 4s `recordMic(16000,4000)` 64k samples single decode → >1s RSS spike fails <3s on P6.
**Why:** `MainScreen.kt:96` captures fixed 4s; if wired via `SherpaAsrAdapter` VAD segments but LiveScreen bypasses, long silence decode wastes 70% budget.
**Fix:** Keep VAD segmentation in hot path (`SherpaVadDetector`), mark `T0` at first VAD speech frame not at `recordMic` start; reuse same segmentation for wav.
**Warning:** `asr >1000ms` only on long recordings, short 1s `<500ms`.

### Pitfall 5: WAV header fed into PCM garbage
**What:** Mic works but wav decode garbage random Latin.
**Why:** Feeding 44+ byte RIFF header as PCM, or 44k stereo read little-big endian, or `JUNK` chunk.
**Fix:** RIFF walk find `"data"` chunk, validate `16000 mono PCM16`, `ShortArray(data.size/2){ (data[i*2]&0xFF)|((data[i*2+1]&0xFF)<<8) }` little-endian, reject non-conforming `INVALID_INPUT` ("WAV must be 16k mono PCM16").
**Warning:** WAV `ASR result: ""` while mic works.

### Pitfall 6: Dual LatencyTracker divergence (600 vs 1200 for same utterance)
**What:** One tracker 600ms, other 1200ms, P6 can't decide budget.
**Why:** `ml/LatencyTracker` nanosecond T0–T4 vs `core/LatencyTracker` millis `marks` map — LiveScreen currently uses millis `System.currentTimeMillis`.
**Fix:** Use single canonical `ml/LatencyTracker` `elapsedRealtimeNanos` + `markSpeechBegin/markAsr/markTranslate/markTtsBegin/markAudioBegin` `Vachak-Latency` + `Vachak-ASR`.
**Warning:** `report(): total=620` sum != total, `withinBudget` flips.

### Pitfall 7: Showing latency report not Hindi text (acceptance fail)
**What:** Live shows `pcm 64000 samples (see logcat)` not `आज हम…`.
**Why:** `MainScreen.kt:99` mapped `runVoicePipeline` `ShortArray` PCM not intermediate ASR text; LiveScreen `latencyMs` only.
**Fix:** Push-to-talk must assign `hindiText = (asr as Ok).value` directly; `santaliText = translate(hindi)`; `ttsPcm` separate state.

### Pitfall 8: INTERNET permission added for MT pick
**What:** `AndroidManifest` gains `INTERNET` for "model download", offline audit `OfflineHarnessTest` fails.
**Why:** MT/TTS already bundled + pack `filesDir/packs` via `PackManager`; no network needed.
**Fix:** Keep `INTERNET` absent, engines reload from active pack path, `grep -r INTERNET --include="*.xml"` must be 0.

### Pitfall 9: Budget overshoot hold (99M whisper + 357M MT + 41M TTS + 632K VAD =498M >500 with pdfs)
**What:** APK+pack >500M P6 fails even if <3s.
**Why:** Unquant 99M + 357M dominate; script already prefers `encoder.int8.onnx` but fallback float; `AGENTS.md` budget 30–80 ASR +100–180 MT +20–80 TTS tight.
**Fix:** P8 keep whispertiny int8 variant if fetched (script `*encoder.int8.onnx`), document size in `THIRD_PARTY_NOTICES.md` + `MODEL_AND_DATA_PROVENANCE.md`; require INT8 before P6 green.
</common_pitfalls>

<code_examples>
## Code Examples

Verified patterns (from repo + P3 MainScreen):

### Hindi ASR sherpa OffRec whisper HI (pack-aware)
```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt:35,73
val baseDir=SherpaAssets.prepare(context,"asr").let{ pack->
  PackManager.getActivePackFor(context,"asr")?.takeIf{ File("$it/tokens.txt").exists() } ?: PackManager.getActivePack(context)?.let{ File(it,"vachak_models/asr") }?.takeIf{ it.exists() }?.absolutePath ?: SherpaAssets.prepare(context,"asr")
}
val modelConfig=OfflineModelConfig(numThreads=1,tokens="$baseDir/tokens.txt", whisper=OfflineWhisperModelConfig(encoder="$baseDir/encoder.onnx",decoder="$baseDir/decoder.onnx",language="hi",task="transcribe",tailPaddings=1000))
val config=OfflineRecognizerConfig(featConfig=FeatureConfig(16000,80), modelConfig=modelConfig)
val recognizer=OfflineRecognizer(null,config) // null AssetManager filesDir
val stream=recognizer.createStream(); stream.acceptWaveform(floats,16000); recognizer.decode(stream); val text=recognizer.getResult(stream).text; stream.release()
```

### VAD-gated live decode via ASREngine (hold-to-talk)
```kotlin
// Source: android/app/src/main/java/com/vachak/ml/adapter/SherpaAsrAdapter.kt:43
override fun transcribe(pcm16:ShortArray,sr:Int): EngineResult<String>{
  if(context==null||!useReal) return Ok("[DEV-FIXTURE-asr] नमस्ते")
  val engine=real?: IndicConformerAsrAdapter(context)
  val segments=vad.detect(pcm16,sr) // SherpaVadDetector
  if(segments.isEmpty()) return Ok("")
  val texts=segments.mapNotNull{ seg-> val s=(seg.startMs*sr/1000).coerceAtLeast(0); val e=(seg.endMs*sr/1000).coerceAtMost(pcm16.size); if(e<=s) null else{ val floats=FloatArray(e-s){ pcm16[s+it]/32768.0f }; engine.transcribe(floats,sr).text } }.filter{it.isNotBlank()}
  return Ok(texts.joinToString(" ").trim())
}
```

### Mic capture 16k mono PCM16 4s max background (port to LiveScreen)
```kotlin
// Source: android/app/src/main/java/com/vachak/ui/MainScreen.kt:220
private fun recordMic(sr:Int=16000,maxMs:Int=4000):ShortArray{
  val minBuf=AudioRecord.getMinBufferSize(sr,CHANNEL_IN_MONO,PCM_16BIT)
  val rec=AudioRecord(MIC,sr,CHANNEL_IN_MONO,PCM_16BIT, maxOf(minBuf,sr*2))
  val n=(sr*maxMs)/1000; val buf=ShortArray(n); rec.startRecording(); var off=0
  while(off<n && rec.recordingState==RECORDSTATE_RECORDING){ val r=rec.read(buf,off,minOf(n-off,minBuf/2)); if(r<=0)break; off+=r }
  rec.stop(); rec.release(); return if(off<n) buf.copyOf(off) else buf
}
```

### Permission launcher
```kotlin
// Source: android/app/src/main/java/com/vachak/ui/MainScreen.kt:104
val launcher=rememberLauncherForActivityResult(RequestPermission()){ granted-> permissionState=granted }
if(!hasRecordPermission(context)){ status="mic permission needed"; launcher.launch(RECORD_AUDIO); return@Button }
```

### Legacy mock to kill (LiveScreen current fixture)
```kotlin
// Source: android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt:52 (TO DELETE)
// delay(180) MT mock + LanguagePair("hi","mund") + AudioTrack(16000) hardcode + simulatedHindi default
// Replace with: engine.asr.transcribe(pcm,16000) → sat_Olck translate → synthesize at audio.sampleRate
```

### Latency T0→T4 sequential (Vachak-Latency)
```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/LatencyTracker.kt:45
val tracker=LatencyTracker(LatencySample("live-${SystemClock.elapsedRealtimeNanos()}"))
tracker.markSpeechBegin(); /* recordMic + VAD + ASR */ tracker.markAsr(hindi); tracker.markTranslate(sat); tracker.markTtsBegin(); /* synthesize */ tracker.markAudioBegin(); Log.d("Vachak-Latency", "${tracker.result().endToEndMs()} total ${tracker.result().stageMs()} isFixture=${tracker.result().isFixture} ${tracker.result().stageMs()["asr"]}≤1000?")
```

### TTS playback at model's rate (22050 not 16000)
```kotlin
// Source: android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt:162 (fix)
val audio=engine.tts.synthesize(sat,"sat_Olck") // returns SynthAudio(samples,sampleRate=22050)
if(audio is Ok){ val track=AudioTrack(MUSIC,audio.value.sampleRate, CHANNEL_OUT_MONO, PCM_16BIT, audio.value.size*2, MODE_STATIC); track.write(audio.value,0,audio.value.size); track.play(); if(audio.value.size <= 0.2*audio.value.sampleRate) Log.w("Vachak-TTS","audible FAIL <200ms") }
```

### Engine wiring single injection (MainActivity)
```kotlin
// Source: android/app/src/main/java/com/vachak/engine/EngineProvider.kt:48
fun real(context:Context)=EngineProvider( translation=IndicTrans2Adapter(context), asr=SherpaAsrAdapter(context, vad=SherpaVadDetector(context)), tts=SherpaTtsAdapter(context), curriculum=content, worksheet=content, flashcard=content, packs=PackManager, sync=PackManager ... )
class MainActivity: ComponentActivity(){ override fun onCreate(s:Bundle){ super.onCreate(s); setContent{ VachakApp(engine=EngineProvider.real(this)) } } }
```

### Offline audit
```kotlin
// Source: android/app/src/androidTest/java/com/vachak/offline/OfflineHarnessTest.kt:27
@Test fun app_has_no_internet_permission(){ assertFalse(ctx.checkSelfPermission(INTERNET)==GRANTED) } // manifest no INTERNET required for grep wr verification P6
```
</code_examples>

<sota_updates>
## State of the Art (2024-2025)

| Old Approach | Current Approach | When | Impact |
|--------------|------------------|------|--------|
| Vosk Hindi 50M | sherpa `OfflineRecognizer` whispertiny/zipformer 99M + Silero 632K | 2023 sherpa 1.4→1.13 | Single AAR ASR/TTS/VAD `OfflineRecognizer`/`Vad`/`OfflineTts` Kotlin null AssetManager `filesDir` |
| AssetManager `context.assets` sherpa ctor | `null` AssetManager + `filesDir/vachak_models` via `SherpaAssets.prepare` | P0 fix STATE.md:7 | AssetManager nested subdirs fail; filesystem pattern proven |
| float32 whisper 100M | int8 `encoder.int8.onnx` 50–70M | sherpa releases 2023 | Script prefers `*encoder.int8.onnx` `fetch_android_models.sh:38` toward 30–80 budget |
| UI-thread `AudioRecord.read` | `VadStream.runMicrophoneVad` 10–100ms background `READ_BLOCKING` | Phase 5A `VadStream.kt:135` | UI jank + `RECORDSTATE_RECORDING` guard solved |
| `System.currentTimeMillis` latency | `elapsedRealtimeNanos` `ml/LatencyTracker.kt:52` | `LATENCY_INSTRUMENTATION.md:6` | Wall-clock skew on sleep/NTP solved |
| Mock `LiveScreen runPipeline(simulated)` + `delay(180)` + `mund` | Real `AudioRecord`→`transcribe(pcm,16000)`→`sat_Olck` translate→22050 `AudioTrack` + `ml/LatencyTracker` T0→T4 | P8 (now) | This phase's bug: UI overhaul left adapters unused — fix is port of P3 `MainScreen.recordMic` + permission + VAD→ASR→MT→TTS sequential |

**Tools to consider:**
- `sherpa OnlineRecognizer` streaming (`isReady`/`isEndpoint`/`reset`) for incremental Hindi partials (`SimulatorStreamingAsr` `Vad.kt`) — keep as P6 optimization, MVP is offline utterance `OfflineRecognizer` simpler ≤1s.
- `Moonshine/Paraformer` code-switch VAD+ASR singletons — not Hindi-specific, not budgeted, keep whispertiny.
</sota_updates>

