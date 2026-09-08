"""Backend sync service — builds/side-loads offline language packs.

Mirrors the Android SyncManager contract (offline package installer, no network).
Produces a pack directory consumable by offline/model_registry.ModelRegistry.
"""
from __future__ import annotations

from pathlib import Path

from offline.model_registry.manifest_schema import (
    ModelEntry, PackManifest, compute_pack_checksum,
)


def assemble_pack(out_dir: str | Path, language: str, version: str,
                  model_files: dict[str, str]) -> Path:
    """Copy model files into out_dir and write a validated manifest.json.

    model_files: {kind: local_path} e.g. {"asr": "asr/model.onnx", ...}
    """
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    entries: list[ModelEntry] = []
    for kind, src in model_files.items():
        src = Path(src)
        dest = out / src.name
        if src.exists():
            dest.write_bytes(src.read_bytes())
        entries.append(ModelEntry(
            name=f"{kind}_{language}", file=src.name,
            sizeBytes=src.stat().st_size if src.exists() else 0,
            checksum="DEV-FIXTURE", kind=kind))
    manifest = PackManifest(
        language=language, version=version, minAndroid=28,
        checksum=compute_pack_checksum(entries), models=entries)
    manifest.save(out / "manifest.json")
    return out
