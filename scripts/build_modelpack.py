#!/usr/bin/env python3
"""Build an offline language pack from local model files.

Usage: python3 scripts/build_modelpack.py --lang mund --version 0.1.0 \
       --out modelpacks/mundari --asr asr/model.onnx --mt mt/model.onnx --tts tts/model.onnx

Produces <out>/manifest.json consumable by offline/model_registry.ModelRegistry
and the Android SyncManager. Fully offline; no network.
"""
from __future__ import annotations

import argparse
from pathlib import Path

from backend.sync.sync_service import assemble_pack


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", required=True)
    ap.add_argument("--version", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--asr")
    ap.add_argument("--mt")
    ap.add_argument("--tts")
    args = ap.parse_args()

    models = {}
    for kind, path in (("asr", args.asr), ("mt", args.mt), ("tts", args.tts)):
        if path:
            models[kind] = path
    out = assemble_pack(args.out, args.lang, args.version, models)
    print(f"pack written to {out}")


if __name__ == "__main__":
    main()
