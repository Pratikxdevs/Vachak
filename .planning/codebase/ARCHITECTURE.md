# Architecture

## System overview (offline-first, sequential pipeline)

Fully offline after install. No `INTERNET` permission (`android/app/src/main/AndroidManifest.xml:4-10`);
`OfflineHarnessTest` / `TranslationEngineTest` assert this. All inference is local via ONNX
Runtime Mobile + sherpa-onnx; packs arrive only as side-loaded files (SAF/USB/bundled asset).

Sequential voice pipeline — ASR → MT → TTS, never parallel (2 GB RAM bound):

```
Mic (AudioRecord 16k mono PCM16, ≤4s, RECORD_AUDIO-gated)
 → AudioCapturer (single instance, LiveViewModel-owned)
 → VAD (SherpaVadDetector / VadStream, segments silence)
 → ASR  (IndicConformerAsrAdapter via SherpaAsrAdapter, hi)   ≤1s
 → normalize + language tags (IndicProcessorPort: hin_Deva→sat_Olck/unr_Deva)
 → MT   (AdapterTranslationEngine → OnnxIndicTrans2Adapter / IndicTrans2Adapter CT2 / phrasebook) ≤0.5s
 → Ol Chiki validate (U+1C50–U+1C7F check in ContentEngine/AppDatabase)
 → TTS  (SherpaOnnxTtsAdapter via SherpaTtsAdapter, VITS, speed 1.2, chunked ≤150 chars) ≤1s
 → AudioTrack speaker
 Total <3s (LatencyBudget.TOTAL_MS; LastPipelineRun + LatencyTracker proof in DiagnosticsScreen)
```

Curriculum path bypasses MT entirely: lessons/worksheets/flashcards are precomputed
assets in Room, served by `ContentEngine`. `LiveViewModel` owns the single live pipeline;
`LiveScreen` is a pure renderer of its `LiveUiState`. `EngineProvider.mock()` vs `.real()`
is the sole mock/real swap point — UI never changes.

## Module map (android/: app/core/ml/content/sync responsibilities)

| Module (`android/settings.gradle.kts:16`) | Responsibility | Key entries |
|---|---|---|
| `:app` | Compose UI, Hilt DI, navigation, audio playback, orchestrator glue | `VachakApplication`, `di/AppModule`, `engine/EngineProvider`, `ml/adapter/AudioPipeline`, `ui/VachakApp`, `ui/MainActivity`, `ui/screens/*`, `ml/orchestrator/Orchestrator` |
| `:core` | Engine contracts + shared logic, no Android ML deps | `engine/EngineContracts` (Translation/ASR/TTS/Curriculum/Worksheet/Flashcard/Pack/Sync/Benchmark interfaces, `LatencyBudget`), `engine/ActiveLanguage`, `engine/LatencyTracker`, `engine/QuizEngine`, `engine/LessonFilter`, `engine/mock/MockEngines` |
| `:ml` | Real on-device adapters, tokenizers, audio utils, sherpa JNI | `ml/adapter/AdapterTranslationEngine` (router), `ml/adapter/OnnxIndicTrans2Adapter` (sat ONNX INT8, live path), `ml/adapter/IndicTrans2Adapter` (CT2 future merged path), `ml/adapter/MundariPhrasebookEngine`, `ml/adapter/mlinternal/SherpaAsrAdapter` + `IndicConformerAsrAdapter`, `ml/SherpaOnnxTtsAdapter`, `ml/SherpaAssets`, `ml/VadStream`, `ml/AudioCapturer`, `ml/StreamingAsrSession`, `ml/ModelStatus` |
| `:content` | Room curriculum DB + precomputed content engine | `content/ContentEngine`, `content/db/AppDatabase` (v2, 6 entities), `content/db/Entities`, `content/db/LessonDao` (+ Outcome/Worksheet/Flashcard/Activity/Assessment DAOs) |
| `:sync` | Offline pack installer + registry (installer, NOT network client) | `sync/PackInstaller` (SAF→verify→extract→Room), `sync/PackManager` (active-pack resolution), `sync/db/PackDatabase` |

Python side: `ml/translation/` (IndicTrans2 fine-tune/export/bench scripts), `ml/asr/`
(benchmark notes), `ml/tts/` (Santali VITS train/export/eval), `ml/pipeline/` (reference
Python pipeline mirror), `ml/benchmarks/` + top-level `benchmarks/` (device/latency harnesses).
Pack building: `scripts/build_modelpack.py`, `scripts/build_content_pack.py`, `scripts/pack.py`.
Content authoring: `curriculum/`, `worksheet/`, `flashcard/` (template engines, not AI generation).

## Key components

- **EngineProvider** (`app/.../engine/EngineProvider.kt:20-78`) — single injection point.
  `mock()` = all mock engines; `real(context)` = `AdapterTranslationEngine` + `SherpaAsrAdapter`
  + `SherpaTtsAdapter` + Room `ContentEngine`, syncs `ActiveLanguage` from installed pack.
- **Adapters** — `AdapterTranslationEngine` routes by target: sat_Olck → `OnnxIndicTrans2Adapter`
  (proven ONNX INT8 bundle, greedy decode + GOLD TSV pre-check); unr_Deva/mun family →
  `MundariPhrasebookEngine` (17k deterministic phrasebook until LoRA-merged CT2 ships);
  `IndicTrans2Adapter` = CT2 int8 pruned 223M future merged path (constructed-but-idle until
  `ct2_migration_benchmark` PASS). ASR: `SherpaAsrAdapter` (app + `:ml` mlinternal shim) wraps
  `IndicConformerAsrAdapter` with VAD segmentation. TTS: `SherpaTtsAdapter` (app) delegates to
  `:ml` `SherpaOnnxTtsAdapter` (sherpa-onnx `OfflineTts`, Ol Chiki char tokens, no espeak-ng-data).
- **AudioPipeline** (`app/.../ml/adapter/AudioPipeline.kt`) — mic→VAD→ASR→TTS orchestration;
  real `AudioRecord` capture (16k PCM16 ≤4s) + `AudioTrack` playback path. Live path now owned
  by `LiveViewModel`; pipeline class remains the testable orchestration unit.
- **LiveViewModel / LiveScreen** — `LiveViewModel` owns `AudioCapturer`, `StreamingAsrSession`,
  debounce, `LatencyTracker`, adapter extraction under `viewModelScope`; `LiveScreen` renders
  `LiveUiState` (isListening/isStopping/isTranslating/isSynthesizing/partial/liveHindi/committed).
- **Pack system** — `PackInstaller.install(uri)` (SAF ContentResolver → tmp → manifest/zip verify →
  `filesDir/packs/<lang>-v<ver>/` → Room + prefs active) + `ensureBundledPacks()` (first-run asset
  packs); `PackManager.getActivePack/getActivePackFor(subdir)` resolves `mt|tts|asr|vad` dirs for
  adapters with bundled-asset fallback via `SherpaAssets.prepare()`. `ManagePacksScreen`
  (route `packs`) lists/frees/installs packs + per-grade curriculum manifests.
- **Content DB** — `AppDatabase` (`vachak_content.db`, v2): lessons/outcomes/worksheets/flashcards/
  activities/assessment_prompts; prepopulated from `assets/curriculum/lessons/sat_lessons.json` +
  `outcomes/nipun.json`; Ol Chiki validation on insert; `ContentEngine` implements
  Curriculum+Worksheet+Flashcard engines with `runBlocking` sync wrappers + suspend IO paths.
- **LatencyTracker** — `:core` `engine/LatencyTracker` (start/mark/stop/report, `Vachak-Latency`
  tag) + `:ml` `LatencySample/LastPipelineRun` measured-only timings surfaced in
  `DiagnosticsScreen` (ASR/MT/TTS/Total + <3s verdict; canned values forbidden).
- **Concurrency guards (sequential proof)** — `LiveViewModel.pipelineDispatcher =
  Dispatchers.IO.limitedParallelism(1)` + `pipelineMutex: Mutex` (`withLock` around every
  ASR→MT→TTS run and retry); `OnnxIndicTrans2Adapter.lock: ReentrantLock` + `IndicTrans2Adapter`
  `synchronized(lock)` singleton CT2 handle (never per-request); `isTranslating`/`isSynthesizing`
  flags gate mic + buttons; sherpa configs `numThreads=1` (TTS `OfflineTtsConfig`, ASR/VAD).
  DiagnosticsScreen documents all three guards inline.

## Data flow

- **Audio**: mic PCM16 16 kHz mono (`AudioRecord`, `minBuf` sized, ≤4 s buffer) → VAD segments
  (startMs/endMs) → per-segment float32 (`/32768`) → ASR Hindi text. TTS returns float PCM
  (22.05 kHz VITS, `SynthAudio(samples, sampleRate, backend=sherpa-onnx, isFixture)`) → `AudioTrack`.
- **Text**: Hindi (Devanagari) → `IndicProcessorPort` normalize + `hin_Deva`/`sat_Olck`/`unr_Deva`
  tags → `SpApproxTokenizer`/BPE (245k merges, Metaspace ▁) → MT → Santali Ol Chiki
  (U+1C50–U+1C7F, validated in `ContentEngine.isOlChikiValid` + `AppDatabase.validateOlChiki`) or
  Mundari Devanagari (`unr_Deva`).
- **Content**: Room entities — `LessonEntity(id,titleHi,titleSatOlChiki,textHi,textSatOlChiki,grade,
  domain,outcomeId,precomputed)`, `OutcomeEntity(nipunCode,descriptor*)`, `WorksheetEntity(templateType,
  assetPath)`, `FlashcardEntity(conceptHi/conceptSatOlChiki/imagePath/sequence)`, `ActivityEntity`,
  `AssessmentPromptEntity`. Worksheets = template PDFs (`worksheet/templates/*.pdf`); flashcards =
  prebuilt PNGs (`flashcard/assets/*.png`).
- **Packs**: `.vachakpack` zips (`manifest.json` + `models[]`/`curriculum_files[]` with sha256) →
  `filesDir/packs/<id>/` (`vachak_models/{mt,asr,tts,vad}`, `curriculum/class/{1..5}/`) + `PackEntity`
  rows in `PackDatabase`; adapters resolve via `PackManager`, never via network.

## Trust boundaries & attack surface relevant to arch

- **SAF zip input (untrusted)** → `PackInstaller`: sha256 per-file verify, zip-slip guards
  (`..`/absolute reject + canonical-path containment), 800 MB pack / 600 MB entry caps,
  free-space check, manifest schema validation (`version` + `models[]`/`curriculum_files[]`
  non-empty). Failures return `EngineResult.Err`, delete partial `destDir`.
- **Pack sha256 / manifest** — per-file sha is primary; `packSha256` mismatch is warn-only (logged).
  `validatePackFile()` allows pre-install verification without extraction.
- **Mic permission** — `RECORD_AUDIO` (runtime-gated in LiveScreen with rationale); `MODIFY_AUDIO_SETTINGS`
  (normal, unmute before capture); `READ_EXTERNAL_STORAGE` (legacy pick fallback). Empty PCM →
  explicit `INVALID_INPUT`, never silent proceed.
- **No INTERNET** — manifest declares no network permission; `:sync` uses only `ContentResolver`;
  `SyncManager.isNetworkAllowed()=false` by contract; instrumented tests assert absence.
- **Exported components** — only `MainActivity` (`exported=true`, launcher); `FileProvider`
  (`exported=false`, `grantUriPermissions`, read-only worksheet PDF share). No exported services/receivers.
- **Model integrity** — ASR fingerprint (`AsrModelFingerprint`), MT GOLD TSV pre-check, TTS shim
  guard (55-token placeholder → text-only `MODEL_NOT_LOADED`, never fake audio); `ModelStatus`
  state machine surfaces READY/LOADING/ERROR honestly to UI.

## Constraints compliance (offline, sequential, 500MB budget, no IN22 training)

- **Offline**: zero runtime network calls; `AGENTS.md` hard rule + manifest + tests enforce.
  Packs via SAF/USB/bundled assets only.
- **Sequential**: single-model residency; `limitedParallelism(1)` + `Mutex`/`ReentrantLock`/
  `synchronized` + `numThreads=1` + `isTranslating` gating. ASR≤1s / MT≤0.5s / TTS≤1s / total<3s.
- **500 MB budget**: MT 100–180 (CT2 stripped 223M → merged ~210M / ONNX 357M transitional),
  ASR 30–80, TTS 20–80 (sprint VITS 110M), runtime/tokenizers 20–50, APK 40–70, content 10–30,
  flashcards 20–50, margin 30–50. DiagnosticsScreen shows live pack+DB+asset storage.
- **No IN22 training**: IN22-Gen/Conv are eval-only (`benchmarks/translation_benchmark.py`,
  `ct2_migration_benchmark.py`); fine-tune corpora are COILD-MT HIN-SAT + Education_v2 only.
- **Pedagogy honesty**: curriculum translations precomputed (never on-device MT); worksheets
  template-based; flashcards prebuilt; `MACHINE_TRANSLATED` content never shipped as approved
  pedagogy; Piper/GPL-3.0 handled deliberately (Ol Chiki char tokens avoid espeak-ng-data bloat).
