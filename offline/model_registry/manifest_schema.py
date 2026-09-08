"""PHASE 2B — language-pack manifest schema and validation.

Manifest JSON shape (see modelpacks/mundari/manifest.json for a real example):

{
  "language": "mund",
  "version": "0.1.0",
  "minAndroid": 28,
  "checksum": "<sha256 of the concatenated model checksums>",
  "models": [
    { "name": "asr_hi", "file": "asr/model.onnx", "sizeBytes": 0,
      "checksum": "<sha256>", "kind": "asr" },
    ...
  ]
}

All checksums are SHA-256 hex. Files referenced are relative to the pack root.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import List


@dataclass
class ModelEntry:
    name: str
    file: str
    sizeBytes: int
    checksum: str
    kind: str  # asr | mt | tts


@dataclass
class PackManifest:
    language: str
    version: str
    minAndroid: int
    checksum: str
    models: List[ModelEntry]

    @classmethod
    def load(cls, path: str | Path) -> "PackManifest":
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        return cls.from_dict(data)

    @classmethod
    def from_dict(cls, data: dict) -> "PackManifest":
        required = {"language", "version", "minAndroid", "checksum", "models"}
        missing = required - data.keys()
        if missing:
            raise ValueError(f"manifest missing keys: {sorted(missing)}")
        models = [ModelEntry(**m) for m in data["models"]]
        return cls(
            language=data["language"],
            version=data["version"],
            minAndroid=int(data["minAndroid"]),
            checksum=str(data["checksum"]),
            models=models,
        )

    def to_dict(self) -> dict:
        return {
            "language": self.language,
            "version": self.version,
            "minAndroid": self.minAndroid,
            "checksum": self.checksum,
            "models": [asdict(m) for m in self.models],
        }

    def save(self, path: str | Path) -> None:
        Path(path).write_text(json.dumps(self.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8")


def sha256_file(path: str | Path) -> str:
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def compute_pack_checksum(models: List[ModelEntry]) -> str:
    """Pack-level checksum = sha256 of sorted (name:checksum) pairs."""
    h = hashlib.sha256()
    for m in sorted(models, key=lambda x: x.name):
        h.update(f"{m.name}:{m.checksum}\n".encode("utf-8"))
    return h.hexdigest()
