# Phase 11 — APK diet (CONTEXT)

## Problem

Measured 2026-09-13: `app-tablet-debug.apk` = **690MB** (911MB uncompressed),
built Sep 11 against STALE assets. Current `android/app/.../assets` alone =
**~605MB** (asr 135 + mt 359 + tts 110 + vad 0.6), so a fresh build lands
≈770MB. Target: **<500MB** with zero quality loss. Gap ≈ **270MB+**.

## Verified live pipeline (voice → voice)

`LiveScreen` mic → `SherpaAsrAdapter` (NeMo CTC 134MB, sherpa) → VAD-gated →
`AdapterTranslationEngine` routes by target:
- **Santali** → `OnnxIndicTrans2Adapter` (ONNX INT8 359MB, live demo path)
- **Mundari** → merged CT2 `model.bin` 197MB if extracted (`isMergedReady()`),
  else phrasebook (3.8MB)
→ `SherpaTtsAdapter` → `SherpaOnnxTtsAdapter` (Coqui VITS 110MB, char tokens,
`dataDir=''`) → `AudioTrack` 22050Hz. Sequential, `numThreads=1`, T0→T4 <3s.
120Hz already handled (`MainActivity.enableHighRefreshRate`, vsync-safe UI).

## Measured APK interior (debug, Sep 11 build)

| Bytes | Path | Status |
|---|---|---|
| 202MB | `assets/vachak_models/mt/decoder_shared.onnx.data` | LIVE (Santali) |
| 197MB | `assets/modelpacks/stripped_mt_merged/model.bin` | LIVE (Mundari, after extract) |
| 140MB | `assets/vachak_models/asr/model.onnx` (NeMo CTC, vocab 5633) | LIVE |
| 120MB | `assets/vachak_models/mt/encoder_model.onnx.data` | LIVE (Santali) |
| 58MB | `classes*.dex` (debug, unminified) | shrinks on release |
| 42MB | `assets/vachak_models/tts/model.onnx` (STALE — assets now 110MB real Coqui VITS) | LIVE, over 80MB budget |
| 48MB | `tokenizer_src/tgt.json` (23MB × 2, full HF fast tokenizer) | LIVE but prunable |
| 38MB | native libs (onnxruntime 25.8 + ctranslate2 6.6 + sherpa 8.9 + misc) | LIVE |
| 28MB | `mundari/santali_adapter/adapter_model.safetensors` (14MB × 2) | **DEAD? (verify)** — extracted + size-logged only; no inference consumer found (ONNX graph can't take LoRA; CT2 base is pre-merged) |
| 12MB | CT2 vocabs (`source/target_vocabulary.json` 4.4MB × 2 + `dict.SRC.json` 3.4MB) | LIVE if CT2 path stays |
| 3.8MB | `mundari_phrasebook/corpus.tsv` | LIVE fallback |

## Budget math (release-build projection, to VERIFY in 11-01)

Base ≈ 605 (assets) + ~20 (dex release est.) + 38 (libs) + ~10 (res) ≈ 673MB.
Needed cuts ≈ 175MB+. Levers: adapters ~28 + tokenizer prune ~25 +
TTS int8 ~55 + dex shrink ~35 ≈ 143MB → ~530MB (still over without scope
call). **Santali-only option** (defer Mundari CT2 stack ~240MB) → ~360-440MB
— but that is a PRODUCT scope decision for the SIH demo, not taken here.

## Storage edge cases (beyond APK bytes)

- `SherpaAssets.prepare` + pack extraction COPIES assets to `filesDir`:
  device needs APK + extracted copy (~2× transiently). Verify what extracts
  on first launch vs lazily; measure `filesDir` after cold start.
- 8GB tablets: 700MB APK + 600MB filesDir + modelpack extract ≈ 1.5GB+.
- 2GB RAM: sequential discipline already enforced; verify no parallel
  regress (grep `async/launch` on engine paths).
- First-run latency: 600MB asset copy on every fresh install — measure it.

## Non-goals (user constraint)

NO deletions, NO behavior/quality changes in 11-01. Output is a VERIFIED
classification (deletable / protected / needs-decision) + measured numbers.
Execution is 11-02, only after explicit approval.
