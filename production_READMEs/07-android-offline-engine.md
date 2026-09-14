# 07 — Android & Offline Engine

## Modules (`android/settings.gradle.kts:16`)

`include(":app", ":core", ":ml", ":content", ":sync")` — UI depends only on `EngineProvider`.

| Module | Deps | Role |
|---|---|---|
| `:app` | `:core,:content,:sync,:ml` + sherpa AAR (implementation) + Hilt/Room/Compose | `EngineProvider`, screens, `SherpaAsr/Vad/TtsAdapter` wrappers |
| `:core` | core-ktx, coroutines | `EngineContracts.kt` (LatencyBudget 3000 ms), `ActiveLanguage.kt` (sat_Olck default), `MockEngines.kt` |
| `:ml` | `:core,:sync` + sherpa AAR (compileOnly) + onnxruntime-android 1.24.3 | adapters, `SherpaAssets.kt`, `LatencyTracker.kt`/`LastPipelineRun`, `Orchestrator.kt` |
| `:content` | `:core` + Room | `ContentEngine`, `vachak_content.db` |
| `:sync` | `:core` + Room | `PackManager.kt`, `PackInstaller.kt`, `PackEntity/Dao/Database` |

AAR split (AGP 8.9 workaround): `:ml` compileOnly, `:app` implementation
(`../ml/libs/sherpa-onnx-1.13.0.aar`); both `pickFirsts **/libonnxruntime.so`.
Toolchain: AGP 8.5.2, Kotlin 1.9.24, JDK 17, compileSdk/targetSdk 35, minSdk 28.
ABIs: `arm64-v8a` (device) + `x86_64` (emulator). Release minify+shrink; `GIT_SHA` in BuildConfig.

## EngineProvider — the swap point

`android/app/src/main/java/com/vachak/engine/EngineProvider.kt:22-79`:
- `mock()` = all `Mock*` (unit tests only).
- `real(context)` = `ContentEngine` (Room) + `ActiveLanguage` sync from packs +
  `AdapterTranslationEngine` (ONNX MT) + `SherpaAsrAdapter + SherpaVadDetector` +
  `SherpaTtsAdapter`. Swap needs no UI change.

## Packs: install / checksum / rollback (no APK rebuild)

- Python authority: `offline/model_registry/registry.py` (`install` requires
  `manifest.json`, per-file sha256, mismatch → `ModelRegistryError`; backup to
  `.rollback/<lang>-<ver>`; `validate`, `rollback`, `version_check`, `storage_used_bytes`).
  Schema: `manifest_schema.py` (`PackManifest`, `ModelEntry kind=asr|mt|tts`).
- Builders: `scripts/build_modelpack.py`, `scripts/build_content_pack.py`,
  `scripts/pack.py` → `packages/packs/sat_Olck-v*.vachakpack`.
- On-device: `PackManager.kt` (`filesDir/packs/<id>/`, active-pack resolution),
  `PackInstaller.kt` (SAF ContentResolver only, sha256, zip-slip sanitize, 800 MB cap),
  `SherpaAssets.kt` (bundled `assets/vachak_models/` → `filesDir/vachak_models/`,
  bypassed when pack has `model.onnx+tokens.txt`).
- Mundari trees excluded from APK (`android/ml/build.gradle.kts:41-48`);
  missing assets → `EngineResult.Err`, never crash. Santali path untouched.

## Diagnostics (measured, never estimated)

- `LatencyTracker.kt` + `LastPipelineRun` — `T0..T4`, `endToEndMs`, per-item display
  (`LiveComponents.kt:62-67`), budgets ASR≤1000/MT≤500/TTS≤1000/Total<3000.
- `DiagnosticsScreen.kt` — `LastPipelineRun.sample` else
  `"Not measured yet — run Live once … never mocked"`; measured bytes
  (APK `packageCodePath` + `filesDir/vachak_models` + packs + `vachak_content.db`)
  vs 500 MB; `GIT_SHA`, pack SHA, ASR fingerprint, `VachakLog` ring.

## Tests

- `scripts/run_offline_tests.sh` (bash): schemas contract + registry + offline harness
  (blocks `socket.socket/urlopen` — any network = fail).
- JVM: `./gradlew :ml:testDebugUnitTest :app:testDebugUnitTest`.
- Python: `/usr/bin/python3 -m unittest discover -s shared/orchestrator/tests`
  (also `backend/api`, `curriculum`, `ml/benchmarks`, `shared/schemas`, `offline/model_registry`).
