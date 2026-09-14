# Stack

## Languages & versions
- **Kotlin 1.9.24** (JVM target 17, `kotlinOptions.jvmTarget=17`, Java 17 source/target) — all Android modules.
- **Java 17** toolchain (compileOptions 17); host JDK observed 25 but build targets 17.
- **C++17** for `:ml` JNI layer (`vachak_ct2_jni`, `CMAKE_CXX_STANDARD 17`).
- **Python 3.11** for ML training venvs (`ml/tts/.venv-tts`); system python 3.14 present but training pins 3.11 (torch 2.14 cu130, transformers 4.53.3).
- **Ol Chiki (U+1C50–U+1C7F)** + Devanagari validated at content/DB boundary, not a language but a hard charset contract.

## Frameworks
- **Jetpack Compose 1.6.8** (compiler extension 1.5.14) + **Material3 1.2.1**; `VachakTheme { MaterialTheme { CupertinoTheme } }`.
- **compose-cupertino 0.1.0-alpha04** (`io.github.alexzhirkevich:cupertino` + `cupertino-core`) — CupertinoScaffold/TopAppBar/NavigationBar/Button/Section; Apache-2.0.
- **Navigation Compose 2.7.7**, **Lifecycle 2.8.6** (runtime-compose, viewmodel-compose), **activity-compose 1.8.2**, **core-ktx 1.13.1**.
- **Hilt 2.51.1** (`hilt-android`, `hilt-navigation-compose 1.2.0`) for DI in `:app`.
- **Room 2.6.1** (runtime, ktx, compiler via KSP) — content DB + pack DB.
- **Kotlinx Coroutines 1.7.3** (`kotlinx-coroutines-android`) — suspend DB wrappers, asset prepopulate on Dispatchers.IO.
- **KSP 1.9.24-1.0.20** for Room/Hilt codegen.

## ML / runtime deps (ASR, MT, TTS, ONNX, sherpa-onnx versions)
- **sherpa-onnx 1.13.0 AAR** vendored at `android/ml/libs/sherpa-onnx-1.13.0.aar` (Apache-2.0). `:ml` uses `compileOnly` (avoids AGP local-AAR-in-library error); `:app` uses `implementation(files(...))` so APK ships `.so`+jar. Bundles `libsherpa-onnx-jni/c-api/cxx-api.so` + `libonnxruntime.so` (arm64-v8a + x86_64).
- **ONNX Runtime Mobile**: catalog pins `onnxruntime-android 1.24.3`, device wiring uses **1.18.0** (`OrtEnvironment/OrtSession`, 1 thread, CPU provider). `packaging.jniLibs.pickFirsts += libonnxruntime.so` in both `:ml` and `:app` resolves sherpa/ORT `.so` duplication.
- **MT — IndicTrans2 distilled 320M** (`ai4bharat/indictrans2-indic-indic-dist-320M`, MIT, HF rev `ffb7582`): exported via manual `torch.onnx.export` (opset17, NOT optimum) to 3-graph `encoder_model.onnx / decoder_model.onnx / decoder_with_past_model.onnx` + shared sidecar; INT8 `quantize_dynamic` (per_channel). On-device bundle `android/ml/src/main/assets/vachak_models/mt/` = **~359 MB** (encoder 115M `.data` + decoder_shared 194M `.data` + fast tokenizers 2×~24M + config). `onnx.checker` PASS, ORT-loadable. Driver: `IndicTrans2Adapter` / `OnnxIndicTrans2Adapter` (greedy decode, past-KV loop, Ol Chiki regex validator, `ReentrantLock` sequential, `supports()` hi→sat alias incl. mund/mun).
- **CTranslate2 v4.4.0** (CPU-only, FetchContent in `android/ml/src/main/cpp/CMakeLists.txt`; Ruy ON, CUDA/MKL/DNNL OFF, OpenMP COMP): lab-oracle INT8 (`models/indictrans2_ct2_int8/`, 312 MB, not shipped) + merged LoRA packs (`modelpacks/stripped_mt_merged/model.bin` 197 MB int8; `sat_bidi_ct2_int8` pending). JNI bridge `Ct2Jni` → `libvachak_ct2_jni.so` (arm64-v8a, API 28).
- **ASR**: DEV-FIXTURE **whisper-tiny multilingual** via sherpa (`assets/vachak_models/asr/`, Apache-2.0) — Hindi transcription, NOT final production ASR. Reference candidates: **IndicConformerASR** (MIT, `IndicConformerAsrAdapter` + `SherpaAsrAdapter`), Vosk/whisper.cpp (benchmark only). Capture 16 kHz (`VachakAudio.ASR_HZ`), Silero VAD (`silero_vad.onnx` in `vachak_models/vad/`), streaming 3s window / 3s commit, partial-decode cadence 700/1000 ms by core count.
- **TTS**: shipped **Santali VITS `model.onnx` 110 MB** (opset17, 22.05 kHz mono, 38 single-char Ol Chiki tokens ids 4–41 + 231-entry char-split `lexicon.txt`; `dataDir=""`, no espeak data shipped) via sherpa `OfflineTtsVitsModelConfig`. Trained with **Coqui TTS 0.27.5** (MPL-2.0, build-time only) from CC BY 4.0 corpora. Playback 22050 Hz (`VachakAudio.TTS_OUTPUT_HZ`) via AudioTrack. Interim Hindi-accented Piper voice (`piper-hi-base/hi-sat-interim.onnx`) is dev-machine only, NOT in APK.
- **Tokenizers**: fast `tokenizer_src/tgt.json` (~24M each, 130k vocab, `hin_Deva`/`sat_Olck` AddedTokens, `</s>` template) + `tokenizer_meta.json`; `IndicProcessorPort` (<200 lines, MIT port of IndicTransToolkit, no runtime import) + `SpApproxTokenizer`/`BpeCodecCache` on device.
- **Python ML libs** (training/export only, never shipped): `torch>=2.0` (2.14 cu130), `transformers 4.53.3` (lightning bundle pins `>=4.36,<4.46`), `peft==0.11.1`, `ctranslate2`, `onnx>=1.16`, `onnxruntime>=1.17`, `sentencepiece`, `sacrebleu>=2.3`, `accelerate`, `safetensors`, `indictranstoolkit`, `sherpa-onnx` (lazy import).

## Build & tooling (gradle, python, scripts)
- **Gradle 8.9** (wrapper), **AGP 8.5.2**, version catalog `android/gradle/libs.versions.toml`.
- Modules: `:app` (application, minSdk 28/target+compile 35, per-ABI `arm64-v8a|x86_64`, git-SHA BuildConfig, release minify+shrink) + `:core` `:content` `:sync` `:ml` (libraries, minSdk 28, compileSdk 35).
- `:ml` CMake 3.22.1 (`src/main/cpp/CMakeLists.txt`), NDK ABI arm64-v8a+x86_64, host fallback x86_64 for verification.
- **No INTERNET permission** in `AndroidManifest.xml` (only RECORD_AUDIO, READ_EXTERNAL_STORAGE, MODIFY_AUDIO_SETTINGS); FileProvider for offline worksheet PDFs.
- Python entry points: `ml/translation/scripts/` (export/quant/bench/diag/finetune), `ml/tts/{train,finetune_sat,export_onnx,evaluate,audible_check}.py`, `ml/pipeline/`, `ml/benchmarks/`, `benchmarks/{translation,asr,tts,conversion,ct2_migration}_benchmark.py`, `packages/build_pack.py`, `scripts/fetch_android_models.sh`, `scripts/patch_piper_for_sherpa.py`.

## Storage (Room/SQLite, filesDir/packs, assets)
- **Room `vachak_content.db` v2** (`AppDatabase`, `exportSchema=false`, `fallbackToDestructiveMigration`, debug `allowMainThreadQueries` + prod suspend wrappers on IO): `lessons | outcomes | worksheets | flashcards | activities | assessment_prompts` (FK cascade to lessons; precomputed Ol Chiki text; prepopulated from `curriculum/lessons/sat_lessons.json` + `outcomes/nipun.json` via `PrepopulateCallback`, validated U+1C50–U+1C7F).
- **Room `PackDatabase`** (`:sync/db`): installed-pack registry (`id=language-version`, path, size, manifestSha, isActive) + SharedPrefs `active_pack_id`.
- **filesDir layout**: `vachak_models/{asr,mt,tts,vad}/` (extracted via `SherpaAssets.prepare()` recursive copy + `.vachak_manifest` size-manifest + generation versioning, tts v2) and `packs/<language>-v<version>/` (SAF-installed `.vachakpack` zips, sha256-verified, zip-slip sanitized, 800 MB cap). Pack-aware TTS bypasses asset extraction (`resolvePackDir`).
- **Assets shipped**: `vachak_models/mt/` 359 MB + `vachak_models/tts/` 110 MB + `modelpacks/` (mundari trees excluded from APK via `packaging.resources.excludes`) + curriculum JSON + Noto fonts (`noto_sans_ol_chiki`, `noto_sans_devanagari`×4, `lexend` variable; OFL, ~1.1 MB) + worksheet PDFs (7) + flashcard PNGs (46).

## Notable version pins / vendored artifacts
- Pinned: Kotlin 1.9.24 / AGP 8.5.2 / Gradle 8.9 / Compose 1.6.8+compiler 1.5.14 / Room 2.6.1 / Hilt 2.51.1 / ORT android 1.18.0 (device) / sherpa-onnx AAR **1.13.0** / CTranslate2 **v4.4.0** / cupertino 0.1.0-alpha04 / robolectric 4.11.1 / peft 0.11.1 / Coqui TTS 0.27.5 / onnx opset17.
- Vendored (do NOT re-fetch at build): `android/ml/libs/sherpa-onnx-1.13.0.aar` (+`.bak`), MT INT8 bundle + tokenizers, TTS `model.onnx/tokens.txt/lexicon.txt`, `silero_vad.onnx`, whisper-tiny fixture, fonts, curriculum/flashcard/worksheet assets, `sat_Olck-v0.1.x.vachakpack`.
- Drift note: catalog `onnx = 1.24.3` vs device `1.18.0`; sherpa-internal ORT vs gradle ORT (resolved by pickFirsts); base MT 747 MB safetensors → 1226 MB FP32 oracle → 357–359 MB INT8 shipped (over 180 MB budget, variance doc `docs/phases/P1-size-variance.md`).
