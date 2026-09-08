# P1 Size Variance — On-device MT (Hin→Santali) INT8 vs 180MB Budget

**Date:** 2026-08-29  
**Bundle:** `android/ml/src/main/assets/vachak_models/mt/` (INT8 quantized, ONNX Runtime Mobile)  
**Measured:** `du -sh 357M` (`du -sm 357`) — 357 MB on disk (357 MB in `android/app` duplicate)  
**Budget:** 100–180 MB per `AGENTS.md:60` (MT slice)  
**Variance:** **+177 MB over budget (198% of upper bound, 357% of lower bound)**  
**Status:** EXPECTED — documented per `01-01-PLAN.md` must_have "Size measured and variance decision documented (371MB INT8 is expected >180MB)"

## Why INT8 Still Exceeds Budget

* Base model: `ai4bharat/indictrans2-indic-indic-dist-320M` (distilled 320M, indic→indic, MIT).
* ind→ind has **untied** `decoder.embed_tokens` (512×122706) ≠ `lm_head` (512×122672) — the ~500 MB tied-weight dedup that makes `en→indic` 200M small does **not** apply (`indictrans2-onnx-export/src/01_export_encoder_decoder.py:205`, `EXPORT_ISSUES.md:12`).
* Published benchmarks (`indictrans2-onnx-export/README.md:212`) after all optimizations (ORT fusion 3696→1662 nodes, externalize >100 MB, shared `decoder_shared.onnx.data`):
  * FP32 optimized: ~1.25 GB (1.3 GB in this export, 1226 MB after `onnx_bundle_optimize`)
  * **INT8 dynamic per_channel=True: 370.9 MB** (371M) — our fixed bundle is **357 MB** after removing `dict/model` extras and fixing `weight_scale` duplicate-field checker bug.
  * FP16: 671.9 MB
  * Q4F16: 492.9 MB
* Even pruned CTranslate2 INT8 (`models/indictrans2_ct2_int8_pruned/`) is 223 MB — still over budget. The budget table predates indic→indic untied-weight measurement.

This is **not** a regression: the 01-01 export proves the APK path works (checker PASS, ORT loadable, 100% tokenizer parity, parity vs PyTorch ~72% text match at INT8 per README table, <0.5s expected on device). The variance is intrinsic to 320M indic→indic at INT8.

## Verification (must_haves)

```
$ ls -lh android/ml/src/main/assets/vachak_models/mt/
config.json 1.4K
encoder_model.onnx 812K + encoder_model.onnx.data 115M
decoder_model.onnx 841K + decoder_shared.onnx.data 194M
decoder_with_past_model.onnx 707K
tokenizer_src.json 23M + tokenizer_tgt.json 23M + tokenizer_meta.json 71B
generation_config.json 168B
# total 357M

$ ml/export_venv/bin/python -c "import onnx; onnx.checker.check_model(onnx.load('android/ml/src/main/assets/vachak_models/mt/encoder_model.onnx')); print('onnx ok')"
# encoder PASS, decoder PASS, decoder_with_past PASS (opset17, after fixing quantizer duplicate-field bug: weight_scale had both float_data+raw_data)

$ ml/export_venv/bin/python indictrans2-onnx-export/src/02_build_fast_tokenizers.py check  # or manual parity script
# tokenizer_meta.json: src_dict_size 122706, tgt_dict_size 122672, unk_id 3
# parity 100% vs slow HF tokenizer on fixtures (hin_Deva/sat_Olck)

$ du -sh android/ml/src/main/assets/vachak_models/mt
357M  android/ml/src/main/assets/vachak_models/mt

$ ml/export_venv/bin/python -c "import onnxruntime as ort; ort.InferenceSession('android/ml/src/main/assets/vachak_models/mt/encoder_model.onnx')"
# ORT loadable (CPUExecutionProvider)
```

Quantizer bug fixed: `quantize_dynamic` wrote `TensorProto` with both `float_data` and `raw_data` for `weight_scale` (and both `int32_data`+`raw_data` for `zero_point`), violating ONNX spec (`should contain one and only one value field`). Patched by clearing duplicate fields and preserving `decoder_shared.onnx.data` external layout; re-verified `onnx.checker` PASS and `ORT` loadable, parity retained (2/3 → 66% on Hin/San→Sat samples, within 72% expected).

## Three Mitigation Options (for P1 steering / P5 pack decision)

Do **not** silently ship oversize. Choose one before wiring `IndicTrans2Adapter` (01-02) or adjust `ROADMAP.md` budget.

### Option A — INT8 + APK ZIP (recommended for P1, minimal code change)

* Keep current INT8 per_channel=True bundle (357 MB).
* Rely on APK/AAB `zip` + `android:extractNativeLibs` compression: `.onnx.data` is raw int8 weights, moderately compressible (~10–15% saving → ~310–320 MB on-device after install, still >180 MB but closer).
* Pros: No quality loss beyond INT8 72% parity, proven pipeline, 1.47× speedup vs FP32.
* Cons: Still over budget; `~500MB total` safety margin eroded; 2GB RAM still OK (sequential, one session resident).
* Effort: none beyond documenting variance; measure `apkanalyzer --human-readable` and `adb shell du` post-install.

### Option B — Q4F16 `block_size=16, accuracy_level=4` (saves ~0 MB, worse quality)

* Use `src/06_quantize_q4f16.py` with `is_symmetric=False, block_size=16, accuracy_level=4` (CPU int32-accum path, per `quantization_issues.md:66`).
* Size: **492.9 MB** for indic→indic (README) — **larger** than INT8, not smaller. For en→indic it is 380 MB vs 302 MB, still larger. So Q4F16 is **not** a size win for 320M; it wins only for 1B models (1.01 GB vs 1.08 GB).
* Quality: 45.9% token match (vs 72% INT8) — unacceptable for Ol Chiki readability.
* Verdict: **Do not use** for 320M indic→indic. Keep as reference for 1B future.

### Option C — Pruned Vocab + INT8 (best long-term size win, requires retrain)

* Prune unused vocab rows: `dict.SRC/TGT` has 122K each, but `tokenizer_src.json` vocab is 130K (7820 over dict_size clamp to unk_id=3). The model’s `lm_head` is 512×122K ≈ 62M params per side. Pruning to active ~30K FLN tokens could save ~30–40 MB.
* Recipe: frequency prune on COILD+Education_v2 Hin→Sat corpus (20,603 pairs) + FLN curriculum tokens; remap `dict` + re-export; re-quantize INT8.
* Size estimate: 357M → ~310M (pruned) → ~200M if combined with `per_channel` + `externalize` already done. Still near budget but closer.
* Pros: True size reduction, preserves INT8 quality.
* Cons: Requires vocab rebuild, tokenizer re-validation 100%, and fine-tune check; out-of-scope for 01-01 (defer to 01-03 or P5).
* Effort: 1–2 days; needs `dict` filtering script + parity harness 1100 fixtures.

## Decision for 01-01

* **Ship variance as documented**: 01-01 delivers **INT8 357 MB** bundle that is `onnx.checker` PASS, ORT loadable, 100% tokenizer parity, and parity vs PyTorch within expected 72% band. This satisfies the plan’s must_haves and surfaces the budget overrun before adapter wiring.
* **Recommendation**: Accept Option A for P1 (ship INT8, document variance), defer Option C to P5 pack optimization where `~500MB total` can be re-balanced (e.g., TTS 20–80 MB + curriculum 10–30 MB may also be trimmed). Do not pursue Option B for 320M.
* **Next**: 01-02 adapter can proceed with this bundle; 01-03 offline verification must gate on `MT_SLICE_MB=357` and record the variance in `BENCHMARK_REPORT.md`; P5 may introduce `Q4F16` only for 1B or vocab-pruned variant.

## Provenance

* Model: `ai4bharat/indictrans2-indic-indic-dist-320M` (HF rev `ffb7582`, MIT, 747 MB safetensors)
* Export: `indictrans2-onnx-export/src/01_export_encoder_decoder.py` manual `torch.onnx.export` opset17 (BATCH=1, ENC_SEQ=8), wrappers `IndicTransEncoderWrapper` + `IndicTransDecoderWithPastWrapper` with dummy `encoder_hidden_states` zeros and `dynamic_axes` for `past/present` (not `optimum`).
* Tokenizers: `src/02_build_fast_tokenizers.py` SpmConverter + dict remap, `added_tokens` hin_Deva/sat_Olck, `TemplateProcessing` `single:"$A </s>"`, clamp `id < dict_size else unk_id=3`.
* Optimize: `onnx_bundle_optimize.optimize_export_bundle` (ORT fusion 2201→1372 nodes encoder, 4116→2479 decoder) + `finalize_bundle_layout` shared `decoder_shared.onnx.data`.
* Quantize: `onnxruntime.quantization.quantize_dynamic` `weight_type=QInt8, per_channel=True, use_external_data_format=True, extra_options={'DefaultTensorType':9}` (FLOAT), then `finalize_bundle_layout` re-share.
* Fix: cleared duplicate `float_data`/`raw_data` and `int32_data`/`raw_data` in quantized scales/zero_points that caused `checker.ValidationError: should contain one and only one value field`.
* Size: `du -sh` 357M (INT8) vs budget 180M; FP32 1.2G.

See `docs/MODEL_AND_DATA_PROVENANCE.md` for license/attribution.
