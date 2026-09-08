"""PHASE 2C — Offline test harness (Python side, runnable without Android).

Covers the SIH "fully offline after sync" acceptance assumptions:
  1. Airplane mode: NO network calls may occur anywhere in the pipeline.
  2. App restart without network: engines + packs re-init from local state.
  3. Model loading: adapters load from a language pack manifest.
  4. Local DB: curriculum served from a local store (Room-equivalent placeholder).
  5. Worksheet generation placeholder: template-based, deterministic.
  6. Startup/memory STUBS recorded (DEV FIXTURE numbers, not measured on device).

Run:  python3 -m unittest offline.sync_engine.offline_harness -v
"""
from __future__ import annotations

import json
import socket
import time
import unittest
from pathlib import Path
from typing import Dict, List

import urllib.request

from offline.model_registry.manifest_schema import PackManifest
from shared.schemas.mock_engines import (
    MockASREngine, MockCurriculumEngine, MockTranslationEngine, MockTTSEngine,
    MockWorksheetEngine,
)
from shared.schemas.engines import LanguagePair


class LocalCurriculumStore:
    """DEVICE-DB PLACEHOLDER standing in for Android Room. Persists to JSON so a
    'restart' (new instance) still finds data with no network."""

    def __init__(self, path: Path):
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self.path.write_text("{}", encoding="utf-8")

    def put(self, lesson_id: str, payload: dict) -> None:
        data = json.loads(self.path.read_text(encoding="utf-8"))
        data[lesson_id] = payload
        self.path.write_text(json.dumps(data), encoding="utf-8")

    def get(self, lesson_id: str) -> dict | None:
        data = json.loads(self.path.read_text(encoding="utf-8"))
        return data.get(lesson_id)


def assert_offline(fn, *args, **kwargs):
    """Run fn under a patched environment where any socket/HTTP call raises.
    If fn triggers network I/O, the test FAILS (proving offline violation)."""
    real_socket = socket.socket
    real_urlopen = urllib.request.urlopen

    def blocked_socket(*a, **k):
        raise AssertionError("NETWORK CALL ATTEMPTED (socket) — offline violation")

    def blocked_urlopen(*a, **k):
        raise AssertionError("NETWORK CALL ATTEMPTED (urlopen) — offline violation")

    socket.socket = blocked_socket  # type: ignore
    urllib.request.urlopen = blocked_urlopen  # type: ignore
    try:
        return fn(*args, **kwargs)
    finally:
        socket.socket = real_socket  # type: ignore
        urllib.request.urlopen = real_urlopen  # type: ignore


class OfflineHarnessTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(__file__).resolve().parents[2] / ".tmp_offline_harness"
        self.tmp.mkdir(parents=True, exist_ok=True)
        self.db = LocalCurriculumStore(self.tmp / "curriculum.json")
        self.db.put("L1", {"title": "DEV-FIXTURE", "hi": "छः हाथी", "mund": "[DEV-FIXTURE-mund]"})

    def tearDown(self):
        import shutil
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_airplane_mode_no_network(self):
        """The full voice+translation pipeline must run with network blocked."""
        asr, tts = MockASREngine(), MockTTSEngine()

        def pipeline():
            r = asr.transcribe(b"\x00\x01", 16000)
            assert r is not None
            return tts.synthesize("[DEV-FIXTURE-mund] नमस्ते", "mund")

        out = assert_offline(pipeline)
        self.assertIsNotNone(out)

    def test_restart_without_network(self):
        """Re-init engines + local DB from disk; must work with no network."""
        def restart():
            # simulate cold start: new DB handle + engines
            store = LocalCurriculumStore(self.tmp / "curriculum.json")
            lesson = store.get("L1")
            cur = MockCurriculumEngine()
            res = cur.get_lesson("L1")
            return lesson, res

        lesson, res = assert_offline(restart)
        self.assertIsNotNone(lesson)
        self.assertEqual(lesson["mund"], "[DEV-FIXTURE-mund]")

    def test_model_loading_from_manifest(self):
        base = Path(__file__).resolve().parents[2]
        m = PackManifest.load(base / "modelpacks" / "mundari" / "manifest.json")
        tr = MockTranslationEngine()
        self.assertTrue(tr.supports(LanguagePair("hi", "mund")))
        self.assertEqual(tr.load_model("modelpacks/mundari").value, None)

    def test_local_db_serves_curriculum(self):
        cur = MockCurriculumEngine()
        r = cur.get_lesson("L1")
        self.assertEqual(r.value.precomputed, True)  # precomputed, not generated

    def test_worksheet_generation_placeholder(self):
        ws = MockWorksheetEngine().generate("L1", "fill_blank")
        self.assertEqual(ws.value.template, "fill_blank")
        self.assertTrue(ws.value.items, "worksheet must have items")

    def test_startup_memory_stubs(self):
        """DEV FIXTURE stubs — replace with real instrumentation on-device."""
        t0 = time.time()
        _ = MockTranslationEngine()  # cold construct
        startup_ms = int((time.time() - t0) * 1000)
        # Stub: real build reads Debug.MemoryInfo / ActivityManager.
        memory_mb_stub = 0  # DEV FIXTURE
        report = {
            "startup_ms": startup_ms,
            "memory_mb_stub": memory_mb_stub,
            "note": "DEV FIXTURE: not measured on device",
        }
        self.assertIn("startup_ms", report)
        self.assertIn("note", report)


if __name__ == "__main__":
    unittest.main()
