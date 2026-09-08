# Vachak — Hindi→Santali (Ol Chiki) Journey Tracker

**Last updated:** 2026-09-01 13:30 IST — Phase 09 complete + 3 hotfixes (JDK / ORT / Gather) verified on `emulator-5554` (x86_64, API 34)  
**Goal (autonomous, SIH26042):** Offline Android tablet (Android 9+, 2 GB RAM, ~500 MB) app for Hindi-medium teachers → Santali Ol Chiki. ~90% classroom translation accuracy, no repetition loops, clean code, finetuned for FLN domain. Sequential pipeline `ASR → MT → TTS` <3 s, no runtime network.

---

## Journey — what happened

### 2026-08-29 — Phases 01-08 + 06 shipped (P1 MT, P2 TTS, P3 ASR, P4 curriculum, P5 pack, P8 Live, P6 budget)

- **P1 on-device MT** `indictrans2-indic-indic-dist-320M` (`ffb7582`) → ONNX opset17 3-graph `export_onnx_it2.py`: `encoder 115M` + `decoder_shared 194M` + `decoder_with_past` + `tokenizer_src/tgt.json 23M` each → **357 MB INT8 per_channel** `onnx.checker PASS`, `ORT Mobile 1.18.0`. `IndicProcessorPort.kt` 20 fixtures 100% parity, `IndicTrans2Adapter` greedy decode, `EngineProvider.real` wired, `no INTERNET`, `assembleDebug` green.
- **P2 TTS** Santali VITS char tokens `U+1C50–U+1C7F` (IndicVoices 19 779 + Nirantar 13 503 + Rasa ~850 + CommonVoice 533) → `SherpaOnnxTtsAdapter` 40 MB, `22050 Hz AudioTrack`, `VOICE_CONSENT.md`.
- **P3 ASR** whisper-tiny Hindi + Silero VAD 643K → `SherpaAsrAdapter`/`SherpaVadDetector` + `LatencyTracker` ≤1 s, `Load WAV → ASR` debug `assets/test/hindi_sample.wav`.
- **P4 curriculum** 8 FLN lessons + 8 NIPUN outcomes precomputed Ol Chiki, Room DB `ContentEngine`, 7 worksheet PDFs + 46 flashcard PNGs (~1.9 MB).
- **P5 pack** `packages/build_pack.py` → `sat_Olck-v0.1.0.vachakpack` 347 M zip (75 files, 497 M source, sha256+manifest), `PackInstaller` SAF + zip-slip sanitization, `PackManager.getActivePack*`.
- **P8 Live** `LiveScreen.kt` `AudioRecord(MIC,16000,MONO,PCM16)` + `RECORD_AUDIO` launcher + VAD-gated ASR → Hindi card → `sat_Olck` MT → 22 kHz TTS, sequential `ReentrantLock + isTranslating + numThreads=1`, `Vachak-*` logs.
- **P6 budget** `DiagnosticsScreen` + `run_benchmark.py` proxy `590 ms p50` (PENDING device), `BENCHMARK_REPORT.md`.

*Gap: this document still said “ONNX decoder BLOCKED” and listed PyTorch 640 MB fallback — that became stale the same night.*

### 2026-08-31 — Phase 09: the wiring was voided (the real blocker)

`09-RESEARCH.md` found 6 voided paths in `IndicTrans2Adapter.kt` — code compiled but never used the model:

| # | Voided | Fix |
|---|--------|-----|
| 1 | `encode()` whitespace `split` → `unk` | BPE `mergesRank 245 k` + Metaspace `▁` + `TemplateProcessing </s>` (`addedTokens` whitelist) |
| 2 | `runEncoder → greedyDecode` dummy `zeros [1,seq,512]` | Real `encoder_hidden_states [1,seq,512]` via `LongBuffer → enc.run → flattenEncoderOutput → FloatBuffer` |
| 3 | Past KV fixed zeros `[1,8,1,64]` | Shape-preserving `List<Pair<value,shape>>` growing `[1,8,seq,64]` in `buildPastTensorsFromValueShapes` |
| 4 | `SherpaAssets mt` marker `tokens.txt` (never present) | `encoder_model.onnx && tokenizer_src.json` |
| 5 | `batchDecode join(" ")` + weak `argmax` | `join("") → replace ▁→" "` + 3-branch `argmaxLogits` |
| 6 | INT8 hallucination `नमस्ते → repetitive` | `curatedMap` 11 GOLD `नमस्ते→ᱡᱚᱦᱟᱨ`, `पानी→ᱫᱟᱜ`, etc. |

Also: `LiveScreen` 600 ms debounce `sat_Olck` preview, `Vachak-MT` varied ids `[8,29925,34,2]` vs `[8,29925,1550,2]`, `ml:test 10/10`, `app:assembleDebug 582 M`, python parity `एक→ᱢᱤᱫᱴᱟᱝ` distinct. `STATE.md` → Phase 09 complete.

### 2026-09-01 — 3 hotfixes the tracker missed (this update)

**1. JDK — `gradle` wouldn’t start (`process.md` never mentioned build):**
- Host `openjdk 25.0.4` + `Kotlin 1.9.24` + `AGP 8.5.2` → `IllegalArgumentException: 25.0.4` at `JavaVersion.parse:305` (Kotlin DSL compiler). Fedora 44 only ships `25/26`.
- Fix: Temurin `17.0.13` at `/tmp/jdk17` (`JAVA_HOME=/tmp/jdk17 ./gradlew -p android assembleDebug` → `BUILD SUCCESSFUL 47 s`). Also fixed `LiveScreen.kt:174` `pcmRes.value` on `EngineResult.Err` (no `.value`) → `when Ok/Err` log.

**2. `libonnxruntime.so` — translation + mic both crashed (`process.md` still said ONNX blocked):**
- `adb logcat -s Vachak-MT`: `dlopen failed: cannot locate symbol "OrtGetApiBase" referenced by libonnxruntime4j_jni.so` and `libsherpa-onnx-jni.so`.
- Root: `sherpa-onnx-1.13.0.aar` bundles `libonnxruntime.so VERS_1.24.3` (25 M/31 M) while `onnxruntime-android:1.20.0` expects `VERS_1.20.0` (17 M/20 M). `pickFirsts **/libonnxruntime.so` kept sherpa’s → Java ORT failed; keeping onnxruntime’s → sherpa Vad failed (`libsherpa-onnx-jni.so U OrtGetApiBase@VERS_1.24.3` vs `VERS_1.20.0`).
- Fix: `android/ml/build.gradle.kts:37` bumped `onnxruntime-android 1.20.0 → 1.24.3` to match sherpa’s `1.24.3` (verified `nm -D` both `U OrtGetApiBase@VERS_1.24.3`), restored sherpa AAR (54 M), `clean assembleDebug` → APK `lib/arm64-v8a/libonnxruntime.so 25 M` now satisfies both. `Vachak-Native: Successfully loaded onnxruntime` + `ORT environment created` + `encoder/decoder/decoder_with_past loaded` + `Vad ready` + streaming `ASR_WINDOW ... → "पने" → "नहीं भाई हमारा प्रस"` with no crash.

**3. `Gather` — `Couldn't translate. [MT:ORT] idx=258 out of (-258,257)` (`process.md` never mentioned limits):**
- `config.json:36` `max_source_positions 256` but quantized weight is `[258,512]` (`encoder_model.onnx` initializer `Constant_5_output_0_quantized [258,512]`). Long ASR-committed Hindi (streaming `WINDOWED_STREAMING 48000` windows accumulate → `273` ids for 30× “मेरा नाम …” or multi-sentence ASR) → position `258` requested → Gather OOB.
- Fix: `IndicTrans2Adapter.kt:44` `maxSourcePositions` loaded from `config.json` (clamped to 258), `translate:116` truncates `ids.size > max` to `255` + EOS before `runEncoder`, `catch` maps `Gather out of data bounds` → `INVALID_INPUT Input too long (>256 tokens) — try shorter`. Verified: `273 → 255 encShape [1,255,512]` `decode: encoder done` no crash (was `Gather` crash before; now truncates and translates, though 255-token INT8 can still hallucinate repetitively — short FLN sentences `मेरा नाम क्या है → ᱤᱧᱟᱹᱜ ᱧᱩᱛᱩᱢ ᱪᱮᱫ?` 1448 ms and `आप कैसे हैं → ᱟᱢ ᱪᱮᱫ ᱞᱮᱠᱟ ᱾` 320 ms are clean).

All three verified on `emulator-5554` (`x86_64,arm64-v8a`, API 34) via `adb logcat -s Vachak-MT: D Vachak-ASR: D Vachak-VAD` and `unzip -l app-debug.apk`.

---

## Current status (what’s real now)

**APK:** `android/app/build/outputs/apk/debug/app-debug.apk` **540 M** (was 541 M before, 582 M during P9, 357 M MT + 140 M ASR + 39 M TTS + 23 M+23 M tokenizers + fonts; `output-metadata.json` + `no INTERNET` + `Noto Sans Ol Chiki 15K` + `Noto Devanagari` + `RECORD_AUDIO` only). `JAVA_HOME=/tmp/jdk17 ./gradlew -p android assembleDebug` `BUILD SUCCESSFUL` (`161 tasks`).

**MT:** ONNX **shipped** (not blocked). 3-graph `encoder 812K + 115M data` + `decoder 841K + 194M shared` + `decoder_with_past 707K` + `tokenizer_* 23M` → `du -sh 357M` `opset17` `onnx.checker PASS` `ORT Mobile 1.24.3` `pickFirsts`. `IndicTrans2Adapter.kt:645` BPE 245 k merges + curated 11 + real hidden states + shape-preserving past KV + `maxSourcePositions` truncation + `OlChikiRegex [\u1C50-\u1C7F]` + `ReentrantLock` sequential (`numThreads=1`). `LiveScreen.kt:669` debounce preview + `Vachak-MT/TTS/Latency` logs. Device test: `नमस्ते → ᱡᱚᱦᱟᱨ` curated, `मेरा नाम क्या है → ᱤᱧᱟᱹᱜ ᱧᱩᱛᱩᱢ ᱪᱮᱫ?` distinct, `tokenize [8,29925,34,2]` vs `[8,29925,1550,2]` not `[3,3,3]`.

**ASR/TTS:** `sherpa-onnx-1.13.0.aar` (54 M, `VERS_1.24.3`) + `onnxruntime-android 1.24.3` (`THIRD_PARTY_NOTICES` still says 1.18.0 — stale), `SherpaAssets.kt:98` recursive `copyTree` with markers `asr: tokens.txt+model.onnx` `vad: silero_vad.onnx` `mt: encoder_model.onnx && tokenizer_src.json`. ASR `whisper-tiny` 99–135 M + VAD 0.6 M `WINDOWED_STREAMING 48000` (3000 ms) `Short/32768` bridge, `AudioRecord(MIC,16000)` + `finish()`; TTS `vits-sat.onnx` 39 M `AudioTrack MODE_STREAM 22050`. Mic no longer crashes (was `Vad <clinit> OrtGetApiBase` until ORT bump).

**Finetune:** `ml/translation/scripts/infer_it2.py` greedy `num_beams=1 + repetition_penalty 1.2 + no_repeat_ngram 3` (removes `forced_bos_token_id=2` → `[2,2]` loop; `num_beams>1` deadlocks). LoRA adapters **trained but not shipped**: `it2_goldverified_lora` 453 pairs (408/45) `train_loss 2.54→0.65 dev 2.56→1.46` `BLEU 21.4→56.1 chrF 47.6→67.0`; `it2_goldnum_lora` 653 pairs (goldverified+numbers 225+boosters 20) `numeral 32/36 89%` `BLEU 64.7 chrF 70.5` on 45-pair dev. Production APK uses **base 320M INT8** (`COILD 20 603` reserved per `MODEL_AND_DATA_PROVENANCE`). Next: merge LoRA → re-export opset17 → re-quant `per_channel` → `onnx.checker` → update `P1-size-variance.md` (357 vs 180).

**Curriculum:** `curriculum/sat_lessons.json` 8 FLN + `outcomes/nipun.json` 8 NIPUN precomputed Ol Chiki, Room `ContentEngine`, 7 PDFs + 46 PNGs (~1.9 MB), `schemas/schema.sql`.

**Packs & sync:** `sat_Olck-v0.1.0.vachakpack` 347 M zip (75 files, 497 M source) `manifest.json` + `PackInstaller` SAF `sha256` + zip-slip sanitization, `PackManager.getActivePack*`, `ManagePacksScreen`.

**.planning:** `STATE.md` Phase 09 complete (64%, 7/9 phases, 11/17 plans), next **Phase 07 Demo Acceptance** (WiFi OFF → lesson → translate → Santali audio → worksheet → flashcards → Hindi ASR → Santali → <3 s diagnostics — pending device measure).

---

## Delivered (vs `process.md` stale claims)

| `process.md` said | Reality |
|---|---|
| “ONNX decode BLOCKED — decoder MatMul mismatch at layers.0/self_attn/MatMul_1, deploy PyTorch 640 M/4-bit 180 M” | **Unblocked 2026-08-29/31**: 3-graph exports, INT8 357 M `checker PASS`, decoder `MatMul` was voided code (zeros), not export bug. Deploy is ONNX, not PyTorch. Historical blocker archived. |
| `it2_goldnum_lora` as production adapter `BLEU 64.7` | Adapters exist (14 M each) but **not in APK**; production is base 320M. BLEU numbers are PyTorch dev eval, not on-device INT8 (on-device ~66–72% text parity, can hallucinate on 255-token trunc). |
| `train_lora_qlora.py` broken — use `finetune_simple.py` | Still true (`pad_without_fast_tokenizer_warning NameError`, beam deadlock, 4-bit `.to` crash). `finetune_simple.py` is canonical. |
| `sat→hi` weak | Still true (dist-320M family weak reverse). |
| Files `ml/models/it2_onnx_fp32` decoder BROKEN | Now `android/app/src/main/assets/vachak_models/mt/*` decoder **good** (verified varied outputs). |
| No APK / no limits | Now APK 540 M green, `max_source_positions 256` (weight 258) truncation + `Gather OOB → INVALID_INPUT` + BPE LRU 2000, `THIRD_PARTY_NOTICES` ORT version stale `1.18.0 → 1.24.3`. |

---

## Known limits & next

- **Input length:** `256` tokens max (config) / `258` weight rows. Streaming ASR can exceed (273 seen) → now truncates to 255+EOS; long truncations can hallucinate repetitive `76527↔89023…` — prefer short FLN sentences. Future: split long ASR into sentence chunks instead of head-truncate.
- **4-bit / INT8 quality:** Base INT8 has `नमस्ते` repetition → curated 11 fixes it; long 255-token INT8 still loops. Finetuned adapters not yet on-device — next is merge+re-export.
- **Benchmarks:** `benchmarks/translation_benchmark.py` 10 FLN fixtures `16.5 ms ref PASS` (host ORT), but `BENCHMARK_REPORT.md` `590 ms p50 proxy` is **not Android** — device `adb logcat -s Vachak-MT/Vachak-Latency` measure for <3 s badge is PENDING (`run_benchmark.py` still proxy).
- **JDK fragility:** Fedora 44 ships only 25/26; build needs 17 at `/tmp/jdk17` (ephemeral) → persist to `/opt/jdk17` or bump Kotlin/AGP for Java 25.
- **Demo acceptance:** Phase 07 (WiFi OFF E2E + `DiagnosticsScreen` T0→T4) not yet performed on 2 GB tablet.

---

## Files (current)

- `ml/translation/scripts/infer_it2.py` (PyTorch baseline, greedy+penalty, optional `adapter_dir` merge)
- `ml/translation/scripts/finetune_simple.py` (canonical LoRA, fp16 AMP, q/k/v/out) / `train_lora_qlora.py` (broken) / `eval_gv.py` / `num_eval.py` / `gen_numbers.py`
- `ml/translation/scripts/export_onnx_it2.py` (3-graph opset17, BATCH1 ENC64 DEC1, externalize >100M)
- `ml/finetune/it2_goldverified_lora/` + `it2_goldnum_lora/` (not shipped) / `ml/finetune/data/it2_goldverified*.tsv` / `numbers_hi_sat.tsv` / `it2_goldnum_train.tsv` (653) / `it2_num_eval.tsv` (36)
- `ml/models/it2_onnx_fp32/` (FP32 oracle) / `android/app/src/main/assets/vachak_models/mt/*` (INT8 357 M shipped) / `android/app/src/main/assets/vachak_models/{asr,tts,vad}/`
- `android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt:645` (BPE+ORT) / `SherpaAssets.kt:98` / `LiveScreen.kt:669` / `StreamingAsrSession.kt` / `IndicProcessorPort.kt`
- `android/{app,ml,core,content,sync}/build.gradle.kts` (`AGP 8.5.2 Kotlin 1.9.24 ORT 1.24.3`)
- `curriculum/{lessons/sat_lessons.json, outcomes/nipun.json, generators/worksheet_generator.py, flashcard/assets/46 PNG}`
- `packages/build_pack.py` + `sat_Olck-v0.1.0.vachakpack` / `docs/{architecture/ARCHITECTURE.md, benchmarks/BENCHMARK_REPORT.md, phases/P1-size-variance.md, MODEL_AND_DATA_PROVENANCE.md}` / `THIRD_PARTY_NOTICES.md` (fix ORT 1.24.3) / `VOICE_CONSENT.md`
- `.planning/{PROJECT.md, ROADMAP.md, STATE.md, phases/09-ml-wiring-fix-*}` / `verification-notes.md` / `android/app/build/outputs/apk/debug/app-debug.apk` (540 M)

---

## To verify

```bash
# MT PyTorch (21 sentences, no loops)
python ml/translation/scripts/infer_it2.py --inp hindi.txt --out santali.txt
python ml/translation/scripts/infer_it2.py --adapter_dir ml/finetune/it2_goldnum_lora --inp hindi.txt --out santali.txt

# Finetune (robust path)
python ml/translation/scripts/finetune_simple.py --tsv ml/finetune/data/it2_goldnum_train.tsv --dev_tsv ml/finetune/data/it2_goldverified_dev.tsv

# Android (needs JDK 17)
JAVA_HOME=/tmp/jdk17 ./gradlew -p android assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Vachak-MT:V Vachak-ASR:V Vachak-VAD:V Vachak-TTS:V Vachak-Latency:V Vachak-Native:V
# Expect: ORT environment created, encoder/decoder/decoder_with_past loaded, vocab 130526/245372,
# curated नमस्ते→ᱡᱚᱦᱟᱨ, truncate len 273→255, no Gather crash, Vad ready, ASR_WINDOW → "पने"
```

---

## Lessons

- Voided wiring (zeros instead of `encoder_hidden_states` / past KV) looked like an export bug — check the Kotlin adapter before blaming `torch.onnx.export`.
- `pickFirsts **/libonnxruntime.so` with mismatched `VERS_1.20.0` vs `VERS_1.24.3` silently dlopens the wrong `libonnxruntime.so` → both `OrtEnvironment` and `Vad` crash with `OrtGetApiBase`. Keep ORT at `1.24.3` for Sherpa `1.13.0`.
- `max_source_positions` is not just a config note — without truncation, streaming ASR’s 273-token Hindi hits `Gather` with `idx=258` and the whole pipeline shows “Couldn't translate”. Truncate at `encode` time.
- `num_beams>1` deadlocks this custom decoder, `forced_bos_token_id=2` gives `[2,2]` empty — greedy + `repetition_penalty 1.2` + `no_repeat_ngram 3` is the only loop-free path.

