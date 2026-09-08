# ml/translation — IndicTrans2 Hindi→Santali baseline (real inference)

This directory now contains a **working real baseline** for the offline classroom pipeline.
It is a LABELED STAND-IN for the Mundari target: it translates Hindi→**Santali** (sat_Olck)
using IndicTrans2. It is NOT Mundari.

## What runs
`ml/translation/mundari/it2_ct2_baseline.py` — `IndicTrans2CT2Baseline`
- Reuses the cloned IndicTrans2 tokenizer (`models/indictrans2_bart`: `tokenization_indictrans.py`
  + SRC/TGT sentencepiece + dict) via the genuine `AutoTokenizer(trust_remote_code=True)`.
- Runs inference with `ctranslate2.Translator` over the exported INT8 checkpoint
  (`models/indictrans2_ct2_int8`).
- Input is preprocessed (lang tags + normalization) with `IndicTransToolkit.IndicProcessor`,
  tokens are derived with the genuine HF tokenizer (`convert_ids_to_tokens`), fed to CT2, and
  the hypotheses are decoded + postprocessed back to Ol Chiki.

This CT2 recipe (HF-tokenizer-derived token strings, not raw sentencepiece pieces) is required:
raw `SentencePieceProcessor.EncodeAsPieces` desyncs the vocabulary and collapses decoding to
`eng_Latn`. The referenced `infer_it2.py` (PyTorch) and `it2_onnx_*` paths are broken in this
env (custom modeling vs new transformers; ONNX KV-cache repetition) — do not use them for decode.

## Run / test
```bash
# real translation (export venv has ctranslate2):
/home/clutch/Desktop/Vachak/ml/export_venv/bin/python -m unittest \
    ml.translation.mundari.tests.test_baseline_real -v

# one-off:
python -c "import sys; sys.path.insert(0,'ml/translation'); \
from mundari.it2_ct2_baseline import IndicTrans2CT2Baseline as E; \
print(E().translate(['बच्चों, पाँच आम गिनो।'],'hin','sat'))"
```
Heavy deps (ctranslate2/transformers/sentencepiece/IndicTransToolkit) are imported lazily in
`load()`, so mock-only CI needs none of them. The pytest skips itself (not errors) if the
artifact/env is unavailable.

## Backend wiring
`backend/api/translation_service.py` selects the engine via `TRANSLATION_BACKEND`
(`mock` | `baseline` | `final`). `baseline` calls this module and emits `Vachak-MT` DEBUG logs
at input/tokenize/model-load/decode/output/latency. `mock` is unchanged for CI.

## Provenance (append to THIRD_PARTY_NOTICES.md — do NOT edit that file here)
- Code: IndicTrans2, MIT, AI4Bharat, repo `/home/clutch/Desktop/Vachak/IndicTrans2` (LICENSE kept).
- Tokenizer: IndicTransTokenizer + IndicTransToolkit, MIT, AI4Bharat; reused, not modified.
- Weights: `ai4bharat/indictrans2-indic-indic-dist-320M` (MIT) → exported CTranslate2 INT8
  (`models/indictrans2_ct2_int8`) via `ctranslate2` TransformersConverter.
- This baseline is Hindi→Santali (sat_Olck). It is explicitly NOT Mundari and must not be
  shipped as approved Mundari pedagogy.
