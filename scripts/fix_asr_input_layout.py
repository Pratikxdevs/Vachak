#!/usr/bin/env python3
"""Fix Vachak Hindi ASR input layout (one-time model correction).

ROOT CAUSE (Sep 2026): `android/.../vachak_models/asr/model.onnx` (AI4Bharat
IndicConformer export) expects filterbank input as [batch, 80, time]
(channel-first), but the sherpa-onnx runtime
(`OfflineNemoEncDecCtcModel` <- `offline-recognizer-ctc-impl.h`) ALWAYS feeds
features as [batch, time, 80] (time-major). Every decode threw
`INVALID_ARGUMENT`, which the app swallowed and reported as
"[ASR:VAD] No speech detected" — live Hindi transcription could never work.

FIX: insert a leading Transpose [0,2,1] so the graph accepts sherpa's native
[batch, time, 80] layout. Weights, metadata (vocab/subsample/normalize),
outputs and `length` semantics are untouched. The pre-fix bytes remain
recoverable from git history; this script is the versioned record of the
change (re-run is a no-op once applied).

Verification performed after running:
  1. ORT probe with sherpa-shaped input [1,T,80] runs, logprobs [1,T',5633].
  2. Greedy CTC decode of espeak-ng Hindi audio yields Devanagari text.
"""
import sys
from pathlib import Path

import onnx

REPO = Path(__file__).resolve().parent.parent
ASR_MODELS = [
    REPO / "android/app/src/main/assets/vachak_models/asr/model.onnx",
]

INPUT_NAME = "audio_signal"
LENGTH_NAME = "length"


def needs_fix(model: onnx.ModelProto) -> bool:
    for i in model.graph.input:
        if i.name == INPUT_NAME:
            dims = [d.dim_value for d in i.type.tensor_type.shape.dim]
            # [B, 80, T] (dim_param on B/T ok) -> needs fix.
            # [B, T, 80] -> already fixed.
            if len(dims) == 3 and dims[1] == 80:
                return True
            return False
    raise RuntimeError(f"input {INPUT_NAME!r} not found in graph")


def apply_fix(path: Path) -> bool:
    model = onnx.load(str(path))
    if not needs_fix(model):
        print(f"SKIP (already sherpa layout): {path}")
        return False
    # Keep the graph input name ("sherpa feeds by model-declared names") but
    # change its layout to sherpa-native [batch, time, 80]; rewire every
    # downstream consumer to a fresh channel-first tensor behind a Transpose.
    cf_name = "feat_cf"
    for i in model.graph.input:
        if i.name == INPUT_NAME:
            dims = i.type.tensor_type.shape.dim
            dims[1].dim_value = 0
            dims[1].dim_param = "T"
            dims[2].dim_value = 80
            dims[2].ClearField("dim_param")
            new_in = i
    for node in model.graph.node:
        for k, n in enumerate(node.input):
            if n == INPUT_NAME:
                node.input[k] = cf_name
    transpose = onnx.helper.make_node(
        "Transpose",
        inputs=[INPUT_NAME],
        outputs=[cf_name],
        perm=[0, 2, 1],
        name="sherpa_layout_transpose",
    )
    model.graph.node.insert(0, transpose)
    onnx.checker.check_model(model)
    onnx.save(model, str(path))
    print(f"FIXED: {path} now accepts [B,T,80]")
    return True


def main() -> int:
    changed = False
    for p in ASR_MODELS:
        if not p.exists():
            print(f"MISSING: {p}")
            return 1
        changed |= apply_fix(p)
    print("CHANGED" if changed else "NO-OP")
    return 0


if __name__ == "__main__":
    sys.exit(main())
