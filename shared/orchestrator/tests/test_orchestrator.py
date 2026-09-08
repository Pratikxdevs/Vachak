"""Unit tests for the orchestrator state machine (PHASE 9).

Run offline, no models required:

    python -m pytest shared/orchestrator/tests -q
    # or: python shared/orchestrator/tests/test_orchestrator.py
"""
from __future__ import annotations

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(__file__)))))

from shared.orchestrator import (  # noqa: E402
    Components,
    Orchestrator,
    MockVAD,
    MockASR,
    MockContext,
    MockNMT,
    MockValidator,
    MockTTS,
    MockSpeaker,
    FailingASR,
    UnavailableASR,
    UnavailableNMT,
    UnavailableTTS,
)


class TestHappyPath(unittest.TestCase):
    def setUp(self):
        from shared.orchestrator import (  # type: ignore
            Components,
            Orchestrator,
            MockVAD,
            MockASR,
            MockContext,
            MockNMT,
            MockValidator,
            MockTTS,
            MockSpeaker,
        )
        self.speaker = MockSpeaker()
        self.orch = Orchestrator(
            Components(
                vad=MockVAD(),
                asr=MockASR(),
                context=MockContext(),
                nmt=MockNMT(),
                validator=MockValidator(),
                tts=MockTTS(),
                speaker=self.speaker,
            )
        )

    def test_full_pipeline_ok(self):
        r = self.orch.process_utterance("audio-bytes")
        self.assertEqual(r.status.value, "ok")
        self.assertIsNotNone(r.transcript)
        self.assertIsNotNone(r.translation)
        self.assertIsNotNone(r.present_audio)
        self.assertTrue(self.speaker.played)
        # every stage recorded
        stages = {s.stage for s in r.stages}
        for expected in ["vad", "asr", "context", "translate", "validate",
                          "synthesize", "speak"]:
            self.assertIn(expected, stages)
        # timings measured
        self.assertGreater(r.total_ms, 0.0)

    def test_state_updated(self):
        self.orch.process_utterance("audio-bytes")
        self.assertEqual(self.orch.state.last_status, "ok")
        self.assertEqual(self.orch.state.transcript, "नमस्ते, आज हम हाथी पढ़ेंगे।")


class TestAsrRetry(unittest.TestCase):
    def _orch(self, asr):
        return Orchestrator(Components(asr=asr))

    def test_retry_then_fail(self):
        orch = self._orch(FailingASR())
        r = orch.process_utterance("x")
        self.assertEqual(r.status.value, "failed")
        # retries configured = 2 -> 3 attempts
        asr_stages = [s for s in r.stages if s.stage == "asr"]
        self.assertEqual(len(asr_stages), 3)

    def test_model_unavailable_prompts_pack(self):
        orch = self._orch(UnavailableASR())
        r = orch.process_utterance("x")
        self.assertEqual(r.status.value, "failed")
        self.assertTrue(r.prompt_language_pack)
        self.assertIn("भाषा पैक", r.error)


class TestTranslateFallback(unittest.TestCase):
    def test_translate_fail_shows_source(self):
        orch = Orchestrator(Components(vad=MockVAD(), asr=MockASR(), nmt=UnavailableNMT()))
        r = orch.process_utterance("x")
        self.assertEqual(r.status.value, "failed")  # unavailable -> prompt pack
        self.assertTrue(r.prompt_language_pack)

    def test_translate_generic_fail_shows_source(self):
        class BoomNMT:
            def translate(self, text, source, target):
                raise RuntimeError("mt oom")

        orch = Orchestrator(Components(vad=MockVAD(), asr=MockASR(), nmt=BoomNMT()))
        r = orch.process_utterance("x")
        self.assertEqual(r.status.value, "degraded")
        self.assertEqual(r.present_text, "नमस्ते, आज हम हाथी पढ़ेंगे।")
        self.assertIsNone(r.present_audio)


class TestTtsFallback(unittest.TestCase):
    def test_tts_fail_shows_translation(self):
        orch = Orchestrator(
            Components(
                vad=MockVAD(), asr=MockASR(), nmt=MockNMT(),
                validator=MockValidator(), tts=UnavailableTTS(),
            )
        )
        r = orch.process_utterance("x")
        self.assertEqual(r.status.value, "failed")  # unavailable -> prompt pack
        self.assertTrue(r.prompt_language_pack)

    def test_tts_generic_fail_shows_translation(self):
        class BoomTTS:
            def synthesize(self, text, language):
                raise RuntimeError("tts oom")

        orch = Orchestrator(
            Components(
                vad=MockVAD(), asr=MockASR(), nmt=MockNMT(),
                validator=MockValidator(), tts=BoomTTS(),
            )
        )
        r = orch.process_utterance("x")
        self.assertEqual(r.status.value, "degraded")
        self.assertEqual(r.present_text, "[DEV-FIXTURE-mund] नमस्ते, आज हम हाथी पढ़ेंगे।")


class TestBudget(unittest.TestCase):
    def test_total_ms_recorded(self):
        orch = Orchestrator(Components(vad=MockVAD(), asr=MockASR(), nmt=MockNMT()))
        r = orch.process_utterance("x")
        self.assertGreaterEqual(r.total_ms, 0.0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
