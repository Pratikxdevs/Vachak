#!/usr/bin/env python3
"""SUPERSEDED — do not use (kept as a versioned record of a wrong turn).

Sep-2026 history: this script assumed sherpa-onnx feeds NeMo CTC features as
[batch, time, 80] and rewrote the graph input to [B,T,80] behind a leading
Transpose. That premise was INVERTED: sherpa's `OfflineNemoEncDecCtcModel::
Forward` transposes fbank (B,T,80) -> (B,80,T) itself (`Transpose12`), so the
model must expect channel-first [B,80,T] (the original AI4Bharat export). The
rewritten bytes threw `index: 2 Got: <T> Expected: 80` on EVERY device decode
(logcat Vachak-ASR, Sep 10 2026).

`scripts/revert_asr_layout_fix.py` undid the rewrite. This script is now a
safe no-op guard: it refuses to touch a correct [B,80,T] model.
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
    # SUPERSEDED: always False. A [B,80,T] model (dims[1]==80) is the CORRECT
    # sherpa-native layout -- "fixing" it is what broke every decode. See
    # scripts/revert_asr_layout_fix.py.
    return False


def apply_fix(path: Path) -> bool:
    print(f"SKIP (superseded -- model must stay [B,80,T], see revert_asr_layout_fix.py): {path}")
    return False


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
