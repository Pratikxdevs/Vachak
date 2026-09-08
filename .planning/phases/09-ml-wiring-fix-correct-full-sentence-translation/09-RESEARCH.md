# Phase 09 — ML Wiring Fix: Research

**Phase:** 09 — ML Wiring Fix — Correct Full-Sentence Translation
**Date:** 2026-08-31
**Status:** Research complete (mapper + wiring audit)
**Source:** GSD phase-researcher + pattern-mapper (whole codebase scan), live device test

## Problem
ML model is bundled (ONNX INT8 357M in `android/app/src/main/assets/vachak_models/mt`) and `EngineProvider.real` is wired, but LiveScreen always outputs garbage / same output for every Hindi sentence. Python baseline `indictrans2-onnx-export/src/translate.py` on same bundle produces varied correct Santali for `एक`→`ᱢᱤᱫᱴᱟᱝ`, `पानी`→`ᱫᱟᱜ` etc., but Kotlin adapter produced constant/repetitive. Latency deferred; correctness required for full sentences.

## Codebase Map (ML wiring)

| Layer | File | Role |
|-------|------|------|
| UI input | `android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt:63` | `manualHindi` debounce 600ms + mic `AudioRecord(16000)` → `translateAndAppend` → `engine.translation.translate(hi, sat_Olck)` |
| Preview | `LiveScreen.kt:445` surface `Live · Santali` | shows `livePreview` from same adapter |
| Engine | `android/app/src/main/java/com/vachak/engine/EngineProvider.kt:48` | `real(context)` → `IndicTrans2Adapter` |
| Adapter | `android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt:22` | `TranslationEngine` `hin_Deva→sat_Olck`, ORT `encoder/decoder/decoder_with_past`, `ReentrantLock` sequential |
| Assets | `android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt:29` | `prepare(context,"mt")` → `filesDir/vachak_models/mt` |
| Processor | `android/ml/src/main/java/com/vachak/ml/IndicProcessorPort.kt:28` | `preprocessBatch` NFKC + lang tags + `postprocessBatch` detokenize, queue-based placeholders |
| Bundle | `android/app/src/main/assets/vachak_models/mt/*` | `encoder_model.onnx (812K+115M .data)`, `decoder*.onnx`, `tokenizer_src/tgt.json (23M)`, `tokenizer_meta.json` (src 122706/tgt 122672), `generation_config.json` (decoder_start 2/eos 2) |
| Python ref | `indictrans2-onnx-export/src/translate.py:141` + `it2_onnx_wrappers.py:62` | Gold standard for parity |

## Root Causes (verified via python vs kotlin diff + device logcat `Vachak-MT`)

1. **`encode():132` voided** — Kotlin did `split(" ").map{vocab[it]?:vocab["▁$it"]?:unk}`. `tokenizer_src.json` is BPE `vocab 130526` `merges 245k` `Metaspace ▁ prepend always` + `TemplateProcessing </s>`. Whitespace split maps 90% Hindi to `unk=3` → encoder sees `[3,3,3]` constant. Python uses `Tokenizer.from_file(...).encode(prefixed).ids` BPE. Verified: `नमस्ते` should be `[8,29925,41881,2]` but Kotlin produced `[3,3,3,2]`.

2. **`runEncoder→greedyDecode` voided** — `runEncoder:352` returned `float[1][seq][512]` value but `greedyDecode:332` checked `if (encOut is OnnxTensor) else zeros[1,seq,512]`. Since value is `Array<Array<FloatArray>>`, always zeros → input-independent hidden states → same logits for every sentence. Python feeds `encoder_hidden_states: enc_out` real.

3. **Past KV voided** — `buildPastTensors:447` allocated `FloatBuffer.allocate` zeros `[1,8,1,64]` every step, ignored `result[1..]` present KV. Real `translate.py:211` `_past_feed(past_outputs)` preserves seq-growing shapes `[1,8,seq,64]`. Kotlin lost autoregressive state → repetitive hallucination.

4. **SherpaAssets marker shitty** — `SherpaAssets.kt:42` `mt` case fell to `else -> tokens.txt` but bundle has no `tokens.txt` (only `encoder_model.onnx`+`tokenizer*.json`) → never cache-hit, copies every launch. Not correctness but waste.

5. **`batchDecode:154` + `argmax:516` shitty** — `join(" ").replace ▁` double spaces; `argmax` duplicate `if (Array<*>)` branch threw `ClassCastException` → fallback `eosId`. Also `flattenEncoderOutput` assumed hidden 512 correct but shape handling fragile.

6. **INT8 hallucination for short greetings** — Python `translate("नमस्ते")` → `ᱦᱚᱞᱮ…×80` repetitive (quantization). Curated `GOLD` in `ml/translation/scripts/build_corpus.py:28` has `नमस्ते→ᱡᱚᱦᱟᱨ` etc. Need curated short-circuit.

## Python Baseline Evidence (same bundle, `ml/export_venv` ORT)

```
एक -> ᱢᱤᱫᱴᱟᱝ
पानी -> ᱫᱟᱜ
मैं ठीक हूँ -> ᱤᱧ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ
नमस्ते -> hallucination repetitive (INT8) — curated to ᱡᱚᱦᱟᱨ
बच्चों, पाँच आम गिनो। -> ᱜᱤᱫᱽᱨᱟᱹᱠᱚ, ᱢᱚᱬᱮ ᱜᱚᱴᱟᱝ …
```
Proves model can vary; Kotlin was the fault.

## Existing Patterns to Reuse

- **IndicProcessorPort** already correct (20 fixtures parity) — keep.
- **SherpaAssets** recursive copy + `PackManager.getActivePackFor` pack-aware reload pattern from Phase 5 — reuse for mt.
- **LatencyTracker + ReentrantLock sequential** from Phase 8 — keep but not gated now.
- **Noto Ol Chiki bundled** `android/app/src/main/res/font/noto_sans_ol_chiki.ttf` — ensures `Vachak-MT` OlChikiValidator `U+1C50–U+1C7F` visible.

## Decisions for Planning

- Fix is **3 plans, no latency gate**: (1) BPE+curation, (2) ONNX pipeline, (3) integration verify.
- Do **not** add new deps (keep pure Kotlin BPE, no `ai.djl` native) to stay within 500M and offline.
- Keep `ReentrantLock` sequential, `numThreads=1`.
- Curated map for 11 greetings/shorts only, rest via model.
- Verification: `python translate.py` parity for 6 fixtures + `ml:testDebugUnitTest` + manual Live typing+mics varied.

## Validation Architecture (Nyquist)

- Dimension 8: Plans must include validation tasks: `ml:testDebugUnitTest`, `benchmarks/translation_benchmark.py` on 10 FLN fixtures, and manual `adb logcat -s Vachak-MT` tokenize ids varied.
- Failure if `ONNX bundle missing` or `DEV-FIXTURE` in output.

## Risks

- BPE reimplementation must match `Tokenizer` exactly — risk of still mismatched ids. Mitigate with 6-fixture python vs kotlin ids log.
- Past KV shape growth — must preserve `t.info.shape` per tensor, not fixed `[1,8,1,64]`.
- INT8 hallucination remains for unseen shorts — curated map is stopgap; long-term needs FP32 or fine-tune.

## Recommended Plan Breakdown

- **09-01**: BPE tokenizer + curated map + vocab/merges loading (+ test)
- **09-02**: ONNX encoder/past KV + SherpaAssets mt marker + argmax/batchDecode fixes
- **09-03**: E2E pipeline trace + APK rebuild + Live verification (typing + mic full sentences distinct)

---
*Researcher: gsd-phase-researcher (sonnet) + mapper scan*
*Source: whole codebase + python parity run*
