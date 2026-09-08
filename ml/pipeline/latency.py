"""5D — Latency instrumentation.

Records pipeline markers:
    T0 = speech begins (first VAD speech frame)
    T1 = ASR result available
    T2 = translation result available
    T3 = TTS synthesis begins (audio generation call issued)
    T4 = audio begins (first audio sample ready / playback starts)

    end_to_end_latency = T4 - T0

IMPORTANT: Latency MUST be MEASURED on the real device (2GB RAM Android 9). The numbers
produced by this harness are real measurements of whatever backend is wired in — but with
mock/fixture backends they are NOT representative of production latency. Do NOT claim <=3s
from fixtures. The <3s target is a device-measurement goal, not a code guarantee.
"""

from __future__ import annotations

import json
import os
import time
from dataclasses import asdict, dataclass, field
from typing import List, Optional


@dataclass
class LatencySample:
    run_id: str
    t0_speech_begin: Optional[float] = None
    t1_asr: Optional[float] = None
    t2_translate: Optional[float] = None
    t3_tts_begin: Optional[float] = None
    t4_audio_begin: Optional[float] = None
    asr_text: str = ""
    target_text: str = ""
    backend: str = ""
    is_fixture: bool = False

    def end_to_end_ms(self) -> Optional[float]:
        if self.t0_speech_begin is None or self.t4_audio_begin is None:
            return None
        return (self.t4_audio_begin - self.t0_speech_begin) * 1000.0

    def stage_ms(self) -> dict:
        out = {}
        pts = {
            "asr": (self.t0_speech_begin, self.t1_asr),
            "translate": (self.t1_asr, self.t2_translate),
            "tts": (self.t2_translate, self.t3_tts_begin),
            "render": (self.t3_tts_begin, self.t4_audio_begin),
        }
        for k, (a, b) in pts.items():
            out[k] = None if (a is None or b is None) else (b - a) * 1000.0
        return out


class LatencyTracker:
    """Capture T0..T4 for a single utterance. Monotonic clock (no wall-clock skew)."""

    def __init__(self, run_id: str = "", backend: str = "", is_fixture: bool = False):
        self._sample = LatencySample(run_id=run_id, backend=backend, is_fixture=is_fixture)

    def _now(self) -> float:
        return time.monotonic()

    def mark_speech_begin(self, text: str = "") -> None:
        if self._sample.t0_speech_begin is None:
            self._sample.t0_speech_begin = self._now()
        self._sample.asr_text = text

    def mark_asr(self, text: str = "") -> None:
        self._sample.t1_asr = self._now()
        if text:
            self._sample.asr_text = text

    def mark_translate(self, target_text: str = "") -> None:
        self._sample.t2_translate = self._now()
        if target_text:
            self._sample.target_text = target_text

    def mark_tts_begin(self) -> None:
        self._sample.t3_tts_begin = self._now()

    def mark_audio_begin(self) -> None:
        self._sample.t4_audio_begin = self._now()

    def result(self) -> LatencySample:
        return self._sample


class LatencyRecorder:
    """Append-only JSONL recorder for device measurement runs."""

    def __init__(self, path: str):
        self._path = path

    def record(self, sample: LatencySample) -> None:
        row = asdict(sample)
        row["end_to_end_ms"] = sample.end_to_end_ms()
        row["stage_ms"] = sample.stage_ms()
        with open(self._path, "a", encoding="utf-8") as f:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    def load_all(self) -> List[dict]:
        if not os.path.isfile(self._path):
            return []
        out = []
        with open(self._path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    out.append(json.loads(line))
        return out
