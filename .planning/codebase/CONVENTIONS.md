# Conventions

Observed from the Vachak repo at /cz/Vachak (Kotlin Android + Python ML). Offline Hindi→Santali (Ol Chiki) pedagogy app.

## Kotlin style (observed: naming, Compose, logging, error handling)

- **Packages/modules:** `com.vachak.{engine,ml,content,sync,ui}` split across Gradle modules (`android/app`, `android/ml`, `android/core`, `android/content`, `android/sync`). Adapters live in `com.vachak.ml.adapter` (e.g. `IndicTrans2Adapter.kt`, `OnnxIndicTrans2Adapter.kt`, `SherpaAsrAdapter.kt`, `SherpaTtsAdapter.kt`); internal impls in `adapter/mlinternal/`; orchestrator in `android/ml/orchestrator/` (`Orchestrator.kt`, `ErrorPolicy.kt`, `ClassroomState.kt`).
- **Naming:** `*Adapter` = model runtime behind an engine interface; `*Engine` = interface (`ASREngine`, `TranslationEngine`, `TTSEngine` in `EngineContracts.kt`); `*Screen`/`*Components` = Compose UI (`HomeScreen.kt` + `HomeComponents.kt`, `LiveScreen.kt` + `LiveComponents.kt`); `*Dao`/`*Entity`/`*Database` = Room; `Pack*` = language-pack system (`PackManager.kt`, `PackInstaller.kt`, `PackDao.kt`).
- **Compose:** one Screen file per destination plus a `*Components.kt` file for reusable composables; theme in `ui/theme/` (`Color.kt`, `Type.kt`, `Theme.kt`); `NotoOlChikiFamily` composite fallback (Lexend → Noto Devanagari → Noto Ol Chiki) so U+1C50–U+1C7F never renders tofu on Android 9. Navigation via `NavDest`/`NavRoute` (`ui/navigation/`).
- **Result type:** `EngineResult<T>` sealed (`Ok`/`Err`) + `EngineError` enum (`MODEL_NOT_LOADED`, `INVALID_INPUT`, `IO_ERROR`, …) used uniformly — Orchestrator, ContentEngine, PackInstaller all return it instead of throwing. `MODEL_NOT_LOADED` is the signal for `PROMPT_LANGUAGE_PACK` (see `ErrorPolicy.kt`).
- **Error handling:** per-stage `try/catch (e: Exception) → EngineResult.Err(...)`; blank ASR `Ok("")` treated as VAD-silence failure, never passed to MT (`Orchestrator.kt:77-83`); fallbacks degrade gracefully (MT fail → show source transcript; TTS fail → show translation text). `Log.w/e` on every degradation path.
- **Room:** entities in `Entities.kt` / `PackEntity.kt`; DAOs expose sync queries plus `*IO()` suspend wrappers forcing `Dispatchers.IO` (`PackDao.getActiveIO()`, `AppDatabase.getAllSuspend()`); `allowMainThreadQueries()` is DEBUG/demo-only with `Log.w` warnings; production paths must use suspend wrappers.

## Coroutine/concurrency patterns (sequential discipline, guards)

- **Sequential pipeline is a hard rule:** ASR → MT → TTS, never parallel (2 GB RAM). Stated in `Orchestrator.kt` header and enforced structurally — single `processUtterance()` call chain, one model resident at a time.
- **`Dispatchers.IO`:** all Room reads and pack file I/O go through `withContext(Dispatchers.IO)`; sync wrappers (`getActivePack()`, `installed()`, `ContentEngine.getLessons()`) use `runBlocking(Dispatchers.IO)` as cold-path bridges only.
- **Guards:** `@Volatile var handle: Long` + `private val lock = Any()` for native singleton handles (`IndicTrans2Adapter`); `synchronized(this)` double-checked singleton for Room (`PackDatabase`); `@Volatile var sample` in `LastPipelineRun`; `ReentrantLock`-style `Any()` monitor rather than `Mutex` (no structured-concurrency scope needed for JNI handles).
- **Audio:** `AudioCapturer`, `VadStream`, `StreamingAsrSession` — VAD-gated segmentation before ASR; TTS synthesis is blocking `generate()` between `markTtsBegin()` and `markAudioBegin()` so the cost lands in the "render" span (see `LatencySample.ttsSynthMs()` honesty fix).

## Ol Chiki handling (U+1C50–U+1C7F validation, tokenizer parity fixtures)

- **Validation idiom (single canonical check):** `text.any { c -> c in '\u1C50'..'\u1C7F' }` — appears in `AppDatabase.validateOlChiki()`, `ContentEngine.isOlChikiValid()`, `Romanize.auto()`, `SherpaOnnxTtsAdapter.synthesize()`. Device tests use `Regex("[\u1C50-\u1C7F]")` (`TranslationEngineTest`, `BothAdaptersTest`).
- **Policy:** warn-don't-crash (`Log.w("Vachak-Content", …)` on insert without Ol Chiki); TTS warns but proceeds when Santali text lacks Ol Chiki.
- **Script routing:** Mundari = Devanagari, Santali = Ol Chiki (`SherpaOnnxTtsAdapter:229`); `ActiveLanguage` normalises to `sat_Olck` (default) with `isOlChiki()` helper.
- **Tokenizers:** Ol Chiki char-token `tokens.txt` (38–55 tokens + sil/eos/sp), no `espeak-ng-data` (avoids 10–15 MB + GPL-3.0); `SpApproxTokenizer` + `BpeCodecCache` on device mirror Python `tokenization_indictrans.py`; keep parity fixtures for both sides.
- **Fonts/romanization:** `NotoOlChikiFamily` in `Type.kt`; `Romanize.olChiki()` (Santali Latin orthography) with `auto()` dispatch by script range.

## Logging/observability (Vachak-ASR/MT/TTS/VAD/Latency/Pack tags)

- **Tag family:** `Vachak-ASR`, `Vachak-MT`, `Vachak-TTS`, `Vachak-VAD`, `Vachak-Latency`, `Vachak-Pack`, `Vachak-Content`, `Vachak-Assets`. All start with `Vachak-` so `VachakLogger` ring buffer (200 lines, in-proc) and `DebugOverlay` can filter; debug hint: `adb logcat -s Vachak-MT Vachak-ASR Vachak-TTS Vachak-Latency`.
- **`LatencyTracker` (Kotlin) mirrors `ml/pipeline/latency.py`:** marks T0 speech → T1 ASR → T2 translate → T3 TTS-begin → T4 audio; `endToEndMs = T4-T0`, monotonic `SystemClock.elapsedRealtimeNanos()`. `LastPipelineRun.publish()` logs `RUN … stages=… totalMs=… withinBudget=<3000`. Budgets: ASR ≤1000 ms, MT ≤500 ms, TTS ≤1000 ms, total <3000 ms — over-budget stages emit `Log.w("Vachak-Latency", …)`.
- **Honesty rule:** fixture/mock timings must never be reported as device latency (`LatencySample.isFixture`, `forbid_dev_as_android` guard in benchmark harness).

## Python/ML conventions

- **Layout:** `ml/translation/{scripts,mundari}/`, `ml/tts/{dataset,}`, `ml/pipeline/` (runtime mirror: `asr_adapter.py`, `tts_adapter.py`, `vad_stream.py`, `latency.py`), `ml/benchmarks/` (`run.py`, `harness.py`, `device_matrix.py`), `ml/finetune/`; top-level `benchmarks/*.py` (`translation_benchmark.py`, `asr_benchmark.py`, `tts_benchmark.py`, `run_benchmark.py`, `conversion_benchmark.py`).
- **Style:** `from __future__ import annotations`, `pathlib.Path`, `argparse` CLIs, docstring header with usage; `train.py`/`finetune_*.py` per domain; offline-only (no network calls; `http://` strings in tests are placeholders, not fetches).
- **Eval hygiene:** FLN fixtures (10 sentences) in benchmark scripts; IN22-Gen/Conv are eval-only, never training — keep splits separate.
- **Outputs:** benchmarks print `*_MB`, p50/p95 and append rows to `docs/benchmarks/BENCHMARK_REPORT.md` (template with PENDING-until-measured cells).

## Gaps & improvement suggestions (top 5, ranked by ROI — lint, detekt/ktlint, error handling, null-safety, log hygiene for open-source)

1. **Add ktlint + detekt (highest ROI).** No lint config found (`grep detekt|ktlint|spotless` in `android/` = 0 hits). Add `ktlint` via Spotless + a minimal `detekt.yml` (complexity, coroutine, null-safety rules) wired into CI; auto-format the `*Components.kt` sprawl first.
2. **Replace `runBlocking` bridges with structured concurrency.** `ContentEngine`/`PackManager` sync wrappers use `runBlocking(Dispatchers.IO)` — ANR-safe only by accident. Migrate call sites to suspend functions + `viewModelScope`, keep one audited bridge with a comment.
3. **Tighten error taxonomy.** `catch (e: Exception) → IO_ERROR` swallows everything (Pack/Content paths). Map `SQLiteException`/`IOException`/`SecurityException` distinctly; reserve a `VALIDATION` code for Ol Chiki/zip-slip rejects so diagnostics can count them.
4. **Null-safety pass on `!!` and nullable DAO returns.** `transcript!!` after retry loop and nullable `getById` chains are the likeliest NPEs; enable detekt `UnsafeCallOnNullableType` and convert to early-return `Err`.
5. **Log hygiene for open-source.** `Log.d` sometimes prints full MT output/audio sizes; cap message length, never log PII/voice bytes, and gate verbose `Vachak-*` debug logs behind `BuildConfig.DEBUG` while keeping budget `Log.w` always-on.
