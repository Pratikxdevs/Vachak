#!/usr/bin/env python3
"""
patch_piper_for_sherpa.py — reproduce the interim Santali voice pack.

Downloads hi_IN-pratham-medium (Piper Hindi VITS) and patches it for
sherpa-onnx OfflineTts (Piper path). The patched ONNX is
modelpacks/piper-hi-base/hi-sat-interim.onnx (git-ignored, reproducible).

  python scripts/patch_piper_for_sherpa.py

Patches (see ml/tts/INTERIM_VOICE.md for why each is required):
  sample_rate=22050, n_speakers=1, language=hi, comment=piper, voice=hi
Plus tokens.txt generated from the voice's phoneme_id_map.

No network at runtime; network only here at pack-build time.
"""

from __future__ import annotations

import json
import urllib.request
from pathlib import Path

BASE = ("https://huggingface.co/rhasspy/piper-voices/resolve/main"
        "/hi/hi_IN/pratham/medium/hi_IN-pratham-medium")
OUT = Path("modelpacks/piper-hi-base")
OUT.mkdir(parents=True, exist_ok=True)


def fetch(name: str) -> Path:
    dest = OUT / name
    if dest.exists():
        print(f"[pack] exists {dest} ({dest.stat().st_size / 1e6:.1f} MB)")
        return dest
    print(f"[pack] downloading {BASE}/{name} ...")
    urllib.request.urlretrieve(f"{BASE}/{name}", dest)
    print(f"[pack] saved {dest} ({dest.stat().st_size / 1e6:.1f} MB)")
    return dest


def main() -> None:
    import onnx

    fetch("hi.onnx")
    cfg_path = fetch("hi.onnx.json")
    cfg = json.loads(cfg_path.read_text(encoding="utf-8"))

    tokens = OUT / "tokens.txt"
    lines = [f"{ph} {ids[0]}" for ph, ids in cfg["phoneme_id_map"].items()]
    tokens.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"[pack] tokens.txt ({len(lines)} phonemes)")

    m = onnx.load(str(OUT / "hi.onnx"))

    def setm(k: str, v: str) -> None:
        for p in m.metadata_props:
            if p.key == k:
                p.value = v
                return
        p = m.metadata_props.add()
        p.key = k
        p.value = v

    setm("sample_rate", "22050")
    setm("n_speakers", "1")
    setm("language", "hi")
    setm("comment", "piper")
    setm("voice", "hi")
    patched = OUT / "hi-sat-interim.onnx"
    onnx.save(m, str(patched))
    print(f"[pack] {patched} ({patched.stat().st_size / 1e6:.1f} MB)")
    print("[pack] verify: python ml/tts/audible_check.py "
          "(OfflineTts generate 'ᱡᱚᱦᱟᱨ' > 0.2*sampleRate)")


if __name__ == "__main__":
    main()
