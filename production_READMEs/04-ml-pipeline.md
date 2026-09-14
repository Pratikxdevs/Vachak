# 04 — ML Pipeline (VAD → ASR → MT → TTS)

Sequential only. Every stage `numThreads=1`, guarded by locks (`ReentrantLock`).

## VAD — Silero (prod) + energy gate (fixture)

- Prod asset: `silero_vad.onnx` via `scripts/fetch_android_models.sh:24-28`
  (k2-fsa/sherpa-onnx) → `android/app/src/main/assets/vachak_models/vad/`.
- Python: `ml/pipeline/vad_stream.py` — `SherpaOnnxVadStream` (sr=16000) vs
  `MockVadStream` (energy threshold 0.02, DEV-FIXTURE).
- Kotlin: `android/ml/.../ml/VadStream.kt` — `SherpaOnnxVadAnalyzer`
  (`VadModelConfig(SileroVadModelConfig(...), sampleRate=16000, numThreads=1)`)
  + `MockVadAnalyzer` (energy 0.012, FIXTURE). Chunks 10–100 ms.

## ASR — NEMO CTC 134/140M (prod) vs whisper-tiny (fixture)

- Prod: `NeMo EncDecCTCModelBPE` — 5113 nodes, opset17, INT8/INT4,
  subsampling 4, vocab 5633, ~140 MB, `model.onnx + tokens.txt`. Absent from repo,
  fetched build-time. Sherpa fbank + `OfflineRecognizer`.
- Adapter: `android/ml/.../ml/IndicConformerAsrAdapter.kt` (shared process-wide
  `OfflineRecognizer`, `warmUpIfNeeded()`, `transcribe(FloatArray)→AsrResult`).
- Wrappers: `android/app/.../ml/adapter/SherpaAsrAdapter.kt` +
  `android/ml/.../ml/adapter/mlinternal/SherpaAsrAdapter.kt` (VAD-gated,
  honest `[ASR:MODEL]` errors, never `""` as success).
- Python stub: `ml/pipeline/asr_adapter.py` (`num_threads=1`, lazy ORT).
- Fixture: `sherpa-onnx-whisper-tiny` (`fetch_android_models.sh:30-42`), multilingual,
  Hindi-capable, NOT final. Candidates doc: `ml/asr/README.md`.
- Swap: `EngineProvider.kt:53-78` — `real()=SherpaAsrAdapter+SherpaVadDetector`,
  `mock()=MockAsrEngine` (tests only).

## MT — ONNX INT8 Santali live + Mundari phrasebook Phase-2

- Prod Santali: `ai4bharat/indictrans2-indic-indic-dist-320M` →
  manual `torch.onnx.export` opset17 →
  `encoder_model.onnx + decoder_model.onnx + decoder_with_past_model.onnx` + `.data`,
  `quantize_dynamic` INT8, **314–357 MB** (variance accepted,
  `docs/phases/P1-size-variance.md`). HF BPE 245k merges, greedy + past-KV,
  `repetition_penalty 1.2 / no_repeat_ngram 3`, `gold.tsv` pre-check,
  Ol Chiki regex `[\u1C50-\u1C7F]`.
- Adapter: `android/ml/.../ml/adapter/OnnxIndicTrans2Adapter.kt`
  (`TranslationEngine`, ORT-Mobile, `ReentrantLock`, 5-file load check).
- Router: `AdapterTranslationEngine.kt` (onnxSat live for `sat_Olck`,
  mundariBook/CT2 for `unr_Deva`).
- Export/train: `ml/translation/scripts/export_onnx_it2.py`, `infer_it2.py`,
  `bench_onnx.py`, `bench_ct2.py`, `quant_onnx_matmulnbits.py`,
  `merge_lora_to_ct2.py`; `ml/finetune/` (sat_bidi r64 merged, mundari r16 pending).
- Mundari Phase-2: `MundariPhrasebookEngine.kt` over
  `modelpacks/mundari_phrasebook/corpus.tsv` (17,826 rows, tiers
  exact→normalized→F1≥0.55→honest Err). Future CT2
  (`stripped_mt 223M` / `stripped_mt_merged ~210M`) via `ctranslate2_jni.cpp`,
  excluded from APK (`android/ml/build.gradle.kts:41-48`).

## TTS — Santali VITS ~110 MB (prod) vs zh/espeak/shim (fixture)

- Prod: Coqui-VITS `model.onnx ~110 MB` (opset17, 22.05 kHz mono,
  38 Ol Chiki char tokens ids 4–41 + 231-entry char-split `lexicon.txt`,
  `dataDir=""`, no espeak data, `speed=1.2`, chunk ≤150 chars).
- Adapter: `android/ml/.../ml/SherpaOnnxTtsAdapter.kt` (pack-aware `resolveBaseDir()`,
  `detectShim/isShim`, `ensureLoadedInner(numThreads=1)`, `synthesize+chunkForTts`).
- Wrapper: `android/app/.../ml/adapter/SherpaTtsAdapter.kt`
  (Ol Chiki gate + shim gate + <200 ms anti-blip guard).
- Training: `ml/tts/finetune_sat.py` (Coqui 0.27.5, `use_phonemes=False`,
  22050/256/1024/80mel, 5284 wavs / 7.46 h), `pipeline.py`, `export_onnx.py`,
  `dataset/prepare_santali.py`, `adapter.py` (espeak-hi fallback).
- Fixture: `vits-zh-aishell3` + `espeak-ng-data` (`fetch_android_models.sh:44-61`),
  55-token shim (`isFixture=true`), `piper-hi-base` interim (GPL-3.0, never in APK).

## Orchestration + budgets

- `shared/orchestrator/interfaces.py` + `orchestrator.py`
  (`VAD→ASR→Context→Translate→Validate→TTS→Speak`, 3000 ms budget).
- Kotlin mirror: `android/ml/orchestrator/Orchestrator.kt` (`EngineResult`,
  `MODEL_NOT_LOADED→promptLanguagePack`).
- Benchmarks: `ml/benchmarks/harness.py` (chrF/WER/CER, PENDING unless real_android).
