# Testing

## Test inventory (unit: IndicTrans2AdapterTest 8/8, TranslationEngineTest, ml:test 10/10; device: assembleDebug; benchmarks: translation_benchmark.py, run_benchmark.py; demo acceptance checklist)

**JVM unit tests (`test/`, ~90 `@Test` total):**

| Module | File | Tests | What it covers |
|--------|------|-------|----------------|
| ml | `StreamingAsrSessionRegressionTest` | 24 | Streaming ASR session regressions |
| ml | `SherpaOnnxTtsAdapterTest` | 6 | TTS adapter, Ol Chiki tokens (`ᱚ`/`ᱡ`) |
| ml | `IndicProcessorPortTest` | 4 | Digit/script normalisation incl. 0x1C50–0x1C59 |
| ml | `MicAuditTest` | 3 | Mic/audit path |
| ml | `WarmUpHonestyTest` | 2 | Warm-up honesty (no fake timings) |
| ml | `AsrLayoutRegressionTest`, `BpeCodecCacheTest` | 1+1 | Layout, BPE cache |
| core | `QuizEngineTest` | 5 | Quiz scoring |
| core | `LessonFilterTest` | 4 | Lesson filtering |
| app | `NavRouteTest` | 6 | Navigation routes |
| app | `RomanizeTest` | 4 | Ol Chiki romanization |
| app | `WorksheetContentTest` | 3 | Worksheet templates |
| app | `NavDestTest`, `LiveTimerTest`, `AudioPipelineTest`, `EngineSwapTest`, `ActiveLanguageTest`, `LiveConversationStoreTest` | 2–3 ea | Nav, timers, pipeline, language default (`sat_Olck`) |
| ml (python) | `ml/pipeline/tests/test_pipeline.py`, `ml/translation/mundari/tests/`, `ml/tts/tests/test_tts.py`, `ml/benchmarks/tests/test_harness.py` | 10 (`ml:test`) | Pipeline parity, harness guards |

**Device/instrumented tests (`androidTest/`, need emulator or tablet):**

| File | Tests | What it covers |
|------|-------|----------------|
| `TranslationEngineTest` | 5 | WiFi-OFF lesson translate → Ol Chiki regex `[\u1C50-\u1C7F]`, ≤500 ms `Vachak-MT` log check, not DEV-FIXTURE |
| `BothAdaptersTest` | 9 | sat output is Ol Chiki (no loop), mun output is NOT Ol Chiki, TTS honesty per-lang |
| `OfflineHarnessTest` | 3 | Airplane-mode harness |

**Benchmarks (Python, offline):** `benchmarks/translation_benchmark.py` (10 FLN fixtures, MT slice MB + p50/p95), `asr_benchmark.py` (WER/CER proxy), `tts_benchmark.py` (first-audio latency), `run_benchmark.py` (E2E T0→T4), `conversion_benchmark.py` (size/pack math), `ct2_migration_benchmark.py`. Results append to `docs/benchmarks/BENCHMARK_REPORT.md` (currently PENDING-until-measured on target 2 GB/Android 9 device; dev-host numbers are marked proxy, never Android).

**Demo acceptance checklist (AGENTS.md):** app opens → lesson loads → WiFi OFF → translate lesson → play Santali audio → generate worksheet → flashcards → push-to-talk Hindi → recognized text → Santali text → audio → <3 s latency shown → diagnostics. No hidden internet.

## How to run (./gradlew tasks, python benchmarks, device verify steps)

```bash
# Android — JVM unit tests (fast, no device)
cd android && ./gradlew :ml:testDebugUnitTest :core:testDebugUnitTest :app:testDebugUnitTest
# Single module: ./gradlew :ml:testDebugUnitTest --tests "com.vachak.ml.*"

# Android — build + lint
cd android && ./gradlew assembleDebug

# Android — instrumented (needs emulator/tablet, WiFi OFF for offline tests)
cd android && ./gradlew :app:connectedDebugAndroidTest

# Python — ML pipeline tests
python -m pytest ml/pipeline/tests ml/benchmarks/tests ml/translation/mundari/tests ml/tts/tests -q

# Python — benchmarks (offline; dev-host numbers are PROXY, not Android claims)
python benchmarks/translation_benchmark.py
python benchmarks/asr_benchmark.py
python benchmarks/tts_benchmark.py
python benchmarks/run_benchmark.py
```

Device verify steps: install `assembleDebug` APK on 2 GB/Android 9 tablet → enable airplane mode → run lesson translate → `adb logcat -s Vachak-MT Vachak-ASR Vachak-TTS Vachak-Latency Vachak-Pack` → confirm Ol Chiki output, stage budgets (ASR ≤1 s, MT ≤0.5 s, TTS ≤1 s, total <3 s) → fill `docs/benchmarks/BENCHMARK_REPORT.md` row.

## Coverage gaps (ASR/TTS on-device, pack installer adversarial tests, eval-split separation IN22, latency <3s proof)

1. **No measured <3 s proof on target.** All latency cells are PENDING or dev-proxy; `BENCHMARK_REPORT.md` hard rule forbids reporting dev numbers as Android. Need a captured `adb logcat Vachak-Latency` run on the 2 GB tablet.
2. **ASR/TTS on-device quality unmeasured.** WER ≤15 %, CER ≤5 %, MOS ≥3.5, intelligibility ≥95 % have no device measurements (proxy-only rows).
3. **Pack installer adversarial tests missing.** `PackInstaller.kt` has zip-slip/canonical-path/size/free-space guards but no unit tests feeding malicious zips (traversal entries, oversized entries, sha256 mismatch, >800 MB, low-space).
4. **Eval-split separation not enforced by test.** IN22-Gen/Conv must never train — but no test asserts zero IN22 overlap with `datasets/`/fine-tune corpora. A CI check (hash/sentence overlap scan) is missing.
5. **Cold start / RAM / offline-audit gaps.** No automated test for ≤5 s cold start, ≤500 MB app+models on device, or manifest/network-call audit (`INTERNET` permission, `HttpURLConnection`/`OkHttp` scan) — currently manual checklist rows.

## Open-source readiness tests to add (secret scan, license check, exported-component audit, pack zip-slip/size tests, PII/consent checks)

1. **Secret scan (CI):** gitleaks/trufflehog over repo + staged `local.properties`, API keys, keystore passwords; assert `local.properties` stays untracked.
2. **License check (CI):** verify `THIRD_PARTY_NOTICES.md` + `VOICE_CONSENT.md` coverage for every entry in `docs/MODEL_AND_DATA_PROVENANCE.md`; fail on new `*.onnx`/`*.bin`/`*.pt` without a provenance row; flag Piper/GPL-3.0 and espeak-ng-data exclusion.
3. **Exported-component audit (unit):** parse `AndroidManifest.xml` — exactly one `exported=true` launcher activity expected; everything else `exported=false`; fail on new exported service/receiver without justification.
4. **Pack installer adversarial suite (JVM):** zip-slip (`../`), absolute paths, symlink entries, entry-too-large, sha256 mismatch, manifest-blank, models-empty, >800 MB pack, insufficient-space — each must yield `EngineResult.Err` with the right `EngineError`, never a crash or overwrite outside the pack dir.
5. **PII/consent + log-hygiene checks:** test that `VachakLogger` ring buffer caps at 200 lines, drops non-`Vachak-` tags, and never stores raw PCM; test voice-sample fixtures carry consent metadata per `VOICE_CONSENT.md`; curriculum Extremely-Sensitive: assert no `MACHINE_TRANSLATED` content is marked approved pedagogy.
