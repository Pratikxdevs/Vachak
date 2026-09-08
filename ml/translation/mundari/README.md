# ml/translation/mundari — Mundari NMT (scaffolding only)

> **PHASE 7 — scaffolding. No training is performed here.**

This module builds the Hindi→Mundari translation pipeline so that, **given the gated
Karya Hindi–Mundari corpus later**, the only remaining work is supplying data and
running the existing fine-tune code path. The final Mundari model is a **drop-in
LoRA adapter** over IndicTrans2, structurally identical to
`ml/finetune/it2_goldverified_lora/` (Santali).

## ⚠️ DATA ACCESS PENDING

The Karya Hindi–Mundari dataset is **NON-COMMERCIAL (BY-NC-SA-FS 1.0)** and is
**NOT available in this environment**. We have **not** trained or evaluated on it.
Every metric below is `TODO` until that corpus is licensed and loaded. See
`docs/MODEL_AND_DATA_PROVENANCE.md`.

## What exists

| File | Purpose |
|------|---------|
| `adapter.py` | `TranslationAdapter` interface + `MockTranslationEngine` (DEV FIXTURE) + `BaselineTranslationEngine` (IndicTrans2 Hindi→Santali stand-in, **NOT Mundari**) |
| `pipeline.py` | `quality_checks → make_splits → run_baseline → finetune → evaluate → export_onnx → quantize → prepare_android_bundle` |
| `evaluate.py` | BLEU/chrF (real, via sacrebleu) + human/terminology/latency/memory (TODO) |
| `_it2_baseline.py` | Lazy bridge to the cloned IndicTrans2 pipeline (reference only) |
| `configs/mundari_config.yaml` | Pipeline config |
| `data/fixtures/dev_fixture.tsv` | **DEV FIXTURE** synthetic pairs for tests |
| `tests/test_pipeline.py` | Unit tests (no gated data / GPU) |

## Interface contract

```python
from ml.translation.mundari import get_adapter, run_pipeline, MundariConfig, Corpus
eng = get_adapter("mock")            # or "baseline"
print(eng.translate("पाँच आम गिनो।"))  # mock wraps; baseline = Santali stand-in
cfg = MundariConfig(adapter_kind="mock")
report = run_pipeline(Corpus([("हिन्दी", "मुंडारी")]), cfg)
```

`TranslationAdapter.translate(text) -> str` and `translate_batch(list) -> list` are
the contract the **final Mundari adapter** must satisfy, so swapping in the real
model requires no call-site changes.

## Run tests

```bash
python -m unittest ml.translation.mundari.tests.test_pipeline
```

## Required evaluation (all TODO until data access)

- **BLEU / chrF** — automatic, computed live once predictions exist (sacrebleu).
- **Human evaluation** — native Mundari-speaking raters; fluency + adequacy.
- **Terminology accuracy** — classroom/FLN glossary coverage (NIPUN outcomes).
- **Latency** — end-to-end MT ≤ 0.5 s on 2 GB Android 9 device.
- **Memory** — peak RSS within the ~500 MB total budget.

## Known limitations

- Decoder ONNX export for IndicTrans2 is **blocked** (KV-cache/positional mismatch,
  tracked in `ml/translation/scripts/process.md`). Quantization depends on it.
- Baseline engine returns Santali, **not Mundari**; it validates integration shape only.
- No fabricated metrics are reported anywhere in this module.
