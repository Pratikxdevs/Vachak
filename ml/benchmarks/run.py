"""Benchmark runner CLI (PHASE 11).

Offline. Measures what it can, marks the rest PENDING. Never reports dev-machine
latency as Android latency.

    python -m ml.benchmarks.run --device android2gb --out report.json
    python -m ml.benchmarks.run --all

Device choices: android2gb | android_modern | dev
Use --real-android ONLY on an actual device; otherwise latency metrics stay PENDING.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from ml.benchmarks.device_matrix import ANDROID_2GB, ANDROID_MODERN, DEV_MACHINE  # noqa: E402
from ml.benchmarks.harness import BenchmarkHarness, ModelAvailability  # noqa: E402

DEVICES = {"android2gb": ANDROID_2GB, "android_modern": ANDROID_MODERN, "dev": DEV_MACHINE}


def main() -> int:
    ap = argparse.ArgumentParser(description="Vachak offline benchmark harness")
    ap.add_argument("--device", default="android2gb", choices=list(DEVICES))
    ap.add_argument("--real-android", action="store_true",
                    help="ONLY set when actually running on the Android device")
    ap.add_argument("--out", default=None, help="write JSON report here")
    ap.add_argument("--models", action="store_true",
                    help="claim on-device models are wired (still PENDING without callable)")
    args = ap.parse_args()

    device = DEVICES[args.device]
    models = ModelAvailability(nmt=args.models, asr=args.models, tts=args.models,
                               note="models wired" if args.models else "no on-device models")
    harness = BenchmarkHarness(device=device, real_android=args.real_android,
                               models=models)
    try:
        report = harness.run_all()
    except ValueError as e:
        print(f"ERROR: {e}")
        return 2

    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"\n[bench] wrote {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
