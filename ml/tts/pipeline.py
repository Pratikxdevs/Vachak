"""Santali (Ol Chiki) TTS training/export pipeline — now Santali-only (02-01).

Active path: Santali VITS (sat_Olck, U+1C50–U+1C7F char tokens, 22.05k mono) via
`ml/tts/dataset/prepare_santali.py` → `ml/tts/train.py` → `models/vits-sat.onnx`
(Coqui VITS + HiFi-GAN, sherpa-onnx). The Mundari scaffolding below is retained
for backwards-compat with `ml/tts/tests/test_tts.py` (dev fixture Hindi
"परीक्षण।"), but the production target is Santali per AGENTS.md: Santali-only
pivot and docs/PHASES.md Phase 2.

Stages (mirrors the deploy path):
  prepare_data -> train_vits -> export_onnx -> quantize_onnx -> convert_sherpa
   -> prepare_android_bundle

Architecture reference: microsoft/MunTTS (VITS/XTTS topology reused for Santali
Ol Chiki char tokens). Real training config (Coqui VITS, 22.05k, 300–800 epochs,
4-loss, batch 16 on RTX3050 / 64 on A100) is documented in
`ml/tts/runs/santali_vits/train_config.json` and `ml/tts/train.py`.

No network. Stages needing gated Santali speech return a TODO plan when audio
is not on disk; the shim `ml/tts/train.py` creates a valid ONNX (opset17,
40 MB, checker PASS) without GPU for CI.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional

from .adapter import FallbackTTSAdapter, get_adapter
from .evaluate import evaluate_voice


@dataclass
class TTSConfig:
    base_arch: str = "vits"          # reference: microsoft/MunTTS (VITS/XTTS)
    target_lang: str = "mun"         # Mundari
    sample_rate: int = 22050
    adapter_kind: str = "fallback"   # fallback | final (final not available)
    out_dir: str = "ml/tts/runs"


def prepare_data(manifest: List[str]) -> Dict[str, object]:
    """Quality-check a speech manifest (list of text+audio pairs)."""
    issues = {"empty": 0, "missing_audio": 0}
    for item in manifest:
        if not item or not item.strip():
            issues["empty"] += 1
    return {"ok": sum(issues.values()) == 0, "n": len(manifest), "issues": issues,
            "note": "DATA ACCESS PENDING: gated Mundari speech not loaded"}


def train_vits(train_manifest: List[str], cfg: TTSConfig, run_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would train/fine-tune a VITS Mundari voice. Scaffolded."""
    run_dir = Path(run_dir or cfg.out_dir)
    plan = {"stage": "train_vits", "status": "TODO", "arch": cfg.base_arch,
            "target": cfg.target_lang, "samples": len(train_manifest),
            "reason": "DATA ACCESS PENDING: gated Mundari speech not available",
            "produces": "ml/tts/runs/mundari_vits (drop-in adapter, NOT shipped yet)"}
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "train_plan.json").write_text(json.dumps(plan, indent=2), encoding="utf-8")
    return plan


def export_onnx(model_dir: str, cfg: Optional[TTSConfig] = None, out_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would export VITS -> ONNX (encoder/decoder+vocoder). Scaffolded."""
    cfg = cfg or TTSConfig()
    out_dir = Path(out_dir or cfg.out_dir)
    return {"stage": "export_onnx", "status": "TODO", "arch": cfg.base_arch,
            "reason": "final Mundari VITS not produced yet",
            "out": str(out_dir / "mundari_vits_onnx")}


def quantize_onnx(onnx_dir: str, out_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would quantize ONNX (int8/int4) for the 2GB device budget."""
    out_dir = Path(out_dir or "ml/tts/runs")
    return {"stage": "quantize_onnx", "status": "TODO", "method": "onnxruntime int8/int4",
            "reason": "depends on export_onnx", "out": str(out_dir / "mundari_vits_onnx_quant")}


def convert_sherpa(quant_dir: str, cfg: Optional[TTSConfig] = None, out_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would wrap quantized ONNX into sherpa-onnx model/streaming format."""
    out_dir = Path(out_dir or "ml/tts/runs")
    return {"stage": "convert_sherpa", "status": "TODO", "runtime": "sherpa-onnx",
            "reason": "depends on quantize_onnx", "out": str(out_dir / "mundari_sherpa")}


def prepare_android_bundle(sherpa_dir: str, cfg: Optional[TTSConfig] = None, out_dir: Optional[Path] = None) -> Dict[str, object]:
    """Would package the sherpa-onnx voice into the Android language pack."""
    out_dir = Path(out_dir or "ml/tts/runs")
    return {"stage": "android_bundle", "status": "TODO", "abi": "arm64-v8a", "minSdk": 28,
            "reason": "depends on convert_sherpa", "out": str(out_dir / "mundari_voicepack")}


def run_pipeline(train_manifest: List[str], cfg: TTSConfig) -> Dict[str, object]:
    data = prepare_data(train_manifest)
    tr = train_vits(train_manifest, cfg)
    ex = export_onnx(".")
    qz = quantize_onnx(".")
    sh = convert_sherpa(".")
    ab = prepare_android_bundle(".")
    # smoke-synthesize one utterance through the fallback stand-in
    eng = get_adapter(cfg.adapter_kind)
    sample_wav = eng.synthesize("परीक्षण") if train_manifest else b""
    voice = evaluate_voice("परीक्षण", sample_wav)
    return {"prepare_data": data, "train_vits": tr, "export_onnx": ex,
            "quantize_onnx": qz, "convert_sherpa": sh, "android_bundle": ab,
            "fallback_voice_eval": voice.as_dict()}
