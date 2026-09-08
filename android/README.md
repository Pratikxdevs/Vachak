# Vachak — Android App (SIH26042)

Offline Hindi→Mundari (Ol Chiki) classroom translator for Hindi-medium teachers.
Fully offline after initial sync. Target: Android 9 (minSdk 28), 2GB RAM, ~500MB.

## Build

```bash
cd android
./gradlew assembleDebug        # gradle not installed in this env; files are conceptual
```

> gradle is not present in the build environment; the Gradle files define the
> **conceptual compile structure** only. Kotlin sources are valid and organized
> so a workstation build compiles.

## Module layout

| Module | Role |
|--------|------|
| `app` | App shell, `EngineProvider` injection point, UI (`ui/`), interface tests |
| `core` | Shared Android utilities |
| `ml`  | `adapter/` — sherpa-onnx reference ASR/TTS adapters (mock by default) + `AudioPipeline` |
| `content` | Room-backed curriculum store |
| `sync` | Offline package installer (NOT a network client) |

## Engine interfaces (PHASE 1)

All engines are defined as Kotlin interfaces in
`app/src/main/java/com/vachak/engine/EngineContracts.kt`:

`TranslationEngine, ASREngine, TTSEngine, CurriculumEngine, WorksheetEngine,
FlashcardEngine, LanguagePackManager, SyncManager, BenchmarkRunner`.

Mock implementations live in `engine/mock/MockEngines.kt`. The UI depends ONLY on
`EngineProvider` (interface-typed fields), so swapping a mock for the real
sherpa-onnx / IndicTrans2 adapter requires editing **only** `EngineProvider`
(`ui/MainActivity.kt`) — no UI changes. Proven by `EngineSwapTest.kt` and
`AudioPipelineTest.kt`.

## Offline guarantees

- `AndroidManifest.xml` declares **no INTERNET / ACCESS_NETWORK_STATE** permission.
- `SyncManager.is_network_allowed()` returns `false`.
- Sequential pipeline only: ASR → MT → TTS (never parallel, RAM budget).
- Latency budget: ASR ≤1s, MT ≤0.5s, TTS ≤1s, total <3s.

## Tests

- Unit (mock swap): `app/src/test/java/com/vachak/engine/EngineSwapTest.kt`
- Unit (audio path): `app/src/test/java/com/vachak/engine/AudioPipelineTest.kt`
- Instrumented offline harness: `app/src/androidTest/java/com/vachak/offline/OfflineHarnessTest.kt`

## Known limitations (skeleton)

- ASR/TTS/MT are **mock adapters**; no final Mundari model trained here.
- sherpa-onnx AAR is referenced but not bundled in this env.
- MT ONNX decoder export is blocked (see `process.md`); ship PyTorch until fixed.
- All sample data marked **DEV FIXTURE**; not real inference.
