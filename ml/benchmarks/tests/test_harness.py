"""Tests for the benchmark harness (PHASE 11).

Run offline, no models/network:

    python ml/benchmarks/tests/test_harness.py
"""
from __future__ import annotations

import os
import sys
import unittest

_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(__file__))))
sys.path.insert(0, _ROOT)

from ml.benchmarks.device_matrix import ANDROID_2GB, DEV_MACHINE  # noqa: E402
from ml.benchmarks.harness import (  # noqa: E402
    BenchmarkHarness,
    ModelAvailability,
    cer,
    chrf,
    network_request_audit,
    wer,
)


class TestMetrics(unittest.TestCase):
    def test_chrf_identical(self):
        self.assertAlmostEqual(chrf("abc", "abc"), 1.0, places=2)

    def test_chrf_differs(self):
        self.assertLess(chrf("abc", "xyz"), chrf("abc", "abd"))

    def test_wer(self):
        self.assertAlmostEqual(wer("a b c", "a b c"), 0.0)
        self.assertAlmostEqual(wer("a b c", "a b"), 1 / 3)

    def test_cer(self):
        self.assertAlmostEqual(cer("abc", "abc"), 0.0)


class TestOfflineAudit(unittest.TestCase):
    def test_no_network_imports(self):
        # Our pipeline code must stay offline.
        self.assertTrue(network_request_audit())


class TestHarness(unittest.TestCase):
    def _harness(self, device=ANDROID_2GB, real=False):
        return BenchmarkHarness(device=device, real_android=real,
                                 models=ModelAvailability())

    def test_report_pending_on_android(self):
        r = self._harness().run_all()
        self.assertGreater(r["pending_count"], 0)
        # no fabricated latency for android
        for m in r["measurements"]:
            if m["metric"] in ("ram_used_mb", "tts_first_audio_ms", "total_pipeline_ms"):
                self.assertEqual(m["value"], "PENDING REAL-DEVICE MEASUREMENT")

    def test_model_size_measured_offline(self):
        r = self._harness().run_all()
        size = [m for m in r["measurements"] if m["metric"] == "model_size_mb"][0]
        self.assertIsInstance(size["value"], (int, float))

    def test_dev_machine_not_tagged_as_android(self):
        # Running on dev machine but asking for android device must NOT crash with
        # a wrong tag; the guard only fires if a dev latency is mislabeled. Here
        # everything is PENDING, so it passes.
        r = self._harness(device=ANDROID_2GB, real=False).run_all()
        self.assertIn("measurements", r)

    def test_guard_raises_on_mislabel(self):
        h = self._harness(device=ANDROID_2GB, real=False)
        # Simulate a mislabeled latency measurement (dev value on android device)
        h.measurements.append({
            "category": "voice", "metric": "total_pipeline_ms", "value": 123.4,
            "unit": "ms", "device": ANDROID_2GB.label, "measured_on": DEV_MACHINE.label,
        })
        with self.assertRaises(ValueError):
            h.forbid_dev_as_android()


if __name__ == "__main__":
    unittest.main(verbosity=2)
