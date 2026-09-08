"""Lazy bridge to the IndicTrans2 Hindi->Santali baseline (reference only).

This reuses the already-cloned IndicTrans2 pipeline as a LABELED STAND-IN for the
Mundari target. It is explicitly NOT Mundari and is used only to validate the
integration shape (tokenization, export, Android bundle) before the real Mundari
adapter exists.

Importing this module does not load any model; the heavy imports happen inside
load_session()/run() so unit tests that use the mock adapter stay torch-free.
"""

from __future__ import annotations

from pathlib import Path
from typing import List, Optional

_SCRIPTS_DIR = Path(__file__).resolve().parent.parent / "scripts"


def load_session(onnx_dir: Optional[str] = None):
    """Return a callable translator object, or raise if IndicTrans2 is unavailable.

    Raises RuntimeError (caught by BaselineTranslationEngine -> mock fallback) when
    the model checkpoint or IndicTransToolkit is not present in this environment.
    """
    import torch  # noqa: F401
    from transformers import AutoModelForSeq2SeqLM, AutoTokenizer  # noqa: F401
    try:
        from IndicTransToolkit import IndicProcessor  # type: ignore  # noqa: F401
    except Exception as exc:  # pragma: no cover - env dependent
        raise RuntimeError(f"IndicTransToolkit not importable: {exc}")

    sys_path = _SCRIPTS_DIR
    import sys
    if str(sys_path) not in sys.path:
        sys.path.insert(0, str(sys_path))
    try:
        from infer_it2 import IndicTrans2Translator  # type: ignore
    except Exception as exc:  # pragma: no cover - env dependent
        raise RuntimeError(f"IndicTrans2 inference wrapper unavailable: {exc}")

    translator = IndicTrans2Translator(device="cpu", use_fp16=False)
    translator._src = "hin_Deva"
    translator._tgt = "sat_Olck"
    return translator


def run(session, text: str) -> str:
    return session.translate([text], src_lang=session._src, tgt_lang=session._tgt)[0]
