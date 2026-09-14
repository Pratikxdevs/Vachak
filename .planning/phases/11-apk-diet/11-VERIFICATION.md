# 11-VERIFICATION.md — APK diet execution report (2026-09-13)

## Result: release tablet APK = **434MB** (was 690MB debug / ~770MB projected)

Target <500MB: **MET. Nothing deleted from repo or code.**

## What changed (2 files edited, 0 files deleted)

1. `android/ml/build.gradle.kts` — `packaging.resources.excludes` for
   `modelpacks/stripped_mt_merged/*`, `modelpacks/mundari_adapter/*`,
   `modelpacks/mundari_phrasebook/*` (+ explicit small-file names).
   Effect: 197MB `model.bin` + 12MB vocabs + 28MB adapters + 3.8MB
   phrasebook no longer packaged. Repo files + UI untouched.
2. TTS assets reverted to HEAD shim (both `app` + `ml` mirrors) via
   `git checkout HEAD -- .../vachak_models/tts/` — because the 110MB
   Sep-12 "fine-tuned 150 epochs, 19 clips" voice is PROVEN BROKEN
   (its own `models/verify.log`: FAIL FAIL; sherpa output 0.13–0.84s at
   rms 0.005 = silence; durations collapsed = undertrained duration
   predictor; tokens.txt missing space U+0020). Saving: ~70MB.

## Fresh release interior (`app-tablet-release-unsigned.apk`, 434MB)

ASR NeMo 134MB (loads in ORT ✓) + MT INT8 ~322MB (encoder loads ✓) +
TTS shim 42MB (honest gate ✓) + tokenizers ~9MB compressed + dex ~20MB
(minified ✓) + native libs ~38MB (arm64-only ✓, no x86_64) + VAD/res.
Mundari traces left: 2 files, ~1.4KB (`adapter_config.json`, `README.md`).

## Pipeline verification (voice → voice)

- ASR: NeMo EncDecCTC graph loads in ORT CPU ✓ (note: vocab is
  Bengali-script BPE — Hindi WER still an open accuracy question, flagged
  in 11-CONTEXT, not a size issue).
- MT Santali: ONNX INT8 encoder loads ✓ (live path via `OnnxIndicTrans2Adapter`).
- MT Mundari: `model.bin` gone from APK → `isMergedReady()` false →
  phrasebook asset gone → `Err(MODEL_LOAD_FAILED)` honest error, no crash
  (try/catch verified in code). UI chips untouched.
- TTS: shim gate (`MatMul_VITS_Acoustic` 2-node graph detected) refuses
  native load → text-truth + honest `MODEL_NOT_LOADED`. No fake audio.
- Offline: `INTERNET` count in Manifest = 0 ✓.
- Latency: sequential discipline unchanged (no code touched).
- 120Hz: `enableHighRefreshRate()` + vsync-safe UI verified present,
  unchanged.

## Pre-existing worktree state (not mine, reported)

`modelpacks/stripped_mt_merged/model.bin`, vocabs, `corpus.tsv` were ALREADY
deleted from disk before this session (git shows D vs HEAD, same as the
`.planning` deletions). My excludes make the APK robust to their return.
The 110MB broken TTS was uncommitted worktree state (now reverted).

## Still open (11-02 candidates, NOT done)

- Real voice: Studio export from a converged checkpoint → `models/vits-sat.onnx`
  (must PASS `export_onnx.py --verify` first — the Sep-12 incident proves why).
  When it lands (~40-60MB), APK grows to ~470-490MB — still under budget.
- REJECTED 2026-09-13: `ai train/export/vits-sat.onnx` (md5-identical to the
  reverted broken asset; own verify.log FAIL FAIL; fresh sherpa test 0.12s @
  rms 0.0042 = silence; trained 19 clips, listen skipped per its did.md).
  Do NOT wire until a full-bulk/full-train export passes verify + human A/B.
- Optional: drop `libctranslate2.so` (2.3MB) + the 2 leftover JSON files
  once CT2 classes are confirmed unreferenced at runtime; tokenizer prune
  (~5MB compressed — likely not worth it); ASR swap (needs WER proof).
- `.git/` is 5.3GB — repo hygiene separate from APK work.
