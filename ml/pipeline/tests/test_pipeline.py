import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from ml.pipeline.vad_stream import MockVadStream, VadSegment, stream_chunks
from ml.pipeline.asr_adapter import AsrAdapter, MockHindiAsrAdapter, IndicConformerAsrAdapter
from ml.pipeline.tts_adapter import TtsAdapter, MockTtsAdapter, EspeakFallbackTtsAdapter
from ml.pipeline.latency import LatencyTracker, LatencyRecorder


class TestVadMock(unittest.TestCase):
    def _speech_chunk(self):
        # energy above mock threshold (0.02)
        return [0.05] * 1600 + [0.0] * 1600

    def test_mock_vad_emits_segment(self):
        vad = MockVadStream()
        vad.accept_waveform(self._speech_chunk(), 16000)
        seg = vad.pop_segment()
        self.assertIsNotNone(seg)
        self.assertGreater(seg.duration_sec, 0)

    def test_stream_chunks_drives_callback(self):
        vad = MockVadStream()
        seen = []
        def prod():
            yield [0.05] * 1600
            yield [0.0] * 1600
        stream_chunks(prod(), vad, lambda s: seen.append(s))
        self.assertTrue(seen)


class TestAsrAdapterSwap(unittest.TestCase):
    def test_mock_returns_fixture(self):
        a = MockHindiAsrAdapter()
        r = a.transcribe([0.0] * 100, 16000)
        self.assertTrue(r.is_fixture)
        self.assertTrue(r.text)

    def test_interface_enforced(self):
        # Any AsrAdapter subclass can be substituted without caller change.
        def use(adapter: AsrAdapter):
            return adapter.transcribe([0.0] * 100, 16000).text
        self.assertTrue(use(MockHindiAsrAdapter()))

    def test_indicconformer_missing_model_raises(self):
        a = IndicConformerAsrAdapter(model_path="/no/model.onnx")
        with self.assertRaises(RuntimeError):
            a.transcribe([0.0] * 100, 16000)


class TestTtsAdapterSwap(unittest.TestCase):
    def test_mock_silent(self):
        au = MockTtsAdapter().synthesize("hi", "mun")
        self.assertTrue(au.is_fixture)
        self.assertEqual(au.samples[0], 0.0)

    def test_espeak_labeled_fallback(self):
        au = EspeakFallbackTtsAdapter().synthesize("hi", "mun")
        self.assertIn("NOT_FINAL_MUNDARI", au.warning or "")

    def test_interface_enforced(self):
        def use(t: TtsAdapter):
            return t.synthesize("x").sample_rate
        self.assertEqual(use(MockTtsAdapter()), 22050)


class TestLatency(unittest.TestCase):
    def test_markers_and_e2e(self):
        t = LatencyTracker(run_id="r1")
        t.mark_speech_begin(); t.mark_asr(); t.mark_translate(); t.mark_tts_begin(); t.mark_audio_begin()
        s = t.result()
        self.assertGreater(s.end_to_end_ms(), 0)
        self.assertIsNotNone(s.stage_ms()["asr"])

    def test_recorder_writes_jsonl(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "lat.jsonl")
            rec = LatencyRecorder(path)
            t = LatencyTracker(run_id="r1")
            for m in (t.mark_speech_begin, t.mark_asr, t.mark_translate, t.mark_tts_begin, t.mark_audio_begin):
                m()
            rec.record(t.result())
            rows = rec.load_all()
            self.assertEqual(len(rows), 1)
            self.assertIn("end_to_end_ms", rows[0])
            self.assertIn("stage_ms", rows[0])


if __name__ == "__main__":
    unittest.main()
