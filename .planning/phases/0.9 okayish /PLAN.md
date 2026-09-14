# Phase 4 — Pipeline Verification, Repo Trimming & Android Optimization

## Objective

Verify the full voice pipeline end-to-end, trim the repo of non-APK artifacts without affecting the APK output, optimize for general Android (120Hz, screen densities, etc.), handle edge cases, and meet the 500MB storage budget.

## Scope

### 1. Pipeline Verification (voice in → voice out)
- Verify ASR → MT → TTS sequential flow with real latency measurements
- Verify Ol Chiki validation at each stage
- Verify no network calls at any stage
- Verify less than 3s total latency on target device

### 2. Repo Trimming (safe deletions, no APK impact)
Safe to delete (not in APK build path):
- IndicTrans2/ (6.5GB cloned repo, training only)
- sherpa-onnx/ (45MB cloned repo, build only)
- indictrans2-onnx-export/ (21MB, training only)
- ml/finetune/ (trained LoRA adapters, not shipped)
- adapter_sat_bidi/ (199MB, not shipped)
- ct2_sat_bidi_int8/ (318MB, not shipped)
- merged_sat_bidi/ (629MB, not shipped)
- adapter_model.safetensors (100MB at root, not shipped)
- modelpacks/quipus/ (1.2GB, not in APK)
- modelpacks/piper-hi-base/ (61MB, interim only)
- modelpacks/mundari_adapter/ (not in Santali-only build)
- modelpacks/santali_adapter/ (not in Santali-only build)
- santali_pdfs/ (1.8GB, downloaded PDFs)
- santali_organized/ (1GB, organized PDFs)
- packages/ (1.6GB, rebuildable via build_pack.py)
- raw/ (3.3GB, investigate first)
- colab/ (292KB, not needed for build)
- backend/ (272KB, not in APK)
- offline/ (100KB, test harness)
- lightning_bundle_tts.zip (220KB, already extracted)
- datasets/hin_mun/ (3.8MB, Mundari not shipped)
- material/ (7.4MB, corpora not in APK)

Keep (needed for build or shipped):
- android/ (the app)
- ml/ (training pipelines, referenced by build)
- models/ (shipped ONNX models)
- modelpacks/stripped_mt/, modelpacks/stripped_mt_merged/ (MT path)
- modelpacks/quipus-sample/ (TTS path)
- curriculum/ (shipped content)
- datasets/hin_sat/ (shipped corpus)
- docs/ (documentation)
- scripts/ (build scripts)
- benchmarks/ (benchmark scripts)
- shared/ (orchestrator schemas)
- localization/ (language config)
- flashcard/, worksheet/ (shipped assets)
- AGENTS.md, README.md, process.md, THIRD_PARTY_NOTICES.md, VOICE_CONSENT.md

### 3. Android Optimization
- Enable 120Hz support via android.maxAspectRatio and window refresh rate settings
- Strip unused densities (keep mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- Enable ProGuard full mode for release (already enabled in build.gradle.kts)
- Add abiSplit for arm64-v8a only in release flavor
- Remove compose.ui.tooling.preview from release build
- Check cupertino dependency usage in production UI
- Verify font fallback for Ol Chiki (Noto Sans Ol Chiki already shipped)
- Add android:resizeable="false" to prevent unwanted resizing
- Add android:configChanges to handle density changes (already present)

### 4. Edge Cases
- Storage full: graceful degradation, pack install failure handling with user message
- Screen size/density: ensure all layouts adapt (configChanges already handles density)
- Ol Chiki font: fallback to system font if Noto Sans Ol Chiki missing from device
- Long sentences: 256-token truncation (already implemented in IndicTrans2Adapter.kt)
- Empty/malformed audio: VAD gating rejects silence, error handling in StreamingAsrSession
- Model load failure: fallback to text-only mode (already in SherpaOnnxTtsAdapter.warmUpIfNeeded)
- Low memory: sequential execution with ReentrantLock, numThreads=1 (already implemented)
- Pack switch: adapters reload from new pack path (already in PackManager)

### 5. Storage Budget
Current state:
- APK debug: 540MB
- Pack: 347MB
- Total: 887MB (over 500MB budget)

Budget breakdown:
- App APK: 40-70MB target
- Hindi ASR: 30-80MB (whisper-tiny 134MB, needs optimization)
- MT (IndicTrans2): 100-180MB (currently 357MB INT8)
- Santali TTS: 20-80MB (currently 109MB)
- Tokenizers/runtime: 20-50MB
- Curriculum content: 10-30MB
- Flashcard images: 20-50MB
- Safety margin: 30-50MB

Strategy to meet budget:
- MT: ship as int8+zip (target 180MB), or use pruned vocab variant
- TTS: quantize to int8 (target 60-80MB)
- ASR: keep whisper-tiny as-is (134MB, within 30-80MB budget with variance doc)
- Pack: compress with zip level 9, remove redundant files
- Release APK: enable minify + shrinkResources

## Success Criteria

1. Pipeline verified: ASR to MT to TTS less than 3s sequential, no network, Ol Chiki validated
2. Repo trimmed: at least 50 percent reduction in non-APK disk usage
3. APK optimized: release build less than 200MB
4. Storage budget met: APK plus pack less than or equal to 500MB
5. Edge cases handled: storage full, font missing, long input, model failure
6. Build still passes: ./gradlew assembleDebug green