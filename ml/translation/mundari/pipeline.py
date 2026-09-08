"""Mundari NMT training/eval/export pipeline (scaffolding only, no training).

Ordered stages, each a function:
  quality_checks -> make_splits -> run_baseline -> finetune -> evaluate
  -> export_onnx -> quantize -> prepare_android_bundle

None of these stages touch the network. Stages that require the gated Karya
Hindi-Mundari corpus or a trained model return a clear TODO / plan instead of
running, because that data is NOT available in this environment
(see docs/MODEL_AND_DATA_PROVENANCE.md -> "DATA ACCESS PENDING").

The final Mundari model is designed as a DROP-IN adapter over IndicTrans2, exactly
like ml/finetune/it2_goldverified_lora is for Santali. Only the target language and
corpus differ.
"""

from __future__ import annotations

import hashlib
import json
import random
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from .adapter import MockTranslationEngine, get_adapter
from .evaluate import evaluate, MundariEval


@dataclass
class MundariConfig:
    src_lang: str = "hin_Deva"
    tgt_lang: str = "mun_Deva"  # Mundari (written in Devanagari / Mundari Bani pending decision)
    base_model: str = "ai4bharat/indictrans2-indic-indic-dist-320M"
    adapter_kind: str = "mock"  # "mock" | "baseline" | "final" (final not yet available)
    seed: int = 26042
    train_frac: float = 0.8
    dev_frac: float = 0.1
    test_frac: float = 0.1
    onnx_dir: Optional[str] = None
    out_dir: str = "ml/translation/mundari/runs"


@dataclass
class Corpus:
    pairs: List[Tuple[str, str]]

    def __len__(self) -> int:
        return len(self.pairs)


def quality_checks(corpus: Corpus) -> Dict[str, object]:
    """Validate a parallel corpus. Returns issue counts; empty issues = clean."""
    issues: Dict[str, int] = {"empty_src": 0, "empty_tgt": 0, "dup_pairs": 0,
                              "mismatch_counts": 0, "non_hindi_src": 0}
    seen = set()
    for s, t in corpus.pairs:
        if not s or not s.strip():
            issues["empty_src"] += 1
        if not t or not t.strip():
            issues["empty_tgt"] += 1
        key = (s.strip(), t.strip())
        if key in seen:
            issues["dup_pairs"] += 1
        seen.add(key)
    clean = sum(issues.values()) == 0
    return {"ok": clean, "n": len(corpus), "issues": issues,
            "note": "DATA ACCESS PENDING: gated Karya corpus not loaded"}


def make_splits(corpus: Corpus, cfg: MundariConfig) -> Dict[str, Corpus]:
    """Deterministic, reproducible train/dev/test split (seeded)."""
    rng = random.Random(cfg.seed)
    idx = list(range(len(corpus)))
    rng.shuffle(idx)
    n = len(idx)
    n_train = int(n * cfg.train_frac)
    n_dev = int(n * cfg.dev_frac)
    train = [corpus.pairs[i] for i in idx[:n_train]]
    dev = [corpus.pairs[i] for i in idx[n_train:n_train + n_dev]]
    test = [corpus.pairs[i] for i in idx[n_train + n_dev:]]
    return {"train": Corpus(train), "dev": Corpus(dev), "test": Corpus(test)}


def run_baseline(split: Corpus, cfg: MundariConfig) -> List[str]:
    """Produce baseline predictions. Uses the mock or the labeled Santali stand-in;
    NOT Mundari. Returns hypothesis list for the eval stage."""
    eng = get_adapter(cfg.adapter_kind, onnx_dir=cfg.onnx_dir)
    src = [s for s, _ in split.pairs]
    return eng.translate_batch(src)


def finetune(train: Corpus, dev: Corpus, cfg: MundariConfig, run_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would fine-tune IndicTrans2 for Mundari. Scaffolded: no training runs here.

    When data access is granted, this should mirror ml/finetune/finetune_simple.py
    (LoRA q/k/v/out, greedy decode, repetition_penalty=1.2, no_repeat_ngram_size=3)
    but with tgt_lang=mun and the Mundari corpus. That model becomes a drop-in
    adapter at ml/finetune/it2_mundari_lora/.
    """
    run_dir = Path(run_dir or cfg.out_dir)
    plan = {
        "stage": "finetune",
        "status": "TODO",
        "base_model": cfg.base_model,
        "tgt_lang": cfg.tgt_lang,
        "train_pairs": len(train),
        "dev_pairs": len(dev),
        "method": "LoRA q/k/v/out, greedy decode, rep_penalty=1.2, no_repeat_ngram=3",
        "reason": "DATA ACCESS PENDING: gated Karya Hindi-Mundari not available",
        "produces": "ml/finetune/it2_mundari_lora/ (drop-in adapter, NOT shipped yet)",
    }
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "finetune_plan.json").write_text(json.dumps(plan, indent=2), encoding="utf-8")
    return plan


def export_onnx(adapter_dir: Optional[str], cfg: MundariConfig, out_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would export the final adapter to ONNX. Scaffolded (encoder-only verified
    for Santali; decoder ONNX blocked — tracked in process.md)."""
    out_dir = Path(out_dir or cfg.out_dir)
    plan = {
        "stage": "export_onnx",
        "status": "TODO",
        "encoder_export": "verified for Santali (1e-5 vs PyTorch); Mundari TBD",
        "decoder_export": "BLOCKED: KV-cache/positional graph mismatch (see process.md)",
        "reason": "final Mundari adapter not produced yet",
        "out": str(out_dir / "it2_mundari_onnx"),
    }
    return plan


def quantize(onnx_dir: str, out_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would quantize ONNX (MatMulNBits int4) for the 2GB device budget."""
    out_dir = Path(out_dir or "ml/translation/mundari/runs")
    return {"stage": "quantize", "status": "TODO",
            "method": "onnxruntime MatMulNBits int4",
            "reason": "depends on export_onnx", "out": str(out_dir / "it2_mundari_onnx_int4")}


def prepare_android_bundle(quant_dir: str, cfg: Optional[MundariConfig] = None, out_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would package the quantized model into the Android language pack."""
    out_dir = Path(out_dir or "ml/translation/mundari/runs")
    return {"stage": "android_bundle", "status": "TODO",
            "abi": "arm64-v8a", "minSdk": 28,
            "reason": "depends on quantize", "out": str(out_dir / "mundari_langpack")}


def run_pipeline(corpus: Corpus, cfg: MundariConfig) -> Dict[str, object]:
    """Orchestrate all stages. Safe to call without gated data (uses mock/baseline)."""
    qc = quality_checks(corpus)
    splits = make_splits(corpus, cfg)
    hyps = run_baseline(splits["test"], cfg)
    refs = [t for _, t in splits["test"].pairs]
    rep: MundariEval = evaluate(refs, hyps)
    ft = finetune(splits["train"], splits["dev"], cfg)
    ex = export_onnx(None, cfg)
    qz = quantize(".")
    ab = prepare_android_bundle(".")
    return {
        "quality_checks": qc,
        "splits": {k: len(v) for k, v in splits.items()},
        "eval": rep.as_dict(),
        "finetune": ft, "export_onnx": ex, "quantize": qz, "android_bundle": ab,
    }
