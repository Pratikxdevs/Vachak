#!/usr/bin/env python3
"""Revert the Sep-2026 ASR layout "fix" (one-time model correction).

WHY (Sep 2026, device log proof): `fix_asr_input_layout.py` assumed sherpa-onnx
feeds NeMo CTC features as [batch, time, 80] and rewrote the graph input to
[B,T,80] behind a leading Transpose. Wrong: sherpa's
`OfflineNemoEncDecCtcModel::Forward` (sherpa-onnx/csrc/...) computes fbank as
(B,T,80) and then applies `Transpose12` itself -- `(B, T, C) -> (B, C, T)` --
before `sess_->Run`. So the model must expect channel-first [B,80,T] (the
original AI4Bharat IndicConformer export). The "fixed" bytes throw on EVERY
decode on device:

  OfflineRecognizer_decode: Got invalid dimensions for input: audio_signal
    index: 2 Got: 110 Expected: 80      (110 = time frames, fed time-last)

This script removes the `sherpa_layout_transpose` node, rewires consumers back
to `audio_signal`, and restores the [B,80,T] input declaration. Idempotent:
re-run is a no-op once reverted. Stale-device note: `SherpaAssets` re-copies
on manifest (name->bytes) mismatch, and the reverted bytes differ in size, so
tablets pick the fix up on next launch (or clear app data / reinstall).
"""
import sys
from pathlib import Path

import onnx

REPO = Path(__file__).resolve().parent.parent
ASR_MODELS = [
    REPO / "android/app/src/main/assets/vachak_models/asr/model.onnx",
]

INPUT_NAME = "audio_signal"
CF_NAME = "feat_cf"
TRANSPOSE_NAME = "sherpa_layout_transpose"


def needs_revert(model: onnx.ModelProto) -> bool:
    return any(
        n.name == TRANSPOSE_NAME and n.op_type == "Transpose"
        for n in model.graph.node
    )


def apply_revert(path: Path) -> bool:
    model = onnx.load(str(path))
    if not needs_revert(model):
        print(f"SKIP (already channel-first [B,80,T]): {path}")
        return False
    # Drop the inserted Transpose; rewire consumers to the graph input.
    for n in [n for n in model.graph.node if n.name == TRANSPOSE_NAME and n.op_type == "Transpose"]:
        model.graph.node.remove(n)
    for node in model.graph.node:
        for k, n in enumerate(node.input):
            if n == CF_NAME:
                node.input[k] = INPUT_NAME
    # Restore [B,80,T]: dim1 fixed 80, dim2 dynamic time.
    for i in model.graph.input:
        if i.name == INPUT_NAME:
            dims = i.type.tensor_type.shape.dim
            assert len(dims) == 3, f"expected rank 3, got {len(dims)}"
            dims[1].dim_value = 80
            dims[1].ClearField("dim_param")
            dims[2].dim_value = 0
            dims[2].dim_param = "T"
    onnx.checker.check_model(model)
    onnx.save(model, str(path))
    print(f"REVERTED: {path} now accepts sherpa-native [B,80,T]")
    return True


def main() -> int:
    changed = False
    for p in ASR_MODELS:
        if not p.exists():
            print(f"MISSING: {p}")
            return 1
        changed |= apply_revert(p)
    print("CHANGED" if changed else "NO-OP")
    return 0


if __name__ == "__main__":
    sys.exit(main())
