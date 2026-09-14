# AGENTS.md — Vachak SIH26042

Offline Android tablet app (Hindi → Santali Ol Chiki, default) for Hindi-medium teachers. Santali adapter is live; Mundari is Phase-2 phrasebook (17k deterministic lookup) pending neural merge. Fully offline after install; Android 9+ (minSdk 28), 2 GB RAM, ~500 MB budget.

## Hard rules

- No runtime network calls. `android/app/src/main/AndroidManifest.xml` declares **no INTERNET permission** — any network call is a bug. Downloads (`scripts/fetch_android_models.sh`, HF) are build-time only, and that script still fetches **dev-fixture** models (whisper-tiny ASR, zh TTS), not production Hindi ASR / Santali VITS.
- Sequential inference only: VAD → ASR → MT → TTS (`shared/orchestrator/`, `numThreads=1` in `VadStream.kt`, `IndicConformerAsrAdapter.kt`, `SherpaOnnxTtsAdapter.kt`). Never parallel (RAM).
- Curriculum Santali (`curriculum/lessons/sat_lessons.json`, AUTHOR-DRAFT, human-reviewed, precomputed) is **never** produced by on-device MT at runtime. Never ship `MACHINE_TRANSLATED` / synthetic `datasets/hin_sat` as approved pedagogy — SME sign-off required.
- Worksheets are template-based (PIL rendering). Flashcards are prebuilt PNGs. Never AI-generate either at runtime.
- `IN22-Gen/Conv` is **eval-only, never train**. FLORES-tagged rows in `material/` must never back score claims — eval is own held-out splits + `goldverified_dev` only.
- `datasets/hin_mun` (Karya **BY-NC-SA-FS 1.0, non-commercial**) is **quarantined, never bundled** (`datasets/_quarantine/`, not in `packages/packs/`). TTS training data has **no consent on file — prototype-only** (see `VOICE_CONSENT.md`) until consent filed or retrained on CC BY 4.0 corpora.
- License: Piper successor / espeak-ng are GPL-3.0 — training/dev-machine only, **never in APK** (APK runtime is sherpa-onnx Apache-2.0 + ORT MIT). Record repo + model + dataset + voice-consent provenance in `THIRD_PARTY_NOTICES.md` + `VOICE_CONSENT.md` + `docs/provenance/` before merging external code/model. Model license ≠ repo license.

## Architecture (actual layout, not aspirational)

```
android/{app,core,ml,content,sync}/  # Gradle modules (see android/settings.gradle.kts); UI depends only on EngineProvider
shared/orchestrator/ + shared/schemas/  # VAD/ASR/NMT/TTS contracts + sequential state machine (stdlib-only, mirrors Kotlin)
offline/model_registry/ modelpacks/     # pack install/checksum/rollback; language packs override bundled assets, no APK rebuild
backend/api/          # POST /translate, stdlib http.server, zero deps; TRANSLATION_BACKEND=satfinal default (flagship hin↔sat bidi)
backend/sync/         # teacher-correction queue; transport via offline pack installer, never auto-trains
curriculum/ worksheet/ flashcard/ localization/  # content DB + generators + l10n
ml/{pipeline,translation,tts,finetune,benchmarks}/  # adapters, training/export, harness
scripts/ packages/packs/  # build_content_pack.py + build_modelpack.py + pack.py; demo via demo_flow.py
```

- Engine swap point is `android/app/src/main/java/com/vachak/engine/EngineProvider.kt`: `EngineProvider.real(context)` = production (ASR NEMO CTC 134M sherpa layout → ONNX INT8 Santali MT → VITS TTS; model files fetched build-time via `scripts/fetch_android_models.sh`, absent from repo). `Mock*` is for isolated unit tests only. Adapters implement `TranslationEngine`/`ASREngine`/`TTSEngine` — swap needs no UI change.
- On-device MT bundle is **~314–357 MB INT8** (3-graph encoder/decoder/decoder_with_past, opset17), **over the old 180 MB budget** — variance accepted, see `docs/phases/P1-size-variance.md`. TTS slice ~110 MB. Diagnostics shows measured APK + filesDir + DB bytes (no estimates); every conversation item shows measured `ASR x • MT y • TTS z • Total` via `LastPipelineRun` — never mock timings. Model failure must surface `[ASR:MODEL] <cause>`, never "no speech detected".
- Ol Chiki validation: `[\u1C50-\u1C7F]`; TTS ships lexicon (space not in charset) with `dataDir=""` (no espeak data in APK). Fonts bundled offline (`noto_sans_ol_chiki`, `noto_sans_devanagari`, Lexend).

## Commands (verified)

```bash
# Python suites — use /usr/bin/python3, run from repo root:
/usr/bin/python3 -m unittest discover -s shared/orchestrator/tests
/usr/bin/python3 -m unittest discover -s backend/api/tests
/usr/bin/python3 -m unittest discover -s curriculum/tests
/usr/bin/python3 -m unittest discover -s ml/benchmarks/tests
/usr/bin/python3 -m unittest discover -s shared/schemas/tests
/usr/bin/python3 -m unittest discover -s offline/model_registry/tests
bash scripts/run_offline_tests.sh   # schemas + registry + offline harness (bash, NOT python3)
# Single test:
/usr/bin/python3 -m unittest shared.orchestrator.tests.test_orchestrator -v

cd backend/api && /usr/bin/python3 app.py   # POST /translate, TRANSLATION_BACKEND=satfinal default

cd android && ./gradlew assembleDebug                              # needs SDK (see local.properties) + JDK 17
./gradlew :ml:testDebugUnitTest :app:testDebugUnitTest              # JVM unit tests
```

## Android gotchas

- `:ml` uses `compileOnly(files("libs/sherpa-onnx-1.13.0.aar"))` (AGP can't bundle local AAR in a library); `:app` adds it as `implementation(files("../ml/libs/sherpa-onnx-1.13.0.aar"))`. Both set `packaging.jniLibs.pickFirsts += "**/libonnxruntime.so"`. JDK 17, compileSdk/targetSdk 35.
- ABIs: `arm64-v8a` (device) + `x86_64` (emulator). `android/ml/build.gradle.kts` packaging `excludes` Mundari trees (`modelpacks/stripped_mt_merged`, `modelpacks/mundari_adapter`, `modelpacks/mundari_phrasebook`) from APK — missing assets must surface `EngineResult.Err`, never crash. Santali path untouched.
- Release enables minify + shrinkResources; debug does not. `GIT_SHA` is baked into BuildConfig for on-tablet provenance.
- Demo bar: WiFi OFF → lesson loads → translate → Santali audio → worksheet → flashcards → push-to-talk Hindi → recognized text → Santali → audio → measured <3 s total → diagnostics.
