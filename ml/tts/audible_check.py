#!/usr/bin/env python3
"""
audible_check.py — re-runnable proof that the interim Santali voice speaks.

Loads modelpacks/piper-hi-base/hi-sat-interim.onnx via sherpa-onnx
OfflineTts (Piper path, espeak-ng 'hi' frontend) and asserts each test
utterance yields >200ms of non-silent audio within the TTS latency slice.

  python ml/tts/audible_check.py
  python ml/tts/audible_check.py --write-wav   # + ml/tts/runs proof wavs

No network. Uses system Python (sherpa-onnx + numpy only).
"""

from __future__ import annotations

import argparse
import sys
import time
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ml" / "tts" / "dataset"))
from transliterate import normalize_mt, ol_to_dev  # noqa: E402

PACK = ROOT / "modelpacks" / "piper-hi-base" / "hi-sat-interim.onnx"
TOKENS = ROOT / "modelpacks" / "piper-hi-base" / "tokens.txt"
RUNS = ROOT / "ml" / "tts" / "runs" / "santali_vits"

TESTS = [
    ("johaar", "ᱡᱚᱦᱟᱨ"),
    ("mt-question", "ᱪᱮᱫ ᱱᱚᱣᱟ ᱫᱤᱱ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?"),
    ("lesson-line", "ᱟᱞᱮ ᱟᱥᱲᱟ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟ"),
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--write-wav", action="store_true")
    ap.add_argument("--data-dir", default="/usr/share/espeak-ng-data")
    args = ap.parse_args()

    import numpy as np
    import sherpa_onnx

    assert PACK.exists(), f"missing {PACK} — run scripts/patch_piper_for_sherpa.py"
    v = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=str(PACK), lexicon="", tokens=str(TOKENS),
        data_dir=args.data_dir)
    mc = sherpa_onnx.OfflineTtsModelConfig(vits=v, num_threads=1,
                                           provider="cpu")
    tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=mc))
    print(f"[check] sr={tts.sample_rate} "
          f"min_samples={int(0.2 * tts.sample_rate)}")

    failed = 0
    for name, ol in TESTS:
        dev = ol_to_dev(normalize_mt(ol))
        t0 = time.time()
        audio = tts.generate(dev, sid=0, speed=1.0)
        dt = time.time() - t0
        n = len(audio.samples)
        a = np.array(audio.samples)
        rms = float((a ** 2).mean() ** 0.5)
        ok = n > 0.2 * tts.sample_rate and rms > 0.02 and dt <= 1.0
        failed += not ok
        print(f"[check] {'PASS' if ok else 'FAIL'} {name}: "
              f"{n} samples @ {tts.sample_rate}Hz, wall {dt:.2f}s, "
              f"rms {rms:.4f} (Ol {ol[:30]} -> Dev {dev[:30]})")
        if args.write_wav and n > 0:
            import struct
            p = RUNS / f"audible_check_{name}.wav"
            w = wave.open(str(p), "w")
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(tts.sample_rate)
            ints = [max(-32768, min(32767, int(s * 32768)))
                    for s in audio.samples]
            w.writeframes(struct.pack("<%dh" % len(ints), *ints))
            w.close()
    print(f"[check] {'ALL PASS' if not failed else f'{failed} FAILED'}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
