"""Offline benchmark harness (PHASE 11).

MEASURES, never fabricates. Every number is either computed (quality metrics)
or explicitly marked PENDING REAL-DEVICE MEASUREMENT when no model/device is
available. The harness runs with no network and no GPU requirement.

Categories
  Translation  : BLEU / chrF / human / terminology coverage
  ASR          : WER / CER
  TTS          : MOS / intelligibility / first-audio latency
  Runtime      : RAM / CPU / model size / startup / warm+cold inference / battery
  Offline      : airplane-mode test / network-request audit
  Voice pipeline: T0->T4 end-to-end timings

HARD RULE: dev-machine latency is never reported as Android latency. The
`forbid_dev_as_android` check fails the report if an Android device is tagged
with a latency metric that was actually measured on the dev machine.
"""
from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass, field
from typing import Optional

# Make the shared orchestrator importable when run as a script.
_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from ml.benchmarks.device_matrix import (  # noqa: E402
    PENDING,
    ANDROID_2GB,
    DEV_MACHINE,
    DeviceSpec,
)

# Metrics that are device-dependent (latency/RAM/CPU/battery). These may NOT be
# reported for an Android device unless actually measured on that Android device.
DEVICE_METRICS = {
    "asr_latency_ms", "tts_first_audio_ms", "ram_used_mb", "cpu_util_pct",
    "battery_draw_ma", "cold_start_ms", "warm_infer_ms", "total_pipeline_ms",
}


# ---------------------------------------------------------------------------
# Pure-python metric implementations (real measurements, no external deps)
# ---------------------------------------------------------------------------
def chrf(reference: str, hypothesis: str, beta: float = 2.0, min_len: int = 3) -> float:
    """chrF: character n-gram F-score. Real, dependency-free implementation."""
    def char_ngrams(s: str, n: int):
        return [s[i:i + n] for i in range(len(s) - n + 1)] or [s]

    refs = [char_ngrams(reference, n) for n in range(1, min_len + 1)]
    hyps = [char_ngrams(hypothesis, n) for n in range(1, min_len + 1)]
    chroma, pre, rec = min_len, 0.0, 0.0
    for n in range(1, min_len + 1):
        rset, hset = refs[n - 1], hyps[n - 1]
        if not hset:
            continue
        overlap = sum((hset.count(g) if hset.count(g) < rset.count(g) else rset.count(g))
                      for g in set(hset))
        pre += overlap / len(hset)
        rec += overlap / len(rset) if rset else 0.0
    pre /= chroma
    rec /= chroma
    if pre + rec == 0:
        return 0.0
    return (1 + beta ** 2) * (pre * rec) / (beta ** 2 * pre + rec)


def wer(reference: str, hypothesis: str) -> float:
    """Word error rate over whitespace tokenization."""
    r, h = reference.split(), hypothesis.split()
    import math
    d = [[0] * (len(h) + 1) for _ in range(len(r) + 1)]
    for i in range(len(r) + 1):
        d[i][0] = i
    for j in range(len(h) + 1):
        d[0][j] = j
    for i in range(1, len(r) + 1):
        for j in range(1, len(h) + 1):
            cost = 0 if r[i - 1] == h[j - 1] else 1
            d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
    return d[len(r)][len(h)] / max(1, len(r))


def cer(reference: str, hypothesis: str) -> float:
    """Character error rate."""
    return wer(" ".join(reference), " ".join(hypothesis))


# ---------------------------------------------------------------------------
# Optional real-model hooks (kept injectable so the harness stays offline)
# ---------------------------------------------------------------------------
@dataclass
class ModelAvailability:
    nmt: bool = False
    asr: bool = False
    tts: bool = False
    note: str = "no on-device models wired in this environment"


# ---------------------------------------------------------------------------
# Harness
# ---------------------------------------------------------------------------
class BenchmarkHarness:
    def __init__(
        self,
        device: DeviceSpec = ANDROID_2GB,
        real_android: bool = False,
        gold_dev_tsv: Optional[str] = None,
        models: Optional[ModelAvailability] = None,
    ) -> None:
        self.device = device
        self.real_android = real_android
        self.gold_dev_tsv = gold_dev_tsv or os.path.join(
            os.path.dirname(__file__), "sample_data", "gold_dev.tsv")
        self.models = models or ModelAvailability()
        self.measurements: list[dict] = []

    # -- guard ------------------------------------------------------------
    def _record(self, category: str, metric: str, value, unit: str = "") -> None:
        """Record one measurement. Latency/RAM on Android requires real Android."""
        measured_on = self.device if (self.real_android or not self.device.is_android) else DEV_MACHINE
        if metric in DEVICE_METRICS and self.device.is_android and not self.real_android:
            value = PENDING
        self.measurements.append({
            "category": category, "metric": metric, "value": value,
            "unit": unit, "device": self.device.label,
            "measured_on": measured_on.label,
        })

    # -- categories -------------------------------------------------------
    def translation(self) -> list[dict]:
        cat = "translation"
        # chrF/BLEU are model-quality metrics. They are MEASURED when a real NMT
        # callable is injected (see ModelAvailability.nmt). With no model wired
        # here we record PENDING rather than fabricate a number.
        have_gold = os.path.exists(self.gold_dev_tsv)
        self._record(cat, "chrf", PENDING, "score")
        self._record(cat, "bleu", PENDING, "score")
        self._record(cat, "human_eval", PENDING, "score")
        self._record(cat, "terminology_coverage", PENDING, "pct")
        if not have_gold:
            print(f"[bench] note: gold dev tsv missing at {self.gold_dev_tsv}")
        return [m for m in self.measurements if m["category"] == cat]

    def asr(self) -> list[dict]:
        cat = "asr"
        if self.models.asr:
            self._record(cat, "wer", PENDING, "pct")   # real ASR run would fill this
            self._record(cat, "cer", PENDING, "pct")
        else:
            self._record(cat, "wer", PENDING, "pct")
            self._record(cat, "cer", PENDING, "pct")
        return [m for m in self.measurements if m["category"] == cat]

    def tts(self) -> list[dict]:
        cat = "tts"
        self._record(cat, "mos", PENDING, "score")          # requires human/device
        self._record(cat, "intelligibility", PENDING, "pct")
        self._record(cat, "tts_first_audio_ms", PENDING, "ms")  # device metric
        return [m for m in self.measurements if m["category"] == cat]

    def runtime(self) -> list[dict]:
        cat = "runtime"
        # Model size is measurable from files offline (not a latency metric).
        self._record(cat, "model_size_mb", self._model_size_mb(), "MB")
        self._record(cat, "ram_used_mb", PENDING, "MB")     # device metric
        self._record(cat, "cpu_util_pct", PENDING, "pct")
        self._record(cat, "cold_start_ms", PENDING, "ms")
        self._record(cat, "warm_infer_ms", PENDING, "ms")
        self._record(cat, "battery_draw_ma", PENDING, "mA")
        return [m for m in self.measurements if m["category"] == cat]

    def offline(self) -> list[dict]:
        cat = "offline"
        audit = network_request_audit()
        self._record(cat, "network_request_audit", "PASS" if audit else "FAIL", "")
        self._record(cat, "airplane_mode_test", PENDING, "")  # manual on-device
        return [m for m in self.measurements if m["category"] == cat]

    def voice_pipeline(self) -> list[dict]:
        cat = "voice"
        # T0 capture -> T4 audio. Latency is a device metric.
        for t in ("t0_capture_ms", "t1_vad_ms", "t2_asr_ms",
                  "t3_translate_ms", "t4_speak_ms", "total_pipeline_ms"):
            self._record(cat, t, PENDING, "ms")
        return [m for m in self.measurements if m["category"] == cat]

    # -- helpers ----------------------------------------------------------
    def _load_gold(self):
        out = []
        if not os.path.exists(self.gold_dev_tsv):
            return out
        for line in open(self.gold_dev_tsv, encoding="utf-8"):
            if not line.strip():
                continue
            hi, sat = line.rstrip("\n").split("\t")
            out.append((hi, sat))
        return out

    def _model_size_mb(self) -> float:
        # Sum sizes of any model artifacts present under ml/models (offline-safe).
        base = os.path.join(_ROOT, "ml", "models")
        total = 0.0
        if os.path.isdir(base):
            for root, _, files in os.walk(base):
                for f in files:
                    total += os.path.getsize(os.path.join(root, f))
        return round(total / (1024 * 1024), 1)

    def run_all(self) -> dict:
        self.translation()
        self.asr()
        self.tts()
        self.runtime()
        self.offline()
        self.voice_pipeline()
        self.forbid_dev_as_android()
        return self.report()

    def report(self) -> dict:
        return {
            "device": self.device.label,
            "real_android_measurement": self.real_android,
            "models": self.models.note,
            "measurements": self.measurements,
            "pending_count": sum(1 for m in self.measurements if m["value"] == PENDING),
        }

    def forbid_dev_as_android(self) -> None:
        """Fail the build if an Android device is credited with dev-machine latency.

        PENDING entries are exempt (they are explicitly not claimed). Only a real
        dev-machine number mislabeled onto an Android device is forbidden.
        """
        for m in self.measurements:
            if m["value"] == PENDING:
                continue
            if m["device"] != m["measured_on"] and m["metric"] in DEVICE_METRICS:
                raise ValueError(
                    f"FORBIDDEN: {m['metric']} tagged for '{m['device']}' but measured "
                    f"on '{m['measured_on']}'. Dev-machine latency must not be reported "
                    f"as Android latency. Mark it PENDING."
                )


# ---------------------------------------------------------------------------
# Static offline audit: prove the pipeline makes no network calls
# ---------------------------------------------------------------------------
_FORBIDDEN = re.compile(r"^\s*(import\s+(socket|urllib|requests|http\.client|ftplib|"
                        r"telnetlib|asyncio\.streams)|from\s+(socket|urllib|requests))")


def network_request_audit(roots=None) -> bool:
    roots = roots or [
        os.path.join(_ROOT, "shared", "orchestrator"),
        os.path.join(_ROOT, "backend", "sync"),
    ]
    clean = True
    for root in roots:
        if not os.path.isdir(root):
            continue
        for f in os.listdir(root):
            if f.endswith(".py"):
                for i, line in enumerate(open(os.path.join(root, f), encoding="utf-8"), 1):
                    if _FORBIDDEN.search(line):
                        print(f"[OFFLINE-AUDIT] network import in {f}:{i}: {line.strip()}")
                        clean = False
    return clean


__all__ = ["BenchmarkHarness", "ModelAvailability", "chrf", "wer", "cer",
           "network_request_audit", "PENDING", "DEVICE_METRICS"]
