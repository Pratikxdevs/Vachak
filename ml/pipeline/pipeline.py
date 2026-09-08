"""Phase 5 orchestrator: VAD -> ASR -> Translate -> TTS, with latency markers T0..T4.

Wiring is sequential (ASR -> MT -> TTS) per AGENTS.md RAM constraint (never parallel).
A `translator` callable is injected so the pipeline is independent of the translation
backend (Mock / Baseline / Final). See backend/api for the translator implementations.

`run_utterance` exercises one speech segment end-to-end and returns a LatencySample.
"""

from __future__ import annotations

from typing import Callable, Optional

from .asr_adapter import AsrAdapter, MockHindiAsrAdapter
from .tts_adapter import TtsAdapter, MockTtsAdapter
from .vad_stream import VadStream, MockVadStream, VadSegment
from .latency import LatencyTracker

Translator = Callable[[str, dict], tuple[str, str, bool]]  # (text, context) -> (target, backend, fixture)


def _default_translator(text: str, context: dict) -> tuple[str, str, bool]:
    # DEV FIXTURE pass-through so the pipeline runs with zero backend deps.
    return (f"[mun] {text}", "mock-passthrough", True)


class SpeechPipeline:
    def __init__(self, vad: VadStream, asr: AsrAdapter, tts: TtsAdapter,
                 translator: Translator = _default_translator, run_id: str = "run"):
        self.vad = vad
        self.asr = asr
        self.tts = tts
        self.translator = translator
        self.run_id = run_id

    def run_utterance(self, segment: VadSegment, context: dict) -> LatencyTracker:
        tr = LatencyTracker(run_id=self.run_id, backend="pipeline", is_fixture=True)
        # T0: speech begins (use segment start; real device sets at first speech frame)
        tr.mark_speech_begin()
        # T1: ASR
        asr_res = self.asr.transcribe(segment.samples, segment.sample_rate)
        tr.mark_asr(asr_res.text)
        # T2: translate
        target_text, backend, fixture = self.translator(asr_res.text, context)
        tr.mark_translate(target_text)
        # T3: TTS begins
        tr.mark_tts_begin()
        audio = self.tts.synthesize(target_text, lang="mun")
        # T4: audio begins (first sample available)
        tr.mark_audio_begin()
        tr._sample.is_fixture = tr._sample.is_fixture or fixture or audio.is_fixture
        return tr


def demo_local() -> None:
    """Local DEV FIXTURE run: mock VAD->ASR->translate->TTS, prints latency."""
    import os
    vad = MockVadStream()
    # Feed a fake "speech" chunk (energy above mock threshold).
    fake = [0.05] * (int(0.3 * 16000)) + [0.0] * 1600
    vad.accept_waveform(fake, 16000)
    seg = vad.pop_segment() or VadSegment(samples=fake, sample_rate=16000, start_sec=0.0, end_sec=0.3)
    pipe = SpeechPipeline(vad, MockHindiAsrAdapter(), MockTtsAdapter())
    tr = pipe.run_utterance(seg, {"grade": 1, "subject": "hindi", "learning_outcome": "alphabet"})
    s = tr.result()
    print(f"end_to_end_ms = {s.end_to_end_ms():.1f}  (FIXTURE — not a device measurement)")
    print(f"stages = {s.stage_ms()}")
    print("NOTE: these fixtures do not represent production latency. Measure on device.")
