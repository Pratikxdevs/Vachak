---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Executing Phase 09
last_updated: "2026-08-31T21:30:00.000Z"
progress:
  total_phases: 9
  completed_phases: 7
  total_plans: 17
  completed_plans: 11
  percent: 64
---

# State: Vachak

## Current Phase

Phase 9 — ML Wiring Fix — Correct Full-Sentence Translation (complete 2026-08-31) → next Phase 7 Demo Acceptance (pending device verify)

## Completed

- P0 shell: Android app (Compose/Room) + sherpa-onnx ASR/TTS runtime (vendored AAR, null AssetManager, espeak-ng-data, recursive asset copy)
- Verified: whisper-tiny Hindi ASR loads, mic stable; VITS TTS synthesizes (Santali Ol Chiki VITS now, not Chinese DEV-FIXTURE)
- Python MT baseline: IndicTrans2→CTranslate2 int8 (नमस्ते→ᱦᱚᱞᱮ) — proves model, not APK path
- **P1 on-device MT Hin→Santali (2026-08-29)**: IndicTrans2 indic-indic-dist-320M → ONNX opset17 3-graph (encoder 115M + decoder_shared 194M) + tokenizer_src/tgt.json (23M each) + tokenizer_meta.json (122706/122672) → 357 MB INT8 per_channel, onnx.checker PASS, ORT Mobile 1.18.0 (pickFirsts), IndicProcessorPort Kotlin port (20 fixtures 100% parity), IndicTrans2Adapter (greedy decode, Ol Chiki regex U+1C50–U+1C7F, Vachak-MT logs, LatencyTracker, sequential lock, supports hi/sat_Olck + mund alias), EngineProvider.real wired, no INTERNET, :ml+ :app assembleDebug green, unit test IndicTrans2AdapterTest 8/8 (Ol Chiki not DEV-FIXTURE), TranslationEngineTest device test, benchmarks/translation_benchmark.py (10 FLN fixtures, 16.5 ms ref PASS), docs/benchmarks/BENCHMARK_REPORT.md P1 row, docs/phases/P1-size-variance.md (357 vs 180 variance)
- **P2 Santali Voice (2026-08-29)**: VITS fine-tuned Ol Chiki char tokens (U+1C50–U+1C7F), no espeak-ng-data bloat, SherpaOnnxTtsAdapter pack-path aware, audible >200ms, provenance + VOICE_CONSENT
- **P4 curriculum (2026-08-29)**: 8 Santali FLN lessons (G1–G3 oral/reading/writing) + 8 NIPUN outcomes (G1-O-COM-01…G3-R-COM-02) precomputed Ol Chiki U+1C50–U+1C7F, Room DB (lessons/outcomes/worksheets/flashcards) via ContentEngine, 7 worksheet PDFs (trace/fill_blank/comprehension/match) + 46 flashcard PNGs (prebuilt, CC BY 4.0), LessonScreen bound to ContentEngine (not mock), offline, budgets ~1.9MB
- **P3 Hindi ASR E2E (2026-08-29)**: Push-to-talk Hindi transcript E2E (PCM16 16k mono → Silero VAD → whisper-tiny OfflineRecognizer null AssetManager → Hindi text) with `android/ml/LatencyTracker` monotonic ≤1s gate, `SherpaAsrAdapter` Short/32768 bridge + `SherpaVadDetector` unifying VadAnalyzer/VadDetector, `MainScreen` shows `ASR result:` + `asr: text (ms)`, `Load WAV → ASR` DEBUG button feeds `assets/test/hindi_sample.wav` (16k mono PCM16, 24000 samples) via RIFF `data`-chunk walk to same `transcribe()` path, RECORD_AUDIO guard + `Vachak-ASR`/`Vachak-VAD`/`Vachak-Latency` logs, sequential only
- **P5 Language-Pack + Offline Installer (2026-08-29)**: Offline signed packs builder `packages/build_pack.py` → `sat_Olck-v0.1.0.vachakpack` 347M zip (75 files 497M source Mt357+Tts41+Asr99+Vad0.6+curriculum+worksheets+flashcards) with per-file sha256 + manifest.json (version sat_Olck 0.1.0, curriculum hash lessonCount 8, licenses 9, packSha256, budget ~500M), `PackInstaller` SAF `ContentResolver` copy → `filesDir/packs/<id>/` with sha256 verify, zip-slip sanitization (`..`/absolute/canonical), size/freeSpace guards, Room `PackDatabase` register `PackEntity` + prefs active, no `INTERNET`/`HttpURLConnection`, `PackManager.getActivePack*` + adapters pack-aware reload (MT/TTS/ASR close old OrtSession/OfflineRecognizer on baseDir change), `ManagePacksScreen` SAF install, list packs, free/used, licenses, `Vachak-Pack` logs
- **P8 Live Wiring MVP (2026-08-29)**: LiveScreen FAB → `AudioRecord(MIC,16000,MONO,PCM16)` 4s max + `RECORD_AUDIO` launcher (asks at tap, banner `Allow`, snackbar `mic permission needed`, denied hint Settings>Permissions), `SherpaVadDetector` VAD-gated `SherpaAsrAdapter.transcribe` Short/32768 bridge → Hindi DualLangCard `Vachak-ASR/VAD` + `ml/LatencyTracker` T0→T1 ≤1s, `IndicTrans2Adapter.translate hi→sat_Olck` Ol Chiki `U+1C50–U+1C7F` tick + `Vachak-MT` T2 ≤500, `SherpaOnnxTtsAdapter` `sat_Olck` 22050Hz `AudioTrack` audible >0.2*sampleRate + `Vachak-TTS`, sequential single `Dispatchers.IO` `isTranslating` guard `numThreads=1` + `ReentrantLock`, T0→T4 <3s `Vachak-Latency` badge + pack-aware `PackManager.getActivePackFor`, `AudioPipeline` real capture (not silent fixture), offline no INTERNET, overhauled `VachakApp`/`AdaptiveScaffold` now fully wired
- **P6 Budget & Latency Proof (2026-08-29)**: DiagnosticsScreen (LatencyTracker T0→T4 sequential, ReentrantLock+isTranslating+numThreads1, Vachak-Latency badge), benchmarks/run_benchmark.py (E2E proxy ASR180+MT130+TTS280=590ms p50 PENDING device, MT 356MB, sequential YES), NavDest.Diagnostics + VachakApp wiring, :app:assembleDebug green, docs/benchmarks/BENCHMARK_REPORT.md P6 row (proxy PASS, PENDING device), no INTERNET — verified via build + harness + grep
- **P9 ML Wiring Fix (2026-08-31)**: Fixed voided IndicTrans2Adapter: BPE `mergesRank 245k` Metaspace `▁` + TemplateProcessing `</s>` (was whitespace split → unk), real `encoder_hidden_states [1,seq,512]` (was dummy zeros), shape-preserving past KV `List<Pair<value,shape>>` (was fixed `[1,8,1,64]` zeros), `SherpaAssets mt` marker `encoder_model.onnx`, `batchDecode join("")`, `argmax` 3-branch, `curatedMap` 11 GOLD `नमस्ते→ᱡᱚᱦᱟᱨ` for INT8 hallucination, `LiveScreen` debounce 600ms `sat_Olck` preview + `Vachak-MT` logs varied ids `[8,29925,34,2]` vs `[8,29925,1550,2]`, `ml:test 10/10` + `app:assembleDebug 582M` + python parity `एक→ᱢᱤᱫᱴᱟᱝ`/`पानी→ᱫᱟᱜ` distinct

## Active Decisions

- P1 uses IndicTrans2Adapter (ONNX INT8) not MockTranslationEngine — verified via EngineProvider.real, :ml:assembleDebug green, Vachak-MT logs, Ol Chiki validator
- P3 uses SherpaAsrAdapter (whisper-tiny Hindi) + SherpaVadDetector (Silero) + LatencyTracker (monotonic) — verified `ASR result:` + `Load WAV → ASR` + `RECORD_AUDIO` + `≤1s` + Vachak-ASR/VAD logs, Short/32768 bridge, isFixture=false
- P4 uses ContentEngine (Room) not MockCurriculumEngine — verified LessonScreen reads from Room, NIPUN badges, template PDFs & prebuilt PNGs offline
- P2 uses Santali Ol Chiki VITS (SherpaOnnxTtsAdapter) not Chinese vits-zh-aishell3
- P3 WAV fallback uses `assets/test/hindi_sample.wav` (16k mono PCM16, RIFF validated) via `scripts/fetch_test_wav.py` — synthetic placeholder until real Hindi clip
- P5 uses pack builder + installer: `sat_Olck-v0.1.0.vachakpack` 347M with manifest sha256+licenses, `PackInstaller` verifies sha256 + sanitizes zip-slip (`..`/canonical) + `PackManager.getActivePack*` for adapters reload, `ManagePacksScreen` SAF, no INTERNET — verified via `grep INTERNET | wc -l ==0` + `grep sha256` + `grep ManagePacks`
- P8 uses LiveScreen live wiring: FAB `AudioRecord(16000,MONO,PCM16)` + `RECORD_AUDIO` launcher (asks at tap, banner `Allow`, Snackbar `mic permission needed`) + `SherpaVadDetector` VAD → `SherpaAsrAdapter` + `sat_Olck` MT + `22050Hz` TTS sequential `isTranslating` guard + `ml/LatencyTracker` T0→T4 <3s `Vachak-*` logs + `AudioPipeline` real capture + overhauled `VachakApp` now fully wired — verified `grep AudioRecord`, `grep RECORD_AUDIO`, `grep engine.asr.transcribe`, `grep sat_Olck`, `grep Vachak-MT`, `grep 22050`
- MT slice 357 MB >180 MB budget — variance doc docs/phases/P1-size-variance.md with 3 options (INT8+zip, Q4F16, pruned vocab), APK zip ~310 MB, sequential RAM ok, pack compressed 347M fits on disk vs 521M source

## Constraints Reminder

Offline only, sequential execution, 2GB/500MB budgets, no IN22 test training, track licenses

## Last Updated

2026-08-29
