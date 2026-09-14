# Structure

## Repo tree (top 2–3 levels, one-line purpose per dir)

```
Vachak/ (SIH26042 — offline Hindi→Santali Android app)
├── android/                  # Gradle project (:app :core :ml :content :sync)
├── ml/                       # Python training/export pipelines (translation/asr/tts)
├── datasets/                 # Parallel corpora (hin_sat/hin_mun, processed/, _quarantine/)
├── curriculum/               # FLN lessons/outcomes/schemas/seed/generators + tests
├── worksheet/                # Template worksheet engine (engine.py/pdf.py/templates/)
├── flashcard/                # Prebuilt flashcard engine (engine.py/assets/)
├── scripts/                  # Pack builders (build_modelpack/content_pack/pack.py), bundle verify, class builders
├── packages/                 # Built .vachakpack artifacts + packs/ staging dir
├── modelpacks/               # On-disk model dirs (stripped_mt, adapters, quipus, piper-hi-base)
├── models/                   # Heavy local models (vits-sat.onnx, tokens, lexicon — git-ignored)
├── benchmarks/               # On-device/harness benchmarks (asr/mt/tts/ct2-migration/conversion)
├── docs/                     # Architecture, phases, provenance, benchmarks, UI docs
├── backend/                  # Server-side dev helper (not shipped on device)
├── sherpa-onnx/              # Vendored runtime reference (AAR consumed via android/ml/libs)
├── colab/ sprint4h/ samples/ # Training notebooks, sprint uploads, sample data
├── localization/ shared/ offline/ # Locale resources, shared schemas, offline registry
├── THIRD_PARTY_NOTICES.md    # License ledger (repo/model/dataset/voice)
├── VOICE_CONSENT.md          # TTS voice provenance
└── AGENTS.md                 # Project hard rules (offline, sequential, budgets)
```

Vendored/training-weight dirs at root (`IndicTrans2/`, `indictrans2-onnx-export/`,
`ct2_sat_bidi_int8/`, `merged_sat_bidi/`, `adapter_sat_bidi/`, `lightning_bundle/`,
`final_curriculum/`, `santali_organized/`, `export (1)/`, `ai train/`, `.tmp*/`, `scratch_tmp/`)
are scratch/training outputs — excluded from source builds (see .gitignore section below).

## Android modules breakdown (files per module, entry points)

Gradle root: `android/settings.gradle.kts` → `include(":app", ":core", ":ml", ":content", ":sync")`.
Entry: `MainActivity` (launcher, `exported=true`) → `VachakApplication` → `VachakApp` NavHost.

- **`:app`** (`android/app/src/main/java/com/vachak/`) — UI + DI + orchestration:
  - `VachakApplication.kt`, `di/AppModule.kt` (Hilt providers, EngineProvider.real/mock).
  - `engine/EngineProvider.kt` — sole mock/real swap point.
  - `ml/adapter/{AudioPipeline,VadAdapter,SherpaAsrAdapter,SherpaTtsAdapter}.kt` — app-side adapters.
  - `ml/orchestrator/{Orchestrator,ClassroomState,ErrorPolicy}.kt` — classroom flow glue.
  - `ui/VachakApp.kt` + `ui/navigation/{NavDest,AdaptiveScaffold,PackSummary}.kt` — nav graph
    (`home|live|curriculum|learn/grade/{g}/chapter/{s}|tools/*|settings|packs|diagnostics`).
  - `ui/screens/`: `LiveScreen` + `LiveViewModel` (sequential pipeline owner) +
    `LiveConversationStore`, `HomeScreen`, `CurriculumScreen`, `GradeScreen`, `ChapterScreen`
    (+Worksheet/Deck), `LessonDetailScreen`, `ToolsScreen`, `SettingsScreen`, `DiagnosticsScreen`;
    `ManagePacksScreen.kt` (packs route); `pdf/WorksheetPdf|Content`, `audio/TtsPlayer`,
    `prefs/VachakPrefs`, `debug/{VachakLogger,DebugOverlay}`, `theme/*`, `components/*`.
  - `assets/{vachak_models/{asr,mt,tts,vad},curriculum/{lessons,outcomes},packs/,flashcard/,worksheet/}`.
  - Tests: `src/test` (NavDest/NavRoute, LiveTimer/ConversationStore, AudioPipeline, EngineSwap,
    WorksheetContent, Romanize) + `src/androidTest` (OfflineHarness, Translation, BothAdapters).
- **`:core`** (`android/core/src/main/java/com/vachak/engine/`) — pure-Kotlin contracts:
  `EngineContracts.kt` (all engine interfaces + `EngineResult`/`EngineError`/`LatencyBudget`),
  `ActiveLanguage.kt`, `LatencyTracker.kt`, `QuizEngine.kt`, `LessonFilter.kt`, `mock/MockEngines.kt`.
- **`:ml`** (`android/ml/src/main/java/com/vachak/ml/`) — real inference:
  `adapter/{AdapterTranslationEngine,OnnxIndicTrans2Adapter,IndicTrans2Adapter,Ct2Jni,
  SpApproxTokenizer,MundariPhrasebookEngine,mlinternal/{SherpaAsrAdapter,VadDetector}}`,
  `SherpaOnnxTtsAdapter`, `IndicConformerAsrAdapter`, `IndicProcessorPort`, `SherpaAssets`,
  `AsrModelFingerprint`, `VadStream`, `VachakAudio`, `AudioCapturer`, `StreamingAsrSession`,
  `TtsAdapter/AsrAdapter`, `LatencyTracker`, `ModelStatus`; `libs/sherpa-onnx-1.13.0.aar`;
  `orchestrator/` mirror; unit tests (`BpeCodecCache`, `WarmUpHonesty`, `MicAudit`, …).
- **`:content`** (`android/content/src/main/java/com/vachak/content/`) — `ContentEngine.kt` +
  `db/{AppDatabase,Entities,LessonDao}` (+ Outcome/Worksheet/Flashcard/Activity/Assessment DAOs).
- **`:sync`** (`android/sync/src/main/java/com/vachak/sync/`) — `PackInstaller.kt` (SAF install,
  sha256/zip-slip guards), `PackManager.kt` (active-pack resolution), `db/{PackDatabase,PackDao,
  PackEntity}`.

## ML pipeline layout

- `ml/translation/` — IndicTrans2 fine-tune path: `scripts/` (`prepare_indictrans_data`,
  `build_corpus`, `finetune_simple`, `train_lora_qlora`, `merge_lora_to_ct2`, `convert_ct2*`,
  `export_onnx_it2`, `quant_onnx_matmulnbits`, `infer_it2`, `it2_onnx_wrappers`, `bench_*`,
  `benchmark`, `eval_gv`, `diag*`, `verify_corpus`, `gen_*` synthetic/classroom data),
  `configs/`, `data/`, `checkpoints/`, `mundari/` (hi-unr pipeline: `pipeline`, `adapter`,
  `it2_ct2_baseline`, `evaluate` + tests).
- `ml/asr/` — `README.md` benchmark plan (IndicConformer vs Vosk vs whisper.cpp on 2 GB device).
- `ml/tts/` — Santali VITS: `train.py`, `finetune_sat.py`, `quipus_sat.py`, `export_onnx.py`,
  `evaluate.py`, `audible_check|verification.py`, `pipeline.py`, `adapter.py`, `dataset/
  (prepare_santali, transliterate, verify_pairs)`, `configs/`, `data/`, `runs/`, `tests/test_tts.py`,
  `requirements.txt`, `INTERIM_VOICE.md`.
- `ml/pipeline/` — Python reference mirror (`pipeline`, `asr_adapter`, `tts_adapter`,
  `vad_stream`, `latency` + `tests/test_pipeline.py`); `ml/finetune/` (LoRA workdirs);
  `ml/conversion/`, `ml/benchmarks/` (`harness`, `device_matrix`, `run`); `ml/models/`.
- `benchmarks/` (repo root) — device gates: `run_benchmark.py`, `translation_benchmark.py`,
  `asr_benchmark.py`, `tts_benchmark.py`, `ct2_migration_benchmark.py` (CT2-vs-ONNX PASS gate),
  `conversion_benchmark.py`.

## Content/curriculum assets layout

- `curriculum/` — `lessons/sat_lessons.json` (precomputed hi + Ol Chiki source of truth),
  `outcomes/nipun.json`, `lesson_map.py`, `data.py`, `class/` (per-class builders),
  `seed/` (seed_lessons.sql/json, lesson_package, provenance.json, templates/),
  `schemas/` (schema.sql, lesson_schema.py, entities.kt mirror), `generators/
  (worksheet_generator.py)`, `tests/`.
- `worksheet/` — `engine.py` (template select), `pdf.py` (offline PDF render),
  `templates/` (trace/fill_blank/match/comprehension per grade), `sample/`, `tests/`.
  Mirrored in APK assets + `filesDir` packs as `worksheet/templates/*.pdf`.
- `flashcard/` — `engine.py`, `assets/` (prebuilt PNGs, e.g. `number_*.png`), `sample/`, `tests/`.
  Mirrored as `flashcard/assets/*.png`.
- APK asset roots (`android/app/src/main/assets/`): `curriculum/{lessons/outcomes}`,
  `vachak_models/{asr,mt,tts,vad}`, `packs/` (bundled `.vachakpack`), `flashcard/`, `worksheet/`.
- Installed packs: `filesDir/packs/<lang>-v<ver>/{manifest.json,vachak_models/*,
  curriculum/class/{1..5}/manifest.json,…}` + `packages/*.vachakpack` build outputs
  (e.g. `sat_Olck-v0.1.0/0.1.1.vachakpack`); builders in `scripts/` (`build_content_pack.py`,
  `build_modelpack.py`, `pack.py`, `build_class{1..5}_content.py`, `verify_bundle.py`).

## Generated/vendored/large-binary dirs to exclude from open-source or git

From `.gitignore` + layout (never commit; keep local or distribute via packs):

- Model weights/binaries: `models/`, `*.onnx`, `*.bin`, `*.pt/.pth/.ckpt/.safetensors`,
  `merged_sat_bidi/`, `modelpacks/{quipus,quipus-sat-sido,quipus-sample,piper-hi-base/*.onnx}`,
  `ct2_sat_bidi_int8/`, `adapter_sat_bidi/`, `indictrans2-onnx-export/`, `IndicTrans2/`,
  `lightning_bundle*.zip`, `final_curriculum.zip`, `santali_organized.zip`, `sprint4h_upload.zip`.
- Packs: `*.vachakpack`, `packages/packs/`, `sherpa-onnx/build|/.build`,
  `android/ml/libs/*.aar(.bak)` (vendored sherpa-onnx AAR — track version, not binary, in OSS).
- Audio/media: `*.wav`, `*.mp3`, `*.m4a`, `ml/tts/dataset/wavs_22050/`, `ml/tts/runs/santali_vits_native/`.
- Build/scratch: `android/{.gradle,build,.idea,local.properties}`, `*/build/`, `.gradle/`, `out/`,
  `*.apk/*.aab/*.apks`, `android/ml/.cxx`, `*.hprof`, `.tmp*/`, `scratch_tmp/`, `raw/`, `REPS/`,
  `datasets/*.zip|*.tar.gz`, `__pycache__/`, `*.pyc`, `.venv*/`, `venv/`, `export_venv/`,
  `.idea/`, `.vscode/`, `.DS_Store`.

## Where to add new code (feature → module → screen → adapter guidance)

| Feature | Module | Where | Notes |
|---|---|---|---|
| New Live behavior (mic/flow/timing) | `:app` | `ui/screens/LiveViewModel.kt` (+ `LiveConversationStore.kt` for item state) | Keep `LiveScreen.kt` a pure renderer; pipeline stays under `pipelineMutex` + `limitedParallelism(1)`; log `Vachak-Latency`. |
| New screen/route | `:app` | `ui/navigation/NavDest.kt` + `ui/VachakApp.kt` NavHost + `ui/screens/<New>Screen.kt` | Reuse `AdaptiveScaffold`; bottom-nav highlight via `NavDest.fromRoute`; forward nav uses `navOnce`/`launchSingleTop`. |
| New engine capability | `:core` first | `engine/EngineContracts.kt` interface → `:app` `EngineProvider` wiring → `:ml`/`:content` impl | UI depends only on interfaces; add mock in `mock/MockEngines.kt` + `EngineSwapTest`. |
| MT path change | `:ml` | `adapter/AdapterTranslationEngine.kt` (routing) or `OnnxIndicTrans2Adapter`/`IndicTrans2Adapter` | Guards: `ReentrantLock`/`synchronized`, singleton handle, `numThreads=1`; CT2 changes need `ct2_migration_benchmark` PASS; never train on IN22. |
| ASR/TTS change | `:ml` (+ thin `:app` wrapper) | `IndicConformerAsrAdapter` / `SherpaOnnxTtsAdapter` (+ `SherpaAssets` for new asset dirs) | Pack-aware `resolveBaseDir`; shim/fixture honesty via `ModelStatus` + `isShim`; chunk TTS ≤150 chars. |
| Lesson/content change | `curriculum/` → `:content` | Author in `curriculum/lessons|outcomes|seed`, validate via `curriculum/tests`, prepopulate through `AppDatabase` + `ContentEngine` | Translations precomputed + Ol Chiki-validated; worksheets template-only; flashcards prebuilt assets. |
| Worksheet/flashcard template | `worksheet/` / `flashcard/` | `engine.py` + `templates/`/`assets/`, surfaced via `ToolsScreen`/chapter screens | No AI generation on device; PDFs via `WorksheetPdf`, share via `FileProvider`. |
| Pack format/install change | `:sync` | `PackInstaller.kt` (verify/extract) + `PackManager.kt` (resolution) + `ManagePacksScreen` | Keep SAF-only, sha256 + zip-slip guards, no `INTERNET`; update `scripts/pack.py` + `verify_bundle.py` in lockstep. |
| Training/export loop | `ml/` | `ml/translation/scripts/*`, `ml/tts/*`, `ml/asr/*` | Record licenses/provenance (`THIRD_PARTY_NOTICES.md`, `VOICE_CONSENT.md`); outputs ship via packs, not git. |
| Latency/budget proof | `:app` + `benchmarks/` | `DiagnosticsScreen.kt` + `LastPipelineRun` + root `benchmarks/*.py` | Measured-only timings; budgets ASR≤1s/MT≤0.5s/TTS≤1s/total<3s. |
