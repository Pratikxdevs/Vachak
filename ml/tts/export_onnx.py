"""ONNX export helper for VITS-style Mundari TTS (scaffolding reference).

References microsoft/MunTTS (VITS/XTTS) topology. No real export runs here because
the final Mundari VITS checkpoint does not exist yet. The function documents the
exact subgraphs that must be exported and validates the output ONNX when present.
"""

from __future__ import annotations

from pathlib import Path
from typing import Dict, Optional


VITS_SUBGRAPHS = ["text_encoder", "decoder", "flow", "vocoder (hi-fi-gan)"]


def export_vits_onnx(checkpoint_dir: str, out_dir: str, sample_rate: int = 22050) -> Dict[str, object]:
    """Would trace and export each VITS subgraph to ONNX.

    Returns a TODO plan plus the required subgraph list so the export contract is
    fixed before the model exists.
    """
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    return {
        "status": "TODO",
        "reason": "DATA ACCESS PENDING: final Mundari VITS checkpoint not available",
        "subgraphs": VITS_SUBGRAPHS,
        "sample_rate": sample_rate,
        "out": str(out),
        "note": "Mirror sherpa-onnx VITS export; validate with onnx.checker",
    }


def verify_onnx(model_path: str) -> bool:
    try:
        import onnx
        m = onnx.load(model_path)
        onnx.checker.check_model(m)
        return True
    except Exception:
        return False
