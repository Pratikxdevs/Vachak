"""Benchmark device matrix (PHASE 11).

We measure on three reference devices. Critically, dev-machine latency is NEVER
reported as Android latency — the harness enforces this (see harness.forbid_dev_as_android).
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class DeviceSpec:
    name: str
    ram_gb: float
    android_version: int | None       # None for a non-Android dev machine
    is_android: bool
    notes: str = ""

    @property
    def label(self) -> str:
        if self.android_version:
            return f"{self.name} (Android {self.android_version}, {self.ram_gb}GB)"
        return f"{self.name} (dev machine)"


DEV_MACHINE = DeviceSpec(
    name="Dev Machine", ram_gb=16.0, android_version=None, is_android=False,
    notes="x86_64 build/CI host. Latency/RAM here is NOT representative of Android.",
)

ANDROID_2GB = DeviceSpec(
    name="Target Tablet", ram_gb=2.0, android_version=9, is_android=True,
    notes="Primary SIH target: Android 9, 2GB RAM, arm64-v8a.",
)

ANDROID_MODERN = DeviceSpec(
    name="Reference Tablet", ram_gb=4.0, android_version=13, is_android=True,
    notes="Modern reference device for comparison only.",
)

DEVICE_MATRIX = [ANDROID_2GB, ANDROID_MODERN, DEV_MACHINE]

PENDING = "PENDING REAL-DEVICE MEASUREMENT"


__all__ = ["DeviceSpec", "DEV_MACHINE", "ANDROID_2GB", "ANDROID_MODERN",
           "DEVICE_MATRIX", "PENDING"]
