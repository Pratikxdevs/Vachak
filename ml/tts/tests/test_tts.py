"""Unit tests for the Mundari TTS scaffolding. Run without gated data / GPU.

    python -m unittest ml.tts.tests.test_tts

Exercises the fallback adapter (real eSpeak NG if present, else a DEV FIXTURE WAV)
and the export/eval pipeline stages on a tiny synthetic manifest.
"""

from __future__ import annotations

import sys
import unittest
import wave
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from ml.tts.adapter import FallbackTTSAdapter, get_adapter, TTSAdapter
from ml.tts.pipeline import TTSConfig, run_pipeline, prepare_data
from ml.tts.evaluate import evaluate_voice

MANIFEST = Path(__file__).resolve().parent.parent / "data" / "fixtures" / "dev_manifest.tsv"


def load_manifest() -> list:
    out = []
    for line in MANIFEST.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        text, audio = line.split("\t")
        out.append(text)
    return out


class TestFallbackTTS(unittest.TestCase):
    def test_fallback_is_not_final(self):
        eng = FallbackTTSAdapter()
        self.assertIsInstance(eng, TTSAdapter)
        self.assertFalse(eng.is_final_voice)
        self.assertIn("NOT-MUNDARI", eng.name)

    def test_synthesize_returns_valid_wav(self):
        eng = FallbackTTSAdapter()
        wav = eng.synthesize("परीक्षण।")
        self.assertTrue(len(wav) > 44)
        with wave.open(__import__("io").BytesIO(wav), "rb") as w:
            self.assertGreater(w.getframerate(), 0)

    def test_synthesize_rejects_empty(self):
        with self.assertRaises(ValueError):
            FallbackTTSAdapter().synthesize("")

    def test_factory(self):
        self.assertIsInstance(get_adapter("fallback"), FallbackTTSAdapter)


class TestTTSPipeline(unittest.TestCase):
    def setUp(self):
        self.manifest = load_manifest()
        self.cfg = TTSConfig()

    def test_prepare_data_clean(self):
        rep = prepare_data(self.manifest)
        self.assertTrue(rep["ok"])

    def test_pipeline_runs_without_data(self):
        rep = run_pipeline(self.manifest, self.cfg)
        self.assertEqual(rep["train_vits"]["status"], "TODO")
        self.assertEqual(rep["export_onnx"]["status"], "TODO")
        self.assertEqual(rep["android_bundle"]["status"], "TODO")

    def test_voice_eval_marks_todo_and_valid(self):
        eng = FallbackTTSAdapter()
        wav = eng.synthesize("परीक्षण।")
        ev = evaluate_voice("परीक्षण।", wav)
        self.assertEqual(ev.wav_valid.status, "computed")   # real check, no gated data
        self.assertEqual(ev.mos.status, "TODO")             # needs native raters
        self.assertEqual(ev.model_size_mb.status, "TODO")


if __name__ == "__main__":
    unittest.main(verbosity=2)
