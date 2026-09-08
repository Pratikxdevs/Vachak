"""Content pack builder (PHASE 12).

Packages precomputed curriculum content + language-pack manifest into a single
offline artifact for side-load install (NOT a network download). Lean: zips a
lessons directory into packages/<packId>.zip.

    python scripts/pack.py --lessons scripts/sample_data --packId fln_hi_mund_v1
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import zipfile

_ROOT = os.path.dirname(os.path.dirname(__file__))


def build_pack(lessons_dir: str, pack_id: str, out_dir: str = "packages") -> str:
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, f"{pack_id}.zip")
    files = []
    for root, _, fs in os.walk(lessons_dir):
        for f in fs:
            files.append(os.path.join(root, f))
    if not files:
        raise SystemExit(f"no files in {lessons_dir}")
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        for fp in files:
            z.write(fp, os.path.relpath(fp, lessons_dir))
        # manifest
        manifest = {
            "packId": pack_id,
            "kind": "curriculum",
            "languages": ["hi", "mund"],
            "offline": True,
            "files": [os.path.relpath(f, lessons_dir) for f in files],
        }
        z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
    return out_path


def main() -> int:
    ap = argparse.ArgumentParser(description="Build an offline content pack")
    ap.add_argument("--lessons", required=True)
    ap.add_argument("--packId", required=True)
    ap.add_argument("--out", default="packages")
    args = ap.parse_args()
    path = build_pack(args.lessons, args.packId, args.out)
    size = os.path.getsize(path) / 1024.0
    print(f"[pack] wrote {path} ({size:.1f} KB) — side-load only, no network.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
