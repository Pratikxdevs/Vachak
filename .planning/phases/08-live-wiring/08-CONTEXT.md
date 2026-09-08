# Phase 08: Live Wiring MVP — Context

**Gathered:** 2026-08-29
**Status:** Ready for planning
**Source:** User live-wire demand + UI overhaul audit (LiveScreen mock vs real engines)

<domain>
## Phase Boundary

Wire the **entire live pipeline** to the overhauled UI so a teacher can speak Hindi on the tablet and see live Hindi transcription + Santali Ol Chiki translation + play Santali audio **entirely offline, sequential, <3s**. Covers audio capture, VAD, ASR, MT, TTS, LiveScreen/Home/Tools wiring, permission, latency, pack-aware reload. Does NOT include retraining models (P1/P2 already shipped), pack builder (P5 shipped), or final <3s/2GB benchmark report (P6) or demo evidence (P7) — but must enable them.

Out of scope: new model training, new curriculum authoring, pack signing, budget report rendering (P6), WiFi-OFF evidence capture (P7). This phase ships the **working MVP** that P6/P7 measure.
</domain>

<decisions>
## Implementation Decisions

### Locked (from user rant + prior phases)

- **Goal today is MVP that works:** `audio capturing work` → `live transcription` → `live translation (entire AI model)` → TTS playback, all wired to updated UI. User can speak Hindi and see live transcription, not bullshit fixture.
- **UI is overhauled:** `VachakApp.kt` with `HomeScreen`, `LiveScreen`, `CurriculumScreen`, `ToolsScreen`, `SettingsScreen`, `ManagePacksScreen` via `AdaptiveScaffold` + `Cupertino` — none of the live elements are currently wired to real `EngineProvider.real()` engines. Must wire them.
- **Audio contract is 16k mono PCM16, 4s max, `AudioRecord(MIC, 16000, CHANNEL_IN_MONO, PCM_16BIT)`** — already proven in `MainScreen.kt` (P3) and `VadStream.kt` but LiveScreen uses simulated string `"नमस्ते, कैसे हैं आप?"` + `delay(180)` mock. Replace mock with real capture.
- **Sequential only:** `ASR → MT → TTS` never parallel (2GB RAM, `numThreads=1`, `ReentrantLock` in `IndicTrans2Adapter`). Enforce via `Orchestrator.kt` or in LiveScreen coroutine.
- **VAD gated:** Silero VAD (`VadStream.SherpaOnnxVadAnalyzer` + `VadAdapter.SherpaVadDetector`) must sit before ASR — already implemented but LiveScreen bypasses `vad.detect()` entirely.
- **ASR is sherpa-onnx whisper-tiny via `SherpaAsrAdapter` + `IndicConformerAsrAdapter` (null AssetManager `OfflineRecognizer(null,config)`)** — already ships, but LiveScreen calls only `engine.translation.translate` with fixture text, never `engine.asr.transcribe(pcm,16000)`.
- **MT is `IndicTrans2Adapter` (ONNX int8 357MB, ORT Mobile 1.18, `IndicProcessorPort`, `sat_Olck` + `mund` alias) ** — LiveScreen calls it with `LanguagePair("hi","mund")` but should use `sat_Olck` (Santali-only) and show Ol Chiki validation.
- **TTS is `SherpaOnnxTtsAdapter`/`SherpaTtsAdapter` (sherpa-onnx VITS 22k mono Ol Chiki, `dataDir=""`) ** — LiveScreen `tryPlayPcm` uses hardcoded `16000` but TTS `sampleRate` is `22050`; must play at model's rate and check audible >200ms.
- **Permission is `RECORD_AUDIO` via `ContextCompat.checkSelfPermission` + `ActivityResultContracts.RequestPermission`** — P3 `MainScreen` already does this, LiveScreen does zero permission check.
- **Latency is `LatencyTracker` (ml, `elapsedRealtimeNanos` T0→T1→T2→T3→T4, `Vachak-Latency`) ** — LiveScreen uses `System.currentTimeMillis` only for total, not per-stage, and never logs `Vachak-ASR`/`Vachak-MT`/`Vachak-TTS`.
- **Pack-aware:** Adapters already resolve `PackManager.getActivePack*` fallback to `SherpaAssets.prepare` (P5). LiveScreen must not break this — engines reload via `closeSessions` on pack switch.
- **Offline only, no INTERNET, WiFi OFF** — `AndroidManifest.xml` already has no `android.permission.INTERNET`; installer is `sync/` not network client. LiveScreen must not add network calls.
- **Language scope Santali-only:** `sat_Olck` Ol Chiki primary, `mund` kept as alias for backward compat but UI should display `sat_Olck`.

### Claude's Discretion

- Whether to reuse `AudioPipeline.kt` vs inline `recordMic()` in LiveScreen, and whether to expose partial streaming (incremental `onPartial` callback) vs hold-to-talk 4s utterance.
- Exact FAB interaction (press-hold vs tap toggle), waveform visualizer binding to real PCM amplitude, and error UI for `RECORD_AUDIO` denied / `INVALID_INPUT` / silence (empty VAD segments).
- Whether to centralize sequential pipeline in `ml/orchestrator/Orchestrator.kt` vs keep `runPipeline` in LiveScreen — either is valid if it preserves `numThreads=1` + `ReentrantLock`.
- Tooling: keep `LiveScreen` as primary live surface; `HomeScreen` `FocusCard` → `Live` navigation already exists; `ToolsScreen` may surface translate/TTS debug.

### Deferred Ideas (OUT OF SCOPE — do NOT plan)

- Retraining MT (P1) or TTS (P2) — models already shipped, pack-swapped via P5.
- Curriculum authoring (P4) — lessons already in Room.
- Pack builder/signing (P5) — builder already emits `.vachakpack`.
- Formal P6 latency harness `benchmarks/` + `BENCHMARK_REPORT.md` (P6 will measure this phase's wiring).
- Demo evidence `docs/demo-acceptance.md` (P7).
- Floating overlay / background service for live capture — use foreground `Activity` only for MVP.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Overhauled UI (source of bug)
- `android/app/src/main/java/com/vachak/ui/VachakApp.kt` — `AdaptiveScaffold` routing (Home, Live, Curriculum, Tools, Settings, ManagePacks)
- `android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt` — current mock `runPipeline(simulatedHindi)` + `toggleListening` (no AudioRecord, no VAD, no ASR, delay(180) MT, hardcoded 16000 playback, no permission, System.currentTimeMillis)
- `android/app/src/main/java/com/vachak/ui/screens/HomeScreen.kt` — `FocusCard` → Live navigation
- `android/app/src/main/java/com/vachak/ui/navigation/NavDest.kt` — `ManagePacks` dest

### Audio & Engine Contracts
- `android/app/src/main/java/com/vachak/ml/adapter/AudioPipeline.kt` — sequential `captureAndRecognize` + `speak` (skeleton, currently synthesizes silent buffer)
- `android/ml/src/main/java/com/vachak/ml/VadStream.kt` — `VadAnalyzer` + `SherpaOnnxVadAnalyzer` (Silero, null AssetManager) + `runMicrophoneVad` chunk 10–100ms
- `android/app/src/main/java/com/vachak/ml/adapter/VadAdapter.kt` — `VadDetector` + `MockVadDetector` + `SherpaVadDetector` (unifies :ml streaming ↔ :app whole-buffer, logs `Vachak-VAD`)
- `android/ml/src/main/java/com/vachak/ml/IndicConformerAsrAdapter.kt` — `OfflineRecognizer(null,config)` whispertiny HI, pack-aware `resolveBaseDir`, logs `Vachak-ASR`
- `android/app/src/main/java/com/vachak/ml/adapter/SherpaAsrAdapter.kt` — `ASREngine` VAD→segment→Short/32768→decode, logs `Vachak-ASR`/`Vachak-Latency`, `isFixture=false`
- `android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt` — MT Hin→sat_Olck (3-graph ONNX int8, ORT Mobile, `IndicProcessorPort`, lock, pack-aware)
- `android/ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt` — TTS VITS 22.05k Ol Chiki, pack-aware `resolveBaseDir`, `dataDir=""`
- `android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt` — recursive `prepare(context, subdir)` → `filesDir/vachak_models/<sub>` (null AssetManager proven fix)
- `android/core/src/main/java/com/vachak/engine/EngineContracts.kt` — `ASREngine.transcribe(ShortArray,16000)`, `TranslationEngine.translate`, `TTSEngine.synthesize`, `LatencyBudget ASR1000 MT500 TTS1000 TOTAL3000`
- `android/ml/src/main/java/com/vachak/ml/LatencyTracker.kt` — `LatencySample` T0 speechBegin T1 ASR T2 translate T3 TTS T4 audio, `elapsedRealtimeNanos`, `isFixture`
- `android/app/src/main/java/com/vachak/engine/EngineProvider.kt` — `real(context)` wires `SherpaAsrAdapter(context, vad=SherpaVadDetector)` + `IndicTrans2Adapter` + `SherpaTtsAdapter`, pack paths via `PackManager`
- `android/ml/orchestrator/Orchestrator.kt` — sequential `Orchestrator` state machine (existing, check if used)
- `android/sync/src/main/java/com/vachak/sync/PackManager.kt` — `getActivePack` / `getActivePackFor(subdir)` for pack-aware adapters

### Permissions & Offline
- `android/app/src/main/AndroidManifest.xml` — `RECORD_AUDIO` declared, `INTERNET` absent (no network)
- `android/app/src/main/java/com/vachak/ui/MainScreen.kt` — P3 reference `recordMic` + `loadWavPcm16Mono16k` + `RECORD_AUDIO` guard + `LatencyTracker` + `Vachak-*` logs (correct pattern to port to LiveScreen)
- `docs/MODEL_AND_DATA_PROVENANCE.md` + `THIRD_PARTY_NOTICES.md` — licenses (MIT sherpa, MIT IndicTrans2, CC BY 4.0 datasets, no Piper in APK)

### Benchmark & Acceptance (for wiring correctness)
- `docs/benchmarks/BENCHMARK_REPORT.md` — MT 16.5ms ref PASS, PENDING target 2GB <3s sequential
- `AGENTS.md` — hard rules: offline, sequential, template worksheets, prebuilt flashcards, Piper GPL handling
- `.planning/REQUIREMENTS.md` — `ASR-01/02`, `PACK-01/02`, `PERF-01/02`, `DEMO-01`
</canonical_refs>

<specifics>
## Specific Ideas

- FAB: tap to start/stop (orange→red), shows `Listening… speak in Hindi` + `BreathVisualizer` pulsing; on stop, real `AudioRecord` 16k mono PCM16 4s max is fed through VAD then ASR; Hindi result appears in `DualLangCard(label="Hindi (ASR)")`.
- Translate button: calls `engine.translation.translate(hindiText, LanguagePair("hi","sat_Olck"))` (not `mund`), shows Santali Ol Chiki in second `DualLangCard`; Ol Chiki regex `[\u1C50-\u1C7F]` validated, log `Vachak-MT`.
- Play Audio: `engine.tts.synthesize(santaliText, "sat_Olck")` then `AudioTrack` at `audio.sampleRate` (22050, not 16000), check `pcm.size > 0.2*sampleRate`, log `Vachak-TTS`.
- Error: if `RECORD_AUDIO` denied → `mic permission needed` snackbar; if VAD returns empty → `silence` hint; if ASR err → show `EngineError` message.
- Sequential enforcement: run `ASR → MT → TTS` in one coroutine `withContext(Dispatchers.IO)` holding `ReentrantLock` or `Orchestrator`, never parallel, `numThreads=1`, one model resident.
</specifics>

<deferred>
## Deferred Ideas

- Streaming partial ASR (`OnlineRecognizer`) for incremental live transcription — kept as `OfflineRecognizer` utterance decode for MVP (≤1s already).
- Waveform amplitude → `BreathVisualizer` real binding (currently isListening boolean only).
- Background service / floating mic overlay — foreground Activity only for MVP.
- Formal P6 harness `benchmarks/translation_benchmark.py` device measure and `BENCHMARK_REPORT.md` <3s proof.
- Demo evidence capture `docs/demo-acceptance.md` screenshots + logcat.
</deferred>

---
*Phase: 08-live-wiring*
*Context gathered: 2026-08-29 via user live-wire rant + UI audit*
