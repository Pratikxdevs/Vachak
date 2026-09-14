# 06 — Training Journey & Compute Log

> Hour counts below are **team-reported** (Colab + Lightning dashboards), not stored in git.
> Code evidence column shows the repo artifact each effort produced.

## Compute summary (as reported by team)

| Effort | Platform | Hours | Output |
|---|---|---|---|
| MT (Santali bidi fine-tune, IndicTrans2 LoRA r64 → merge → CT2 INT8 + ONNX INT8 export) | Google Colab | ~30 h | `ml/finetune/adapter_sat_bidi/`, `merged_sat_bidi/`, `ct2_sat_bidi_int8/`, `modelpacks/sat_bidi_ct2_int8/`, ONNX 3-graph 314–357 MB |
| TTS (Santali VITS fine-tune, Coqui 0.27.5) | Lightning AI | ~20 h | `ml/tts/finetune_sat.py` run, 5284 wavs / 7.46 h data → `model.onnx ~110 MB + lexicon.txt (231)` |
| Sprint bundles / export / bench | Colab + Lightning + local | interleaved | `colab/`, `lightning_bundle/`, `sprint4h/`, `indictrans2-onnx-export/`, `benchmarks/ct2_migration_benchmark.py` |

Total ML training compute reported: **~50 h** (30 Colab + 20 Lightning), plus ASR integration,
UI revamp, and pack/diagnostics engineering time (below — not GPU, counted separately).

## Stage timelines

**MT (Santali, flagship).** Base `indictrans2-dist-320M` (MIT) → LoRA r64 bidi on
9,423 verified `material/` pairs → merge (`merge_lora_to_ct2.py`) → CT2 INT8
(`modelpacks/sat_bidi_ct2_int8/`, held-out classroom chrF 40.4 hi→sat / 45.8 sat→hi,
per `backend/api/translation_service.py`) → manual `torch.onnx.export` opset17
(`ml/translation/scripts/export_onnx_it2.py`) → `quantize_dynamic` INT8
(`quant_onnx_matmulnbits.py`) → bench (`bench_onnx.py`, `bench_ct2.py`,
`benchmarks/ct2_migration_benchmark.py`: CT2 223M vs ONNX 357M).
Size variance 314–357 MB accepted (`docs/phases/P1-size-variance.md`);
`packages/packs` manifest notes totalBytes 558 MB vs 500 MB budget → withinBudget:false.

**TTS (Santali VITS).** Data prep `ml/tts/dataset/prepare_santali.py` (5284 utt / ~10 h raw
`raw/santali_male_native_web/`, UNVERIFIED prototype-only per `VOICE_CONSENT.md` §9;
CC BY path: IndicVoices SAT-IV-SP001 ~3200/4.52 h, Nirantar, Rasa ~850, Common Voice ~533)
→ `ml/tts/finetune_sat.py` (Coqui 0.27.5, use_phonemes=False, 22050/256/1024/80mel,
7.46 h effective) on Lightning ~20 h → `export_onnx.py` (VITS subgraphs, TODO in repo)
→ `model.onnx + tokens + lexicon`, `dataDir=""`, shim-gated on device
(`SherpaOnnxTtsAdapter.kt: detectShim/isShim`: 55-token=shim, 38=live, 219=zh-fixture).
Interim `piper-hi-base` shim (`scripts/patch_piper_for_sherpa.py`, GPL-3.0, never in APK).

**ASR (Hindi).** No from-scratch training in this sprint — integration + adaptation of
NeMo CTC 134M sherpa-layout ONNX (~140 MB, build-time fetch). Work: `IndicConformerAsrAdapter.kt`
(shared recognizer, warm-up, honest `[ASR:MODEL]` errors), VAD gating
(`SherpaAsrAdapter.kt`), Python stub (`ml/pipeline/asr_adapter.py`), candidates survey
(`ml/asr/README.md`: IndicConformer/Vosk/whisper.cpp). Fixture `whisper-tiny` kept for dev.
ASR effort = days of adapter/diagnostics work, not GPU hours.

**Mundari (Phase-2).** Deterministic phrasebook first: 17,826-row `corpus.tsv`
(exact→normalized→F1≥0.55→honest Err, `MundariPhrasebookEngine.kt`).
Neural LoRA r16 (`ml/finetune/it2_mundari_lora_real`, 14M params) pending merge;
`stripped_mt` 223M / `stripped_mt_merged` ~210M + `Ct2Jni` + `SpApproxTokenizer`
excluded from APK until ready (`android/ml/build.gradle.kts:41-48`).

**UI revamp.** Full Compose rebuild: `MainScreen` + `LiveComponents`
(per-item `ASR x • MT y • TTS z • Total`), `DiagnosticsScreen`
(measured APK+filesDir+DB bytes, `GIT_SHA`, pack SHA, ASR fingerprint, `VachakLog`),
`ModelStatus` dots, `ActiveLanguage` StateFlow (sat_Olck default).
Effort = app-module engineering (no GPU); verified by
`./gradlew :ml:testDebugUnitTest :app:testDebugUnitTest`.

## Journey in one paragraph

Colab (~30 h) turned the MIT IndicTrans2 base into a Santali bidi ONNX bundle;
Lightning (~20 h) turned ~7.5 h of prototype Santali speech into a VITS ONNX voice;
ASR was integrated (not retrained) with honest error surfacing; Mundari shipped as a
safe deterministic phrasebook while its neural merge matures; the UI was revamped around
measured latency and offline packs — WiFi OFF, <3 s voice-to-voice, diagnostics proving it.
