"""Backend API (service side) — PHASE 1 skeleton.

NOTE: This is NOT a runtime inference server. The Android app is fully offline.
This service exists only to BUILD and SIDE-LOAD language packs (SyncManager
consumes them locally). It must never be called from the device at runtime.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class PackBuildRequest:
    language: str
    version: str
    min_android: int
    asr_model: str | None = None
    mt_model: str | None = None
    tts_model: str | None = None


def build_pack(req: PackBuildRequest) -> dict:
    """Placeholder pack-build entrypoint. Real impl calls ml/conversion + the
    model_registry manifest writer. Returns a manifest-shaped dict."""
    return {
        "language": req.language,
        "version": req.version,
        "minAndroid": req.min_android,
        "checksum": "DEV-FIXTURE",
        "models": [],
    }


if __name__ == "__main__":
    print(build_pack(PackBuildRequest("mund", "0.1.0", 28)))
