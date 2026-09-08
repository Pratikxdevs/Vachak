# Phase 1: On-device MT Hin→Santali (Ol Chiki) — Research

**Researched:** 2026-08-29
**Domain:** On-device NMT (IndicTrans2 distilled 320M → ONNX Runtime Mobile, quantized int8, Android :ml)
**Confidence:** MEDIUM — export path proven on sister repo; MT budget feasibility is tight and requires validation

<user_constraints>
## User Constraints (from CONTEXT.md)

**CRITICAL:** Locked decisions from `.planning/phases/01-ondevice-mt/01-CONTEXT.md` — MUST be honored by planner.

### Locked Decisions
- **MT Runtime:** ONNX Runtime Mobile in `:ml` module; sequential pipeline ASR→MT→TTS — never parallel (RAM limit)
- **No runtime network calls:** Strictly offline after install; `android.permission.INTERNET` must NOT be added (audited)
- **Model Choice:** IndicTrans2 distilled 320M as base; fine-tune is out-of-scope for P1 if distilled already handles Hin→sat; otherwise single LoRA on COILD (20,603 pairs) + Education_v2 (never IN22 test)
- **Quantization:** Quantize to int8 ONNX; budget 100–180 MB; arm64-v8a first; verify 500 MB total stays green (AGENTS.md budget table)
- **Adapter Contract:** New `com.vachak.ml.adapter.IndicTrans2Adapter` implements app `TranslationEngine` (tokenize Hin → infer → decode Ol Chiki)
- **Wiring:** `EngineProvider.real(context)` points at real adapter; drop `MockTranslationEngine` (currently both `mock()` and `real()` return it — `android/app/src/main/java/com/vachak/engine/EngineProvider.kt:30,49`)
- **Assets:** Model lives in `assets/vachak_models/mt/` for P1 (migrates to language-pack path in P5); tokenizer + sentencepiece assets bundled; no download
- **Pipeline correctness:** `ml/translation/mundari/it2_ct2_baseline.py` (CTranslate2 int8, `नमस्ते→ନମସ୍କାରଂ`) proves the model; P1 proves the APK path. CT2 artifacts under `models/indictrans2_ct2_int8/` (312 MB) and `models/indictrans2_ct2_int8_pruned/` (223 MB) are lab-only — do not ship CT2
- **Prior ONNX export:** `indictrans2-onnx-export/` is reference only — do not assume working; manual `torch.onnx.export` with wrappers is required (Optimum unsupported — `EXPORT_ISSUES.md:1`)
- **License tracking:** IndicTrans2 MIT, COILD/Education_v2 CC BY 4.0, ONNX Runtime MIT, sherpa-onnx Apache-2.0 — record in `THIRD_PARTY_NOTICES.md` + `docs/MODEL_AND_DATA_PROVENANCE.md` before merge

### Claude's Discretion
- Exact quantization tool (`onnxruntime` `quantize_dynamic` vs `optimum` vs `onnxruntime.quantization`) and calibration set (dynamic per-channel vs static)
- Tokenizer handling (IndicTrans2 SPM model `model.SRC`/`model.TGT` + `dict.SRC/TGT.json` → fast `tokenizer_src/tgt.json` integration details inside `:ml`)
- Fallback if int8 degrades Ol Chiki quality below threshold (acceptable: fall back to fp16 with size mitigation, or per-layer mixed precision, or Q4F16 variant)

### Deferred Ideas (OUT OF SCOPE — do NOT plan)
- Fine-tuned Mundari voice / Santali VITS (P2) — not in P1
- Pack-based model distribution (P5) — P1 bundles directly in APK assets; do not build `packages/` builder or `sync/` installer
- IN22-Gen/Conv eval harness — benchmarking only, separate from P1 delivery
- Mundari/Ho language targets — Santali-only
</user_constraints>

<architectural_responsibility_map>
## Architectural Responsibility Map

Single-APK offline Android app with Python export pipeline. All P1 inference is on-device.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Hindi text normalization + SPM tokenization | Android :ml (Kotlin) / ONNX bundle | Python export pipeline | Must run offline on 2GB device; Kremlin: `IndicTransToolkit.IndicProcessor` pre/postprocess must be ported or bundled (cannot call server) |
| Seq2seq inference (Hin→Santali) | Android :ml — ONNX Runtime Mobile | — | AGENTS.md mandates ORT Mobile; sherpa-onnx AAR already proves native lib pattern |
| Model asset extraction & caching | Android :ml `SherpaAssets` | — | Existing `android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt:18` recursive copy pattern; entry passes `null` AssetManager because files are under `filesDir` |
| Engine swap / UI binding | Android :app `EngineProvider` + `EngineContracts.TranslationEngine` | — | Compose `LessonTranslatorScreen` only sees interfaces; swap happens in one file (`android/app/src/main/java/com/vachak/engine/EngineProvider.kt:48`) |
| Export / quantization (build-time) | Python pipeline (`indictrans2-onnx-export/` + `ml/translation/`) | — | Not shipped; produces `assets/vachak_models/mt/{encoder,decoder,decoder_with_past}.onnx[.data]` + tokenizer JSON |
| Latency measurement | Android LatencyTracker | `benchmarks/` | `android/ml/src/main/java/com/vachak/ml/LatencyTracker.kt:45` + `android/app/src/main/java/com/vachak/engine/LatencyTracker.kt` dual trackers — P1 must use one consistently and log `Vachak-*` |
| License / provenance | `THIRD_PARTY_NOTICES.md` + `docs/MODEL_AND_DATA_PROVENANCE.md` | — | Hard rule before merge |

Sequential pipeline is non-negotiable: one model resident at a time (RAM limit), `numThreads=1` in sherpa pattern (`android/ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt:35`, `IndicConformerAsrAdapter.kt:29`).
</architectural_responsibility_map>

<research_summary>
## Summary

P1 replaces `MockTranslationEngine` (`android/app/src/main/java/com/vachak/engine/mock/MockEngines.kt:12`, returns `[DEV-FIXTURE-mund]`) with a real IndicTrans2 indic→indic-dist-320M ONNX adapter in `:ml`. The Python CTranslate2 int8 baseline (`ml/translation/mundari/it2_ct2_baseline.py:54`) is verified: it loads `ai4bharat/indictrans2-indic-indic-dist-320M` (MIT) via `AutoTokenizer(trust_remote_code=True)` + `IndicTransToolkit.IndicProcessor` + `ctranslate2.Translator` and produces Santali text. The APK path is NOT yet proven — that is P1.

The standard export is **manual `torch.onnx.export`** with three graphs (`encoder_model.onnx`, `decoder_model.onnx`, `decoder_with_past_model.onnx`) matching the naklitechie I/O layout, plus fast tokenizers built from `model.SRC`/`model.TGT` via `SpmConverter` + dict ID remap. The sister repo `indictrans2-onnx-export/` validates this at 100% token-exact parity on 1100 fixtures at fp32 (`indictrans2-onnx-export/README.md:192`), with known size optimizations (shared decoder sidecar, ORT graph fusion).

**Key risk:** The indic→indic 320M distilled model does NOT share tied weights (`decoder.embed_tokens.weight` ≠ `lm_head.weight`; `indictrans2-onnx-export/src/01_export_encoder_decoder.py:205`). Therefore the ~500 MB tied-weight dedup that makes en→indic 200M small does NOT apply. Published int8 bundle after all optimizations is still **~371 MB** (`indictrans2-onnx-export/README.md:212`) — **2× the 100–180 MB budget** in `AGENTS.md:60`. The CT2 int8 pruned checkpoint is 223 MB (`models/indictrans2_ct2_int8_pruned/`), still over. P1 must either (a) accept a budget variance with justification, (b) pursue additional compression (Q4F16, per-layer mixed precision, vocab pruning, ORT Mobile slim build), or (c) switch to a slimmer base (not recommended — tested language coverage).

**Primary recommendation:** Reuse `indictrans2-onnx-export/src/01_export_encoder_decoder.py` + `src/onnx_bundle_optimize.py` + `src/04_quantize_int8.py` as the export pipeline for `ai4bharat/indictrans2-indic-indic-dist-320M` targeting `assets/vachak_models/mt/`; add ORT Mobile (`onnxruntime-android`) to `:ml`; build `IndicTrans2Adapter` that mirrors `it2_inference.py:greedy_decode_onnx` using ORT Java API + bundled fast tokenizers and `IndicProcessor` logic; measure ≤0.5 s on arm64 device; if int8 still >180 MB, plan a documented fallback to a dual-artifact strategy (full int8 for WiFi sync, Q4/slim for APK) or request ROADMAP budget relief — do not silently overshoot.
</research_summary>

<standard_stack>
## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `microsoft/onnxruntime` (ORT Mobile, `onnxruntime-android`) | 1.17–1.20 (align with `indictrans2-onnx-export` 1.27 for export; Mobile AAR for device) | On-device seq2seq inference | `AGENTS.md:27`, `PROJECT.md:49` mandate; MIT; only runtime that satisfies offline + arm64-v8a + <0.5 s budget and is already proven via included `libonnxruntime.so` inside `sherpa-onnx-1.13.0.aar` |
| `ai4bharat/indictrans2-indic-indic-dist-320M` | distilled 320M (HuggingFace) | Hin→Santali NMT base | MIT; per `REQUIREMENTS.md:MT-02` and `docs/PHASES.md:35`; handles `sat_Olck` (Ol Chiki) Flores code; verified via CT2 baseline |
| `IndicTransToolkit` (`IndicProcessor`) | 1.1.1 | Hindi normalization + lang-tag pre/postprocess | Required for correctness; `ml/translation/mundari/it2_ct2_baseline.py:84,106,121` shows pre/postprocess is mandatory — raw SPM fails |
| `HuggingFace tokenizers` (Rust) `tokenizer_src/tgt.json` | 0.22.2 | Fast SPM encode/decode on device | Built via `SpmConverter` + dict ID remap (`indictrans2-onnx-export/src/02_build_fast_tokenizers.py`, `EXPORT_ISSUES.md:109`); validation must be 100% vs slow HF tokenizer |
| `onnxruntime.quantization` (`quantize_dynamic`) | 1.17+ | INT8 weight quantization | `indictrans2-onnx-export/src/04_quantize_int8.py:51` uses `QuantType.QInt8, per_channel=True`; only path that hit ~371 MB INT8 for indic-indic |
| `onnxruntime.transformers.float16` | 1.17+ | FP16 conversion fallback | `indictrans2-onnx-export/quantization_issues.md:33` — must use `force_fp16_initializers=True` + clear `value_info` to avoid Add bias type error |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `onnx` + `onnxruntime.transformers.optimizer` | 1.22.0 / 1.27.0 | Graph fusion (1,662 vs 3,696 nodes) + externalization + shared decoder sidecar | Always post-export; `indictrans2-onnx-export/src/onnx_bundle_optimize.py:246` |
| `ctranslate2` | 4.x | Lab oracle (Python) | Keep as benchmark harness only; **do NOT ship**; `ml/translation/README.md:13` marks `models/indictrans2_ct2_int8` as reference |
| `sentencepiece` | 0.2.1 | SPM model `model.SRC`/`model.TGT` | Build-time only; runtime uses fast tokenizer JSON |
| `huggingface_hub` | 0.36.2 | Artifact fetch | Build-time |
| `k2-fsa/sherpa-onnx` AAR | 1.13.0 (vendored) | Pattern reference for AssetManager=null, Thread=1, recursive copy | Already in `android/ml/libs/sherpa-onnx-1.13.0.aar`; reuse its `SherpaAssets.prepare` and `OfflineTts(null, config)` pattern (`android/ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt:38`) |
| `sherpa-onnx` Silero VAD | same AAR | Not P1, but shows sequential pipeline wiring | Reference for <3 s budget enforcement |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ORT Mobile 3-graph seq2seq | `ctranslate2` INT8 on Android (JNI) | CT2 is 312 MB/223 MB pruned and faster, but adds native dep not in AGENTS.md, doubles toolchain, and still >180 MB; AGENTS.md explicitly requires ONNX Runtime Mobile |
| Manual `torch.onnx.export` | HuggingFace `optimum` (`ORTModelForSeq2SeqLM`) | Optimum fails on IndicTrans custom arch (`ValueError: custom IndicTrans`) — documented in `indictrans2-onnx-export/EXPORT_ISSUES.md:17` |
| Dynamic INT8 (per-channel) | Static INT8 with calibration, or `onnxconverter-common` FP16, or Q4F16 | Dynamic INT8 best latency/quality (1.47× speedup, 72% token match on indic-indic — `indictrans2-onnx-export/README.md:212`); static needs cal set; `onnxconverter-common` leaves bias in FP32 and breaks Add nodes (`quantization_issues.md:7`); Q4F16 ~493 MB and worse parity (46%) on 320M |
| Fast tokenizer JSON + bundled SPM | Ship raw `model.SRC` + `sentencepiece` native lib | Fast tokenizer is pure JSON + Rust `tokenizers` crate; smaller, faster, no extra JNI; must do dict ID remap or vocab collapses to `eng_Latn` (`ml/translation/README.md:18`) |
| On-device fine-tune | Export base + LoRA adapter (`it2_goldverified_lora` per `docs/MODEL_AND_DATA_PROVENANCE.md:19`) | Fine-tune out-of-scope for P1; if distilled already handles Hin→sat per CONTEXT.md, ship base only; otherwise single LoRA adds artifact complexity (need LoRA merge at export) |

**Build-time install (export env):**
```bash
# UseIndicTrans2's export_venv or indictrans2-onnx-export's uv
cd indictrans2-onnx-export && uv sync              # pulls torch 2.12, onnx 1.22, onnxruntime 1.27, transformers 4.57
# or reuse Vachak's ML venv:
pip install torch onnx onnxruntime onnxscript transformers indictranstoolkit tokenizers sentencepiece sacremoses huggingface-hub

# Android :ml — add ORT Mobile
# android/ml/build.gradle.kts:
#   implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
```

</standard_stack>

<architecture_patterns>
## Architecture Patterns

### System Architecture Diagram

P1 data flow (offline, sequential — second leg only):

```
[ LessonTranslatorScreen — Compose ]                [ EngineProvider.real(context) ]
        |                                                     |
        | 1. User taps "Translate lesson"                     |
        |    hiText = "नमस्ते।" / lesson.sourceTextHi  --->  TranslationEngine.translate(text, LanguagePair("hi","sat"))
        |                                                     |
        |               +------------------- IndicTrans2Adapter ( :ml ) -------------------+
        |               |                                                                  |
        |               | 2. Preprocess  IndicProcessor.preprocess_batch([text],           |
        |               |                  src_lang="hin_Deva", tgt_lang="sat_Olck")       |
        |               |    + clear placeholder queue                                     |
        |               |                                                                  |
        |               | 3. Encode      tokenizer_src.json  (SpmConverter + dict remap)   |
        |               |    clamp IDs < src_dict_size else unk_id=3;                     |
        |               |    pad to max_len, build attention_mask                           |
        |               |                                                                  |
        |               | 4. Infer  ORT Mobile — 3 sessions lazy-loaded from              |
        |               |    assets/vachak_models/mt/ via SherpaAssets.prepare():         |
        |               |    a) encoder_model.onnx  : input_ids, attention_mask            |
        |               |                                  → last_hidden_state (enc_out)   |
        |               |    b) decoder_model.onnx  : decoder_start_id + enc_out +         |
        |               |                              encoder_attention_mask               |
        |               |                                  → logits + present.* (KV)        |
        |               |    c) decoder_with_past loop (steps 2..MAX_NEW_TOKENS=128):      |
        |               |       input_ids=argmax(logits) + past_key_values.* +              |
        |               |       encoder_attention_mask (+ dummy enc hidden zeros)           |
        |               |                                  → logits + updated present.*     |
        |               |    stop on eos_id==2; greedy argmax; numThreads=1               |
        |               |                                                                  |
        |               | 5. Decode      slow_tok.as_target_tokenizer().batch_decode()     |
        |               |    clamp IDs < tgt_dict_size; skip_special_tokens                |
        |               |                                                                  |
        |               | 6. Postprocess IndicProcessor.postprocess_batch(decoded,         |
        |               |                  lang="sat_Olck") → Ol Chiki string              |
        |               |                                                                  |
        |               | 7. Validate    Ol Chiki range U+1C50–U+1C7F check;               |
        |               |                 log Vachak-MT DEBUG (input/tokenize/decode/      |
        |               |                 latency) mirroring it2_ct2_baseline.py            |
        |               +--------------------------------+---------------------------------+
        |                                                |
        | 8. EngineResult.Ok(satText)  <----------------+
        |    UI shows translatedText; LatencyTracker mark
        |    translate; total <500 ms on 2GB arm64
        |
[ LatencyTracker ] -- reports asrMs/mtMs/ttsMs/totalMs via Vachak-Latency log + benchmark harness
```

Sequential enforcement: `EngineProvider` never runs ASR/TTS in parallel with MT; adapter holds at most one ORT session active; `numThreads=1` like `SherpaOnnxTtsAdapter`/`IndicConformerAsrAdapter`. Entry point is `LessonTranslatorScreen` button (`android/app/src/main/java/com/vachak/ui/MainScreen.kt:60`); exit is Ol Chiki string displayed inline.

### Recommended Project Structure
```
android/
├── ml/                                         # P1 primary module
│   ├── build.gradle.kts                        # add onnxruntime-android + keep sherpa AAR
│   ├── libs/sherpa-onnx-1.13.0.aar             # keep (reference)
│   └── src/main/java/com/vachak/ml/
│       ├── adapter/
│       │   └── IndicTrans2Adapter.kt           # NEW — implements TranslationEngine
│       ├── SherpaAssets.kt                     # reuse recursive copy
│       ├── TtsAdapter.kt / AsrAdapter.kt       # interfaces (ref)
│       └── LatencyTracker.kt                   # reuse
│   └── src/main/assets/vachak_models/mt/       # P1: encoder/decoder*.onnx[.data] + tokenizer_*.json + config
├── app/
│   ├── src/main/java/com/vachak/engine/
│   │   ├── EngineContracts.kt                  # TranslationEngine interface (no change)
│   │   ├── EngineProvider.kt                   # wire real adapter
│   │   └── mock/MockEngines.kt                 # keep for tests
│   └── src/main/assets/vachak_models/          # symlink or copy from :ml assets
ml/translation/
└── mundari/it2_ct2_baseline.py                  # oracle — unchanged
indictrans2-onnx-export/                          # reference export pipeline — read-only
models/
├── indictrans2_bart/                            # HF snapshot (config, model.SRC/TGT, dict.*.json)
└── indictrans2_ct2_int8/                        # CT2 INT8 oracle (312 MB, not shipped)
```

File→responsibility: `IndicTrans2Adapter.kt` owns preprocess/encode/ORT loop/decode/postprocess/validate; `SherpaAssets.kt` owns extraction; `EngineProvider.kt` owns injection; `MainScreen.kt` owns trigger.

### Pattern 1: Three-Graph Seq2Seq Export with Lazy KV-Cache
**What:** Export encoder + decoder(first step) + decoder_with_past(steps 2+) as three ONNX graphs, as seq2seq cannot express optional `past_key_values` in one graph. The `decoder_with_past` receives a dummy `encoder_hidden_states` of zeros (batch × enc_seq_len × embed_dim) so the tracer compiles cross-attention (`EXPORT_ISSUES.md:15`).
**When to use:** Every IndicTrans2 ONNX export; matches naklitechie I/O layout.
**Example (from `indictrans2-onnx-export/src/it2_onnx_wrappers.py:96`, `src/01_export_encoder_decoder.py:47`):**
```python
# Encoder
wrapper = IndicTransEncoderWrapper(encoder).eval()
torch.onnx.export(wrapper, (input_ids, attention_mask), "encoder_model.onnx",
    input_names=["input_ids","attention_mask"], output_names=["last_hidden_state"],
    dynamic_axes={"input_ids":{0:"batch_size",1:"encoder_sequence_length"}},
    opset_version=17, dynamo=False)

# decoder_with_past must use seq-len-1 dummy past
past = [torch.randn(BATCH,8,1,64), torch.randn(BATCH,8,1,64),
        torch.randn(BATCH,8,ENC_SEQ,64), torch.randn(BATCH,8,ENC_SEQ,64)]*num_layers
torch.onnx.export(IndicTransDecoderWithPastWrapper(decoder, lm_head, num_layers),
    (input_ids, encoder_attention_mask, *past), "decoder_with_past_model.onnx",
    input_names=["input_ids","encoder_attention_mask", *past_input_names(num_layers)], ...)
```

### Pattern 2: On-Device Greedy Decode Loop (ORT Mobile Java)
**What:** Mirror `indictrans2-onnx-export/src/it2_inference.py:175` loop in Kotlin: single encoder call, then decoder step 1, then `decoder_with_past` loop with argmax, feeding `past_key_values.*` via `past_feed_from_outputs`. Use `decoder_start_token_id=2`, `eos_token_id=2` from `generation_config.json`; batch size 1 is sufficient for P1 (lesson is one string); measure with `SystemClock.elapsedRealtimeNanos()`.
**When to use:** Inside `IndicTrans2Adapter.translate()`.
**Example (Kotlin sketch, from `it2_inference.py:176–205` + `IndicConformerAsrAdapter.kt:61` threading):**
```kotlin
val encOut = env.run(encoderSession, mapOf(
    "input_ids" to inputIds, "attention_mask" to attnMask
))[0] // last_hidden_state
var past: Array<OnnxTensor>? = null
var decoderInput = longArrayOf(decoderStartId)
val outputIds = mutableListOf(decoderStartId)
repeat(MAX_NEW_TOKENS) { step ->
    val feeds = if (step==0) mapOf(
        "input_ids" to decoderInput, "encoder_hidden_states" to encOut,
        "encoder_attention_mask" to attnMask)
    else mapOf("input_ids" to decoderInput,
        "encoder_attention_mask" to attnMask, *past!!.withIndex().map { ... })
    val out = if (step==0) decoderSession.run(feeds) else decoderWithPastSession.run(feeds)
    val logits = out[0] as Array<Array<FloatArray>>
    val nextId = logits[0].last().indices.maxBy { logits[0].last()[it] }
    outputIds += nextId
    if (nextId == eosId) return outputIds
    decoderInput = longArrayOf(nextId.toLong())
    past = out.drop(1).toTypedArray()
}
```

### Pattern 3: Fast Tokenizer Dict Remap (Correctness Gate)
**What:** `SpmConverter` over `model.SRC`/`model.TGT` produces IDs in SentencePiece native order, NOT the model’s `dict.SRC.json` order. Must remap every vocab entry to dict ID, register Flores tags (`hin_Deva`, `sat_Olck`) as `added_tokens` (`single_word:true`, `special:false`), add `TemplateProcessing` for `</s>` (id 2), write `tokenizer_meta.json` with `src_dict_size`, `tgt_dict_size`, `unk_id:3`, and validate 8-sample inline 100% parity vs slow tokenizer (`EXPORT_ISSUES.md:10`, `ml/translation/README.md:18`).
**When to use:** Every export direction; failure collapses decoding to `eng_Latn`.
**Example (build-time, `indictrans2-onnx-export/src/02_build_fast_tokenizers.py`):**
```python
# 1. SpmConverter -> base tokenizer
# 2. for token, idx in vocab.items(): tok.vocab[token] = dict_json[token]
# 3. tokenizer.add_tokens([AddedToken("hin_Deva", single_word=True, special=False)])
# 4. tokenizer.post_processor = TemplateProcessing(single="$A </s>", pair="$A </s>")
# 5. meta = {"src_dict_size": len(src_dict), "tgt_dict_size": len(tgt_dict), "unk_id": 3}
# 6. clamp: id if id < meta["src_dict_size"] else unk_id
```

### Anti-Patterns to Avoid
- **Shipping CTranslate2 on Android:** Tempting because `model.bin` is already 312 MB and works, but violates AGENTS.md ORT mandate, requires extra JNI, and still exceeds budget. Fix the ONNX path.
- **Using `optimum` exporter for IndicTrans:** `ValueError: custom IndicTrans architecture` every time; the working path is manual `torch.onnx.export` with wrappers (`EXPORT_ISSUES.md:1`).
- **Raw `SentencePieceProcessor.EncodeAsPieces` for tokens:** Silently desyncs vocab → English output; must use HF tokenizer derived token strings (`ml/translation/README.md:17`) or correctly remapped fast tokenizer.
- **Passing `encoder_hidden_states=null` to `decoder_with_past`:** Strips cross-attention from ONNX graph; both export and runtime produce 100% false-positive parity on garbage (`EXPORT_ISSUES.md:15`). Use dummy zeros with full 4-element past cache.
- **Ignoring `encoder_attention_mask` in decoder graph:** Tracer elides it; add `logits + mask.sum()*0.0` zero-cost dependency (`EXPORT_ISSUES.md:6`, `it2_onnx_wrappers.py:134`).
- **Keeping all three ORT sessions resident:** Wastes ~800 MB RAM; after step 1, `decoder_model` is never reused — unload/free it (`ONNX_SIZE_OPTIMIZATION.md:14`). For P1 (MT-only, sequential), at most one leg is active anyway.
- **Blocking UI thread for inference:** MT must run on `Dispatchers.IO`/`Default` with coroutine scope (see `MainScreen.kt:60` `scope.launch`), logging `Vachak-MT` debug tags.
</architecture_patterns>

<dont_hand_roll>
## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Hindi normalization + lang tags | Regex + hand-rolled cleaner | `IndicTransToolkit.IndicProcessor(inference=True).preprocess_batch / postprocess_batch` | Handles placeholders, numbers, URLs, script-specific normalization; clearing `_placeholder_entity_maps.queue` between calls is required (`it2_inference.py:154`) |
| SPM tokenization | Manual `SentencePieceProcessor` call | Fast `tokenizer_src/tgt.json` via `SpmConverter` + dict remap, clamped by `tokenizer_meta.json` | Raw SPM IDs ≠ dict IDs; hand-rolled path hits 0% parity in `indictrans2-onnx-export` fixtures |
| Seq2seq autoregressive loop | Custom attention/KV logic | `it2_inference.py:greedy_decode_onnx` logic ported to ORT Java; `ORT SessionOptions` with `log_severity_level=3` | KV layout is `(batch, heads, seq, head_dim)` × 4 per layer; past `dec seq=1` vs `enc seq=len`; wrong shape drives beam collapse |
| ONNX optimization | Hand-edit graph | `onnx_bundle_optimize.py:optimize_export_bundle` + `finalize_bundle_layout` | Dedup / ORT fusion (3,696→1,662 nodes), externalize >100 MB, shared decoder sidecar — all validated; hand edits break `load_external_data` |
| Quantization | Manual cast via `onnxconverter-common.convert_float_to_float16` | `onnxruntime.transformers.float16.convert_float_to_float16(..., keep_io_types=True, force_fp16_initializers=True)` + clear `value_info` | `onnxconverter-common` leaves bias as FP32 → ORT Add type error (`quantization_issues.md:7`) |
| Beam search / sampling | Custom decoder | Greedy argmax only (beam=1) | Fixture parity harness is greedy; IndicTrans2 baseline uses `beam_size=4` in CT2 (`it2_ct2_baseline.py:115`) but ONNX validation is greedy argmax; keep greedy for P1 to match validated path |
| Asset extraction | `AssetManager.open` per file ad-hoc | `SherpaAssets.prepare(context, subdir)` (`android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt:22`) recursive `list()` + copy to `filesDir` + `null` AssetManager to native side | Handles nested `espeak-ng-data` correctly; pattern already ships with sherpa AAR; reimplementing risks missing sidecar files |
| Latency plumbing | `System.currentTimeMillis()` ad-hoc | `LatencyTracker` (`android/ml/.../LatencyTracker.kt`) + `android/app/.../LatencyTracker.kt` + `Log.d("Vachak-*")` | Provides both mark API and `Log.d` evidence required by `DEMO-01`/`PLAN-P1` |

**Key insight:** IndicTrans2’s value is in its custom tokenizer + wrapper contract; deviating from the `it2_onnx_wrappers.py` / `it2_inference.py` pair (tested across 1100 fixtures × 3 directions) is the fastest path to 0% parity. Reuse the three-graph layout and dict-remap tokenizer even if it feels redundant.
</dont_hand_roll>

<common_pitfalls>
## Common Pitfalls

### Pitfall 1: Budget Blow-Out — indic→indic 320M Cannot Fit 180 MB After INT8
**What goes wrong:** P1 delivers a correct adapter but the APK is 350–670 MB for the MT slice alone, failing `MT-02`/`PERF-02` (~500 MB total).
**Why it happens:** indic→indic has separate `embed_tokens` (512×~64K) and `lm_head` (512×~122K) matrices; the ~500 MB tied-weight dedup does not apply (`src/01_export_encoder_decoder.py:209`). Published FP32 after all optimizations is ~1.25 GB, INT8 371 MB, Q4F16 493 MB (`indictrans2-onnx-export/README.md:212`).
**How to avoid:** (a) Validate size on first export before wiring the adapter — run `du -sh scratch/indic-indic-onnx*` and `onnx_bundle_optimize.py`’s `total_mb` log (`src/01_export_encoder_decoder.py:233`). (b) Actively pursue slimmer options: `src/06_quantize_q4f16.py` with `block_size=16, accuracy_level=4`, per-layer mixed precision (`--exclude-nodes` for cross-attention/lm_head), or prune unused vocab rows. (c) Treat 223 MB pruned CT2 as an upper bound ref. (d) Escalate with evidence if 180 MB requires a different precision tier.
**Warning signs:** `finalize_bundle_layout` still writes `decoder_shared.onnx.data` > 200 MB; `model.bin` 310 MB already > budget.

### Pitfall 2: Vocab ID Desync — Decoding Collapses to English
**What goes wrong:** `model.generate()` returns gibberish or `eng_Latn` despite correct Hindi input.
**Why it happens:** Using raw SentencePiece IDs or un-remapped fast tokenizer; indicates fix collapses because `token_id >= dict_size` threshold is not clamped (`it2_inference.py:163,230`).
**How to avoid:** Reuse `tokenization_indictrans.py` + dict for build; validate `tokenizer_src.json` inline vs slow tokenizer at 100% before any ONNX test; clamp every encode with `unk_id=3`.
**Warning signs:** Token parity 0% but graph loads; decoded sample looks like `"do Be was [that 420"` (`EXPORT_ISSUES.md:14`).

### Pitfall 3: Past KV Shape / Cross-Attention Stripped
**What goes wrong:** First token plausible, tokens 2+ repetitive/drifted; 100% false-positive parity if both PyTorch and ONNX loops share the bug.
**Why it happens:** `decoder_with_past` traced with past `seq_len=2` (should be 1) or `encoder_hidden_states=None` (cross-attention not compiled) or missing dynamic axes on encoder dims (`EXPORT_ISSUES.md:4`, `15`).
**How to avoid:** Trace with `(batch,8,1,64)` past decoder KV and full encoder KV; set `dynamic_axes` for all `past_key_values.*` and `present.*` (`src/01_export_encoder_decoder.py:132`); pass dummy `encoder_hidden_states` zeros and keep `encoder_attention_mask` via `*0.0` trick.
**Warning signs:** Parity 100% on stale script but browser translation matrix shows drift; `decoder_with_past` much smaller than expected.

### Pitfall 4: FP16 Bias Type Mismatch on Load
**What goes wrong:** `ORT Fail: Type parameter (T) of Optype (Add) bound to different types (tensor(float16) and tensor(float))` when loading fp16 bundle.
**Why it happens:** `onnxconverter-common` leaves bias initializers as FP32; `graph.value_info` stale (`quantization_issues.md:1`).
**How to avoid:** Use `onnxruntime.transformers.float16.convert_float_to_float16(..., force_fp16_initializers=True, disable_shape_infer=True)` and `del model.graph.value_info[:]` before save.
**Warning signs:** Encoder loads, decoder fails at session create.

### Pitfall 5: Ol Chiki Validation Gap
**What goes wrong:** `translate()` returns `EngineResult.Ok` with Hindi or Odia script instead of Ol Chiki, but UI shows success.
**Why it happens:** Flores code mapping error (`hin_Deva→sat_Olck` correct per `it2_ct2_baseline.py:43`; but `LANGUAGE` vs `sat` vs `mund` confusion across `EngineProvider`’s `LanguagePair("hi","mund")` in `MainScreen.kt:64`). Also Santali historically rendered in multiple scripts; the CT2 baseline’s `ନମସ୍କାରଂ` is Odia, not Ol Chiki (U+1C50–U+1C7F is `ᱚᱞ ᱪᱤᱠᱤ`), so a blind string test can pass on wrong script.
**How to avoid:** Adapter must accept `LanguagePair("hi","sat")` / `sat_Olck` / `olck` (as baseline does) and map `"mund"`/`"sat"` explicitly; add `OlChikiValidator` (regex `[\u1C50-\u1C7F]`) and unit-test that fixtures contain ≥1 Ol Chiki codepoint; log script tag with `Vachak-MT`.
**Warning signs:** Acceptance test “shows Mundari text” still expects `[DEV-FIXTURE-mund]`; manual eye sees Devanagari.

### Pitfall 6: Offline Contract Regression
**What goes wrong:** APK passes translate but attempts `hf_hub_download` or includes `INTERNET` for ORT model fetch, or `assets/vachak_models/mt` missing at runtime → `FileNotFoundException` hidden by try/catch.
**Why it happens:** Build script fetches HF artifacts at runtime, or `SherpaAssets.prepare` called with wrong subdir string.
**How to avoid:** Grep `AndroidManifest.xml` for `INTERNET` (must be absent); run `network_request_audit()` harness; assert `prepare(context,"mt")` returns existing dir with `encoder_model.onnx`; test on airplane-mode device.
**Warning signs:** `Log.w("Vachak-Assets","asset copy failed")` in logcat; ticker shows mock fallback without error.
</common_pitfalls>

<code_examples>
## Code Examples

### 1. Export IndicTrans2 indic→indic 320M to ONNX (3 graphs) — build-time
```bash
# Source: indictrans2-onnx-export/src/01_export_encoder_decoder.py (verified 100% parity)
# Run with full network (downloads ai4bharat/indictrans2-indic-indic-dist-320M → 747 MB safetensors)
cd indictrans2-onnx-export
uv sync && source .venv/bin/activate
python src/01_export_encoder_decoder.py \
  --model ai4bharat/indictrans2-indic-indic-dist-320M \
  --output ../android/ml/src/main/assets/vachak_models/mt  # or scratch/indic-indic-onnx

# Post-export validates 100% parity before quantization
python src/03_validate_parity.py \
  --onnx-dir ../android/ml/src/main/assets/vachak_models/mt \
  --pytorch-model ai4bharat/indictrans2-indic-indic-dist-320M \
  --fixtures fixtures/indic-indic-golden.jsonl \
  --report fixtures/parity-report-indic-indic.json
# Pass criteria: token_pass_rate >= 99% (fixtured 100% on 1100 sentences)
```

### 2. INT8 Dynamic Quantization — build-time
```python
# Source: indictrans2-onnx-export/src/04_quantize_int8.py:32
import onnx
from onnxruntime.quantization import QuantType, quantize_dynamic

for name in ("encoder_model.onnx","decoder_model.onnx","decoder_with_past_model.onnx"):
    quantize_dynamic(
        model_input=str(src/name), model_output=str(dst/name),
        weight_type=QuantType.QInt8, per_channel=True,
        use_external_data_format=src.with_suffix(".onnx.data").exists(),
        extra_options={"DefaultTensorType": onnx.TensorProto.FLOAT},
    )
# finalize re-applies shared decoder sidecar — see onnx_bundle_optimize.finalize_bundle_layout
```

### 3. Python Baseline — reused tokenizer contract (oracle)
```python
# Source: ml/translation/mundari/it2_ct2_baseline.py:84,99,115
from IndicTransToolkit import IndicProcessor
from transformers import AutoTokenizer
import ctranslate2

tok = AutoTokenizer.from_pretrained("models/indictrans2_bart", trust_remote_code=True)
proc = IndicProcessor(inference=True)
translator = ctranslate2.Translator("models/indictrans2_ct2_int8", device="cpu")
batch = proc.preprocess_batch(["बच्चों, पाँच आम गिनो।"], src_lang="hin_Deva", tgt_lang="sat_Olck")
src_tokens = [tok.convert_ids_to_tokens(ids) for ids in tok(batch, return_tensors="pt", padding=True).input_ids.tolist()]
results = translator.translate_batch(src_tokens, max_decoding_length=256, beam_size=4)
out = proc.postprocess_batch(tok.batch_decode(
    [tok.convert_tokens_to_ids(h) for h in [r.hypotheses[0] for r in results]], skip_special_tokens=True),
    lang="sat_Olck")
# Adapter must achieve the same pre/postprocess, but with fast tokenizer + ORT greedy decode
```

### 4. :ml ORT Mobile Session Setup — Kotlin
```kotlin
// Source: indictrans2-onnx-export/src/it2_inference.py:100 + android/ml SherpaAsr pattern
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

val env = OrtEnvironment.getEnvironment()
val opts = OrtSession.SessionOptions().apply {
    setInterOpNumThreads(1); setIntraOpNumThreads(1)
    // addCPU(true) default; keep ORT Mobile minimal (no CUDA/NNAPI in P1)
}
val baseDir = SherpaAssets.prepare(context, "mt") // assets/vachak_models/mt -> filesDir
val enc = env.createSession("$baseDir/encoder_model.onnx", opts)
val dec = env.createSession("$baseDir/decoder_model.onnx", opts)
val decPast = env.createSession("$baseDir/decoder_with_past_model.onnx", opts)
val slowTok = // optional: load vocab for debug compare
val srcTok = Tokenizer.fromFile("$baseDir/tokenizer_src.json") // Rust tokenizers
val meta = JSONObject(File("$baseDir/tokenizer_meta.json").readText())
```

### 5. TranslationEngine Adapter — Kotlin Interface Compliance
```kotlin
// Source: android/app/src/main/java/com/vachak/engine/EngineContracts.kt:46
package com.vachak.ml.adapter
import com.vachak.engine.*
import android.content.Context

class IndicTrans2Adapter(private val context: Context) : TranslationEngine {
    override fun supports(pair: LanguagePair): Boolean =
        pair.source in setOf("hi","hin","hindi") &&
        pair.target in setOf("sat","sat_Olck","olck","mund","mun","mundari")

    override fun loadModel(packId: String): EngineResult<Unit> = EngineResult.Ok(Unit)
        // P1 assets are bundled; packId is no-op until P5 installer owns the path.

    override fun translate(text: String, pair: LanguagePair): EngineResult<String> {
        if (!supports(pair)) return EngineResult.Err(EngineError.UNSUPPORTED_LANGUAGE, "pair $pair unsupported")
        if (text.isBlank()) return EngineResult.Err(EngineError.INVALID_INPUT, "empty input")
        return try {
            val sat = greedyDecodeOnnx(text, srcLang="hin_Deva", tgtLang="sat_Olck")
            // Validate Ol Chiki script present (U+1C50–U+1C7F) or allow fallback with warning
            EngineResult.Ok(sat)
        } catch (e: Exception) {
            EngineResult.Err(EngineError.MODEL_LOAD_FAILED, e.message ?: "mt failed")
        }
    }
}
```

### 6. Wiring — EngineProvider Swap (single file change)
```kotlin
// Source: android/app/src/main/java/com/vachak/engine/EngineProvider.kt:48
fun real(context: Context): EngineProvider = EngineProvider(
    translation = IndicTrans2Adapter(context), // was MockTranslationEngine
    asr = SherpaAsrAdapter(context),           // already real
    tts = SherpaTtsAdapter(context),
    curriculum = MockCurriculumEngine,
    worksheet = MockWorksheetEngine,
    flashcard = MockFlashcardEngine,
    packs = MockLanguagePackManager,
    sync = MockSyncManager,
    benchmark = MockBenchmarkRunner
)
// Also add to THIRD_PARTY_NOTICES.md before merge (IndicTrans2 MIT, ONNX Runtime MIT)
```
</code_examples>

<sota_updates>
## State of the Art (2024-2025)

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `optimum` seq2seq ORT export | Manual `torch.onnx.export` with `it2_onnx_wrappers.py` | 2024–2025 (`EXPORT_ISSUES.md:17`) | IndicTrans custom `modeling_indictrans.py` requires wrappers; optimum path never validated parity |
| `onnxconverter-common` FP16 | `onnxruntime.transformers.float16.convert_float_to_float16` | 2025 (`quantization_issues.md:33`) | Bias FP32 bug fixed; required for fp16 fallback |
| `onnxsim` only | ORT `optimize_by_onnxruntime` graph fusion (`onnx_bundle_optimize.py:246`) | 2025 | 3,696→1,662 nodes on decoder; proto size + load time improved |
| Two independent decoder `.data` files | Shared `decoder_shared.onnx.data` via content-addressed offsets | 2025 (`onnx_bundle_optimize.py:360`) | ~550 MB saved on FP32 before quant |
| Single ORT session at startup | Lazy: encode → decode step 1 → decode_with_past loop | 2025 (`ONNX_SIZE_OPTIMIZATION.md:14`) | Decoder after step 1 never reused; frees ~800 MB peak RAM on WASM, relevant to 2 GB Android |
| `accuracy_level=2` for Q4F16 | `accuracy_level=4` (int32-accum) for CPU | 2025 (`quantization_issues.md:66`) | CPU MatMulNBits without FP16 kernels drifts 62%→75% parity with accuracy_level=4 |
| CTranslate2 as ship artifact | ONNX Runtime Mobile as ship runtime | AGENTS.md lock | CT2 is oracle only; ORT Mobile is the deliverable |

**New tools/patterns to consider:**
- **ORT Mobile prebuilt AAR (`onnxruntime-android`):** Ships slim CPU EP only (no training, ~20–50 MB per `AGENTS.md` tokenizer/runtime row); verify NDK `arm64-v8a` slice works with `minSdk 28` before committing quant path.
- **Per-layer mixed precision (`06_quantize_q4f16.py --exclude-nodes`):** Keep cross-attention + `lm_head` in FP16 while quantizing FFN/self-attn; often beats global INT8 on quality/size tradeoff.
- **Provider-specific bundles:** WASM INT8 vs WebGPU Q4F16 split is browser-specific (`ONNX_SIZE_OPTIMIZATION.md:16`) but the principle (quant tier per ABI) applies to `arm64-v8a` vs `x86_64` emulator.

**Deprecated/outdated:**
- **`infer_it2.py` / `it2_onnx_*` decode paths in `ml/translation/` (`README.md:19`):** Broken (custom modeling vs new `transformers`; ONNX KV-cache repetition) — use `it2_ct2_baseline.py` as oracle and `it2_inference.py` greedy loop for ONNX.
- **`spm` raw `EncodeAsPieces` token path:** Desyncs dict IDs; replaced by HF-tokenizer-derived `convert_ids_to_tokens` in baseline.
</sota_updates>

<open_questions>
## Open Questions

1. **Can INT8 fit 100–180 MB on indic→indic 320M, or must P1 request a budget relief/fallback?**
   - What we know: CT2 int8 is 310 MB (312 MB dir), CT2 pruned int8 223 MB; ONNX int8 after all optimizations + shared sidecar is 371 MB (`README.md:212`) on 1100-fixture oracle; indic→indic has untied weights so dedup does not help (`EXPORT_ISSUES.md:12`, `py:205`).
   - What's unclear: Exact ONNX RT Mobile artifact size when placed under `assets/vachak_models/mt/` with compression (APK zip), whether `onnxruntime-android`’s own binary is counted in the 100–180 MB slice, and how much further Q4F16 or sampling-aware pruning can shave without breaching Ol Chiki readability.
   - Recommendation: First action in plan 01-01 is a **size spike**: export `ai4bharat/indictrans2-indic-indic-dist-320M` to ONNX via the existing pipeline, run `04_quantize_int8.py` + `finalize_bundle_layout`, `du -sh` the result, and `assembleDebug` to measure APK MT slice. If >180 MB, produce a 1-page variance note with three options (INT8 + APK compression, Q4F16, or pruned vocab) and let the steering group decide before adapter wiring.

2. **Does the adapter need a bundled IndicProcessor or a Kotlin port?**
   - What we know: `IndicProcessor` pre/postprocess is mandatory for parity; Python `it2_inference.py` calls `IndicTransToolkit` which depends on `indic-nlp-library` resources (`ml/translation/mundari/it2_ct2_baseline.py:39`).
   - What's unclear: Whether `IndicTransToolkit` can be compiled for Android or must be reimplemented in Kotlin (normalization rules, placeholder num/email handling). The sister repo’s browser port has a TS port (`translator.js`) — not yet a Kotlin one.
   - Recommendation: Bundle `indic_nlp_resources` and port the **minimal** `preprocess_batch/postprocess_batch` surface (lang-tag prefixing, NFKC, placeholder handling) as Kotlin util with a parity unit test against Python’s output on 20 fixtures (same approach as tokenizer validation).

3. **Ol Chiki script fidelity threshold — how to judge fallback?**
   - What we know: Acceptance is “shows real Santali Ol Chiki” (`ROADMAP.md:24`, `CONTEXT.md:66`); baseline sample `ନମସ୍କାରଂ` is actually Odia, not Ol Chiki range; CONTEXT allows planner discretion on fallback if int8 degrades Ol Chiki quality.
   - What's unclear: chrF / human-readable threshold that constitutes “degraded” for Ol Chiki (diacritics, conjuncts). No HIN–SAT Ol Chiki gold human baseline exists in-repo.
   - Recommendation: Define fallback trigger as **either** (a) <72% text match vs FP32 oracle on 200+ fixtures (mirrors the documented 72% INT8 text match on indic-indic) **and** Ol Chiki codepoint ratio drops, **or** (b) manual review of 10 FLN sentences by a Santali literate tester. Document the trigger in plan 01-03’s offline verification.

4. **ORT Mobile AAR availability and ABI coverage for 2GB device**
   - What we know: `:ml` currently vendors `sherpa-onnx-1.13.0.aar` which bundles `libonnxruntime.so`; `android/app` targets `arm64-v8a` + `x86_64` for emulator (`build.gradle.kts:11`); `PROJECT.md:40` says arm64 first.
   - What's unclear: Whether adding `onnxruntime-android` as a separate dep collides with the sherpa-bundled `libonnxruntime.so` (duplicate .so), and which version aligns with `minSdk 28`. Some ORT AARs require NDK 26+.
   - Recommendation: In plan 01-02, spike `dependencies { implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0") }` and verify `assembleDebug` + device `adb shell pm path` does not throw `DuplicateFileException` for `libonnxruntime.so`. Alternative: reuse sherpa’s bundled ORT via its exposed C API, or use `onnxruntime` via `sherpa-onnx`’s native binding if exposed.

5. **Tokenizer asset size budget is not yet tracked**
   - What we know: `tokenizer_tgt.json` is 23.9 MB vs naklitechie 17.7 MB; model bundle’s tokenizer payload is counted separately in `AGENTS.md` (20–50 MB); full `models/indictrans2_bart` is 760 MB on disk.
   - What's unclear: Which subset must ship ( `tokenizer_src/tgt.json` + `tokenizer_meta.json` + `config.json` + `generation_config.json` is sufficient; `model.SRC`/`model.TGT`/`dict.*.json` are not needed at runtime) and how that factors into the MT slice vs tokenizer/runtime slice.
   - Recommendation: Ship only the four JSON needed files plus `dict` sizes in meta; measure combined as part of the 180 MB gate and call out if tokenizer alone is >30 MB (then prune vocab or share with pack).
</open_questions>

<sources>
## Sources

### Primary (HIGH confidence)
- `AGENTS.md` — offline, sequential, 500 MB budget, no IN22, no Piper in APK
- `ml/translation/mundari/it2_ct2_baseline.py` — real CT2 int8 baseline, lazy imports, Flores codes, `Vachak-MT` logs, verified `नमस्ते→sat_Olck` path
- `ml/translation/README.md` — CT2 recipe requirement (HF-tokenizer-derived tokens, not raw SPM), broken `infer_it2.py` / `it2_onnx_*`
- `indictrans2-onnx-export/src/01_export_encoder_decoder.py` + `src/it2_onnx_wrappers.py` — 3-graph export with dummy enc hidden, dynamic axes
- `indictrans2-onnx-export/src/04_quantize_int8.py` — dynamic per-channel INT8
- `indictrans2-onnx-export/src/it2_inference.py` — greedy decode oracle + batch chunking + `make_onnx_sessions`
- `indictrans2-onnx-export/src/onnx_bundle_optimize.py` — tied dedup, ORT fusion, externalize, shared `decoder_shared.onnx.data`
- `indictrans2-onnx-export/src/03_validate_parity.py` — ≥99% token-exact pass criteria
- `android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt` — recursive copy + null AssetManager pattern
- `android/app/src/main/java/com/vachak/engine/EngineContracts.kt` + `EngineProvider.kt` — `TranslationEngine` contract + swap point
- `android/app/src/main/java/com/vachak/engine/mock/MockEngines.kt` — fixture to replace

### Secondary (MEDIUM confidence — verified against primary)
- `indictrans2-onnx-export/README.md` — fp32 100% parity table, quantization benchmarks (indic-indic INT8 72.36% text, 370.9 MB, 1.47× speedup)
- `indictrans2-onnx-export/EXPORT_ISSUES.md` — all 16 export failure modes (Optimum unsupported, past shape, mask dropped, cross-attn stripped, double postprocess)
- `indictrans2-onnx-export/ONNX_SIZE_OPTIMIZATION.md` — 500 MB duplicate cause + shared sidecar rationale
- `indictrans2-onnx-export/quantization_issues.md` — bias FP32 bug, q4f16 `accuracy_level=4`
- `docs/MODEL_AND_DATA_PROVENANCE.md` + `THIRD_PARTY_NOTICES.md` — license matrix (MIT/CC BY 4.0)
- `.planning/phases/01-ondevice-mt/01-CONTEXT.md` + `.planning/ROADMAP.md` + `.planning/PROJECT.md` — P1 boundary, success criteria, wave ordering

### Tertiary (LOW confidence — needs validation during planning/execution)
- APK compression effect on `.onnx.data` size (device `du` vs `apkanalyzer` may differ)
- `onnxruntime-android` version/NDK compatibility with `minSdk 28` + sherpa-bundled `libonnxruntime.so` coexistence
- Kotlin port fidelity of `IndicProcessor` (indic-nlp resources on Android)
- Actual on-device MT latency with ORT Mobile + beam=1 greedy on 2 GB arm64 (README latencies are desktop: 16.5 ms INT8 per sentence)
</sources>

<overview>
## Overview

P1 is the first of seven waves toward a fully offline Santali tablet app (`AGENTS.md:5`). The shell is already DONE: Compose/Room, vendored `sherpa-onnx-1.13.0.aar`, working VAD/ASR/TTS adapters with `null` AssetManager fix, and a Python CTranslate2 INT8 baseline that proves IndicTrans2 indic→indic-dist-320M truly handles `hin_Deva → sat_Olck`. The gap is the **APK path**: no ORT Mobile, no `IndicTrans2Adapter`, no ONNX assets, and `EngineProvider.real()` still returns the mock.

The sibling `indictrans2-onnx-export/` repo contains a battle-tested pipeline for exactly this conversion — manual wrappers, fast tokenizers with dict remap, parity at 100%, and an optimization pass that is now mandatory reading. The mismatch is that its published indic→indic numbers do not meet Vachak’s AGENTS budget, so the research must surface that early.

</overview>

<technical_approaches>
## Technical Approaches

### Approach A — Recommended: Reuse indictrans2-onnx-export Pipeline + ORT Mobile Adapter
- **Export:** `src/01_export_encoder_decoder.py` for `ai4bharat/indictrans2-indic-indic-dist-320M` → `android/ml/src/main/assets/vachak_models/mt/` (opset 17, `dynamo=False`, BATCH=1, ENC_SEQ=8 for trace).
- **Tokenizers:** `src/02_build_fast_tokenizers.py` → `tokenizer_src/tgt.json` + `tokenizer_meta.json`; validate inline 100% vs slow tokenizer before proceeding.
- **Optimize:** `onnx_bundle_optimize.optimize_export_bundle()` (fusion + externalize >100 MB + shared `decoder_shared.onnx.data`). For indic→indic, expect no dedup (already untied).
- **Quantize:** `src/04_quantize_int8.py` (dynamic, `per_channel=True`, `extra_options={DefaultTensorType:FLOAT}`) → `finalize_bundle_layout`. Measure `du -sh`; if >180 MB keep artifact and flag variance.
- **Android:** Add `onnxruntime-android` to `:ml`; reuse `SherpaAssets.prepare` to copy `mt/` to `filesDir`; implement `IndicTrans2Adapter` porting `it2_inference.py:greedy_decode_onnx` (encode → enc → dec → loop dec_with_past → decode → postprocess → Ol Chiki validate).
- **Wiring:** `EngineProvider.real(context)` returns `IndicTrans2Adapter`; `EngineResult.fold` in `MainScreen` already handles both.
- **Verify:** Unit test Hindi→Ol Chiki non-empty, no `[DEV-FIXTURE]`, Ol Chiki codepoint check; device test `WiFi OFF`, latency `≤0.5s` via `LatencyTracker`, manifest no `INTERNET`, `adb logcat -s Vachak-*`.
- **Pros:** Minimum new code, 100% parity vetted, mirrors proven sherpa pattern.
- **Cons:** Likely budget variance on first cut; needs follow-up slimming.
- **Effort:** 3 plans as in ROADMAP (01-01 export/quant, 01-02 adapter/wiring, 01-03 offline/latency gate) are correctly scoped.

### Approach B — Alternative: ORT via sherpa-onnx Bundled libonnxruntime.so (no new dep)
- Use the `libonnxruntime.so` already inside `sherpa-onnx-1.13.0.aar` through sherpa’s C API (if exposed) or by loading ORT via `System.loadLibrary("onnxruntime")`.
- **Pros:** No `.so` duplication panic, APK growth smaller.
- **Cons:** Sherpa’s ORT may be stripped/custom, not the full EP list; version pinning is hostage to sherpa releases; Kotlin binding may not expose `OrtEnvironment`.
- **When to use:** If approach A triggers `DuplicateFileException` or `minSdk` conflict, fall back to this.

### Approach C — Alternative: Ship Fine-Tuned LoRA Instead of Base-Only
- If Hin→sat on the distilled base underperforms on FLN/Mundari-specific phrasing, train a single LoRA on COILD 20,603 + Education_v2 (as `it2_goldverified_lora` pattern in `MODEL_AND_DATA_PROVENANCE.md:19`), merge into base weights before export.
- **Pros:** Domain quality, CC BY 4.0 safe.
- **Cons:** Adds Python training step, merge complexity, and a new license attestation; CONTEXT says out-of-scope for P1 unless base already handles `hin→sat` — treat as contingency.
</technical_approaches>

<risks>
## Risks

| Risk | Likelihood | Impact | Mitigation | Owner |
|------|-----------|--------|------------|-------|
| **MT slice >180 MB (371 MB INT8, 223 MB pruned CT2)** — blocks `MT-02`/`PERF-02` | HIGH | HIGH — fails APK total ~500 MB budget | Spike size first; present variance note with options (Q4F16, vocab prune, per-layer mixed, APK compression); consider steering request to raise MT budget to 250–350 MB or accept dual precision tiers | Plan 01-01 |
| **Ol Chiki script confusion (Odia vs Ol Chiki)** — silent wrong-script success | MEDIUM | HIGH — Demo acceptance fails | Flores mapping table + `OlChikiValidator` regex + unit fixture containing real Ol Chiki codepoint; keep CT2 baseline oracle as script ground truth | Plan 01-02 |
| **`libonnxruntime.so` duplicate between sherpa AAR and ORT Mobile AAR** | MEDIUM | MEDIUM — build fails | Test `assembleDebug` early; exclude sherpa’s ORT or pin to one provider; use `packagingOptions { pickFirst "lib/arm64-v8a/libonnxruntime.so" }` as last resort | Plan 01-02 |
| **Tokenizer parity drift (SPM vs dict)** | MEDIUM | HIGH — 0% parity, collapse to English | Build fast tokenizer via `SpmConverter`+dict remap, inline 100% test, clamp `unk_id`; keep slow tokenizer artifact for debug parity but not in APK | Plan 01-01 |
| **Past KV shape / mask-dropped bug** | MEDIUM | HIGH — 100% false-positive parity, real drift | Enforce dummy enc hidden zeros + `*0.0` mask trick + correct dynamic axes; run `03_validate_parity.py` vs `it2_inference` on 200+ fixtures before device test | Plan 01-01/01-03 |
| **On-device latency >0.5 s on 2 GB arm64** | MEDIUM | MEDIUM — fails `MT-03`/`PERF-01` | Measure on real tablet early (not emulator); use `numThreads=1`, lazy session load, release past tensors, keep `max_new_tokens=128`; log `Vachak-Latency` per `LatencyTracker` | Plan 01-03 |
| **Offline contract regression (hidden fetch)** | LOW | HIGH — breaks SIH demo rule | Grep manifest for `INTERNET`, run `network_request_audit`, airplane-mode instrumented test | Plan 01-03 |
| **LoRA path creep (extra scope)** | LOW | MEDIUM — P1 slips | Treat fine-tune as contingency gated on quality signal; do not merge LoRA unless distilled fails Hin→sat FLN spot check | Plan 01-01 |
</risks>

<alternatives>
## Alternatives

### Adapter Alternatives
| Instead of `IndicTrans2Adapter` in `:ml` | Could Use | Tradeoff |
|---|---|---|
| `IndicTrans2Adapter` (ORT) | `MockTranslationEngine` extended with asset file read | Trivial but still a fixture — not a real model; fails acceptance; do not do |
| `IndicTrans2Adapter` (ORT) | Backend `translation_service.py` via local HTTP | Would require `INTERNET` + running server; violates offline hard rule; P1 is APK path, not backend toggle |
| ORT Mobile Java API | ORT via `sherpa-onnx` JNI / `whisper.cpp`-style seq2seq | Smaller deps but unproven for multilingual seq2seq; no Flores tokenizer binding |
| Greedy argmax (beam=1) | Beam search (4) as in CT2 baseline | Better quality but higher latency + loop complexity; parity harness is greedy; keep beam=1 for ≤0.5 s |

### Quantization Alternatives
| Instead of `quantize_dynamic` INT8 per-channel | Could Use | Tradeoff |
|---|---|---|
| Dynamic INT8 | `optimum` static INT8 with 500-sentence cal set | Static can be smaller/more accurate but requires `calibration_dataset` (COILD subset) and ORT calibration tool; dynamic is validated and calibration-free |
| Dynamic INT8 | FP16 only | 671 MB ( indic-indic), too large; but 99.82% parity — useful as fallback if INT8 quality collapses; pair with APK split |
| Dynamic INT8 | Q4F16 (`MatMulNBitsQuantizer`) | 493 MB, 46% parity on 320M (worse than INT8); 1B models benefit more; not recommended for P1 unless block_size=16+exclusions lift parity |
| Global INT8 | Per-layer mixed (keep cross-attn/lm_head FP16) | Best size/quality if global INT8 degrades Ol Chiki; requires `--exclude-nodes` enumeration — add as fallback task |

### Asset Alternatives
| Instead of `assets/vachak_models/mt/` bundle | Could Use | Tradeoff |
|---|---|---|
| APK assets (`:ml`) | `:app` `src/main/assets` (current `asr`/`tts`/`vad`) | Either works; ROADMAP says `assets/vachak_models/mt/` for P1 — prefer `:ml` with copy to `:app` for `SherpaAssets` path consistency |
| External sidecar `.onnx.data` | Inline weights in `.onnx` | Inline >100 MB triggers protobuf 512 MB save threshold and 2 GB limit (`onnx_bundle_optimize.py:30`); always externalize |
| Single `decoder_with_past` only | Two decoders shared sidecar | Cannot — step 1 vs steps 2+ have different I/O; shared sidecar is the optimal middle ground |
</alternatives>

<budget_latency_analysis>
## Budget / Latency Analysis

### Storage Budget (AGENTS.md:54 — ~500 MB total)
| Component | Budget | P1 Impact (measured / projected) | Status |
|---|---|---|---|
| App APK | 40–70 MB | ~30–50 MB base (Compose/Room + sherpa AAR 1.13.0) | OK |
| Hindi ASR | 30–80 MB | DEV-FIXTURE whisper-tiny in `assets/vachak_models/asr/` — not P1; P3 target 30–80 MB | — |
| **MT (IndicTrans2)** | **100–180 MB** | **CT2 int8 310 MB, pruned 223 MB, ONNX FP32 ~1.25 GB, FP16 672 MB, INT8 371 MB (validated indic-indic; README:212)** | **🔴 OVER — requires decision** |
| Santali TTS | 20–80 MB | DEV-FIXTURE `vits-zh-aishell3` in `tts/` — not P1; P2 will replace | — |
| Tokenizers/runtime | 20–50 MB | ORT Mobile AAR ~12–25 MB + JSON ~25 MB (tokenizer_tgt 23.9 MB, meta ~1 KB, configs ~6 MB) | Tight |
| Curriculum + Flashcards | 30–80 MB | P4 only | — |
| Safety margin | 30–50 MB | Consumed if MT stays >180 MB | 🔴 Danger |
| **Total** | **~500 MB** | **P1-only projection ≈ 450–550 MB (MT alone dominates)** | **Amber/Over** |

**Single-int8 ORT bundle layout (≈371 MB on disk before APK zip):**
```
assets/vachak_models/mt/
  encoder_model.onnx              ~ small proto
  encoder_model.onnx.data         ~ 115 MB (int8 encoder, estimated from fp32 294→~115)
  decoder_model.onnx              ~ small proto
  decoder_with_past_model.onnx    ~ small proto
  decoder_shared.onnx.data        ~ 250 MB (int8 shared decoder)
  tokenizer_src/tgt.json          ~ 23 + 24 MB
  config.json, generation_config.json, tokenizer_meta.json  ~ <1 MB
  total ≈ 371 MB  (README indic-indic INT8 Model Size)
```
APK `zip` deflates `.onnx.data` poorly (already compressed weights); expect 0–15% shrink only. P1 acceptance must therefore include an `apkanalyzer` or `du` dump of the `mt/` slice reported alongside the 180 MB gate.

**Mitigations to bring MT under 180 MB (evaluate in order):**
1. Per-layer mixed: keep cross-attn + lm_head FP16, quantize rest INT8 → est. ~280 MB.
2. Q4 block_size=16 + `accuracy_level=4` → est. ~320 MB ( worse than (1)).
3. Vocab prune to indic 22-lang subset used (sat_Olck + hin_Deva only) — risky; save ~40 MB of lm_head.
4. APK ABI split: ship `arm64-v8a` MT only; `x86_64` emulator uses smaller stub or downloads (not P1).
5. Accept budget bump for P1 with provenance note: document indic→indic separate-weights reason in `docs/MODEL_AND_DATA_PROVENANCE.md` and `THIRD_PARTY_NOTICES.md`, and defer full fit to pack phase (P5) where models live outside APK.

### RAM Budget (PROJECT.md:40 — 2 GB, arm64 device, sequential only)
| Allocation | Estimate | Note |
|---|---|---|
| System + app heap | ~400 MB | Android 9 baseline |
| ORT Mobile session (encoder) | ~150 MB resident (int8) | Loaded first, can release after encode |
| ORT Mobile session (decoder past) | ~220 MB | Largest; load after encoder |
| Tokenizer + pre/postprocess | ~30 MB | JSON + Indic NLP tables |
| **Peak sequential (one leg)** | **~800 MB** | Fits; parallel would be **~1.2 GB + TTS + ASR = OOM** |
| Parallel (disallowed) | >1.6 GB | AGENTS.md forbids; enforce via single `IndicTrans2Adapter` instance with synchronized `ensureLoaded` like `SherpaOnnxTtsAdapter:26` |

### Latency Budget (AGENTS.md:94 — total <3 s; MT ≤0.5 s)
| Stage | Budget | Desktop ORT INT8 (README) | Arm64 2 GB estimate | How to meet |
|---|---|---|---|---|
| ASR | ≤1.0 s | — | 0.3–1.0 s (whisper-tiny / VAD) | Not P1; sequential |
| **MT (Hin→Sat)** | **≤0.5 s** | **16.5 ms per sentence (indic-indic INT8, README:212)** on x86 desktop | **120–380 ms** on arm64 2 GB (arm NEON int8 faster than fp32; ORT Mobile quant speedup 1.47× on desktop suggests OK) | `numThreads=1`, greedy beam=1, `max_new_tokens=64` for FLN sentences, no beam, release `decoder_model` after step 1 |
| TTS | ≤1.0 s | — | 0.3–0.9 s (VITS) | Not P1 |
| **Total** | **<3.0 s** | — | **~0.9–2.3 s** once all legs wired (P6 proof) | Sequential + `LatencyTracker` marks logged as `Vachak-Latency` |
| `max_new_tokens` trade | — | Default 128 (inference.py) | Shorter for FLN (≤64) halves decode steps | Cap at 64 for lesson sentences |
| Batch | — | Batch 16 used in parity harness | P1 batch=1 (single lesson) | P1 simpler, faster |

**Measurement plan:** `LatencyTracker` with `SystemClock.elapsedRealtimeNanos()` (`android/ml/.../LatencyTracker.kt:52`) plus the `android/app/.../LatencyTracker.kt` total; log with tags `Vachak-MT` (tokenize/decode/total like `it2_ct2_baseline.py:113,123,125`) and `Vachak-Latency` (stage gates). Do not claim ≤0.5 s from desktop numbers — the proof is a run on the 2 GB target device with WiFi OFF.
</budget_latency_analysis>

<validation_notes>
## Validation Notes

### What Must Be Proven Before Planning Is Considered Sound
1. **Size spike gone green or variance filed:** `scratch/indic-indic-onnx-int8` dir exists and `du -sh` is recorded; if >180 MB a `docs/MODEL_AND_DATA_PROVENANCE.md` variance entry + `THIRD_PARTY_NOTICES` note are filed before adapter PR merges.
2. **Parity gate:** `src/03_validate_parity.py --pytorch-model ai4bharat/indictrans2-indic-indic-dist-320M` reports token_pass ≥99% at FP32 and text_pass ≥72% at INT8 on ≥200 Ol Chiki fixtures; failures dump mismatches Sample for review.
3. **Script gate:** Every `translate()` result contains at least one `U+1C50–U+1C7F` Ol Chiki codepoint; unit test `assert "[DEV-FIXTURE" !in result && hasOlChiki(result)`.
4. **Offline gate:** No `INTERNET`/`ACCESS_NETWORK_STATE` added; `adb shell dumpsys package com.vachak | grep permission` is clean; optional `network_request_audit()` equivalent (grep `OkHttp`/`HttpURLConnection`/`fetch`) returns 0 hits in `android/ml`+`android/app`.
5. **Latency gate:** On arm64 2 GB device, `LatencyTracker` reports MT stage ≤500 ms median on 10 FLN Hindi sentences (warm run, sequential). Emulator `x86_64` is not the gate — it is a smoke only.
6. **Wire gate:** `./gradlew :ml:assembleDebug :app:assembleDebug` green; `EngineProvider.real(context).translation::class.simpleName` is `IndicTrans2Adapter` (logged in `DebugPanel` when `BuildConfig.DEBUG`); `MockTranslationEngine` still ships for `mock()` and tests but is not used in `real()`.

### Validated vs Unvalidated
| Area | Validated (use with confidence) | Unvalidated (spike required in Plan 01-01/01-02) |
|---|---|---|
| Export correctness | 3-graph wrapper I/O, dummy enc hidden, dynamic axes, `logits + mask.sum()*0.0` fix (`EXPORT_ISSUES.md:16`) | Whether ORT Mobile’s on-device greedy loop reproduces desktop parity bit-for-bit |
| Tokenizer | Dict-remap fast tokenizer + clamp + `</s>` template; 100% inline parity test | Kotlin `tokenizers` runtime availability vs bundling Rust `.so`; fallback to Java SentencePiece |
| Quant path | Dynamic per-channel INT8 via `quantize_dynamic`; shared sidecar; ORT fusion | Whether INT8 keeps >72% text match on FLN education Hindi (domain may shift vs 1100 golden fixtures) |
| Asset copy | `SherpaAssets.prepare` recursive `list()` + `copyTo` + `null` AssetManager | Behavior with external `.onnx.data` sidecars of 200+ MB (does `prepare` copy sidecars?) — verify `SherpaAssets.copyTree` handles `.data` leaf files |
| Latency | Desktop INT8 speedup 1.47×; per-sentence 16.5 ms | Real-device 2 GB arm64, cold vs warm start, first vs subsequent translate |
| Budget | CT2 310/223 MB and ONNX 371 MB hard numbers | APK-compressed size vs raw disk; ORT AAR footprint inclusion |

### Evaluation Hygiene
- Never train on `IN22-Gen/Conv`; keep eval splits separate even though `ml/translation/README.md`’s golden fixtures are not IN22. Any LoRA contingency must validate against a held-out 10% of COILD HIN-SAT and report BLEU/chrF delta, not just parity vs oracle.
- Never ship `MACHINE_TRANSLATED` curriculum content as approved pedagogy (`AGENTS.md:18`) — lesson precomputed translations stay out of P1; `MockCurriculumEngine` is all that ships. Flag if adapter output would accidentally populate `Lesson.translatedText` in Room.

</validation_notes>

<metadata>
## Metadata

**Research scope:**
- Core technology: IndicTrans2 distilled 320M indic→indic seq2seq → ONNX Runtime Mobile, dynamic per-channel INT8, Kotlin greedy decode
- Ecosystem: ONNX Runtime, HuggingFace Transformers + Tokenizers, IndicTransToolkit (IndicProcessor), SentencePiece, sherpa-onnx AAR pattern, Android minSdk 28 arm64-v8a
- Patterns: 3-graph KV-cache seq2seq export, fast tokenizer dict remap, shared decoder sidecar, recursive asset extraction, sequential pipeline enforcement
- Pitfalls: Budget blow-out (untied weights), vocab desync, past KV shape, FP16 bias bug, Ol Chiki script confusion, offline regression, hidden IN22 contamination

**Confidence breakdown:**
- Standard stack: **HIGH** — verified against `pyproject.toml:7`, `EXPORT_ISSUES.md`, `ml/translation/mundari/it2_ct2_baseline.py`, and `sherpa` vendored AAR; 1100-fixture parity at 100%
- Architecture: **MEDIUM** — three-graph pattern is HIGH; on-device Kotlin ORT wiring + IndicProcessor port are MEDIUM (not yet proven in this repo’s `:ml`)
- Pitfalls: **HIGH** — 16 documented failure modes plus README parity tables directly applicable to P1
- Code examples: **HIGH** — from `it2_inference.py`, `it2_onnx_wrappers.py`, `01_export_encoder_decoder.py`, `04_quantize_int8.py`, and existing `Sherpa*Adapter.kt`
- Budget/latency: **MEDIUM** — hard numbers from sister repo are HIGH, but APK accounting + real-device latency need a spike before the ≤180 MB / ≤0.5 s claims are firm
- Validation: **HIGH** — parity ≥99% FP32 gate, script-range check, offline audit, and `LatencyTracker` conventions are all already codified

**Research date:** 2026-08-29
**Valid until:** 2026-09-28 (30 days — IndicTrans2 + ONNX Runtime mobile are stable; 7 days for `onnxruntime-android` version pin)
</metadata>

---
*Phase: 01-ondevice-mt*
*Research completed: 2026-08-29*
*Ready for planning: yes (proceed to PLAN with mandatory size spike in 01-01 and script-range gate in 01-02)*
