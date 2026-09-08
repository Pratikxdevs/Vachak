"""Voice-quality evaluation scaffolding for Mundari TTS (no fabricated metrics).

Defines which measurements MUST be captured once a real Mundari neural voice and
native raters are available. None are fabricated here; unavailable metrics return
TODO. WAV validity (real, parseable audio) is checked now because it needs no
gated resource.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, Optional


@dataclass
class VoiceEvalReport:
    metric: str
    value: object
    status: str
    detail: str = ""


@dataclass
class MundariVoiceEval:
    mos: VoiceEvalReport = field(default_factory=lambda: VoiceEvalReport("MOS", "TODO", "TODO"))
    intelligibility: VoiceEvalReport = field(default_factory=lambda: VoiceEvalReport("intelligibility", "TODO", "TODO"))
    pronunciation: VoiceEvalReport = field(default_factory=lambda: VoiceEvalReport("pronunciation", "TODO", "TODO"))
    latency_ms: VoiceEvalReport = field(default_factory=lambda: VoiceEvalReport("latency_ms", "TODO", "TODO"))
    model_size_mb: VoiceEvalReport = field(default_factory=lambda: VoiceEvalReport("model_size_mb", "TODO", "TODO"))
    wav_valid: VoiceEvalReport = field(default_factory=lambda: VoiceEvalReport("wav_valid", "TODO", "TODO"))

    def as_dict(self) -> Dict[str, object]:
        return {r.metric: {"value": r.value, "status": r.status, "detail": r.detail} for r in
                [self.mos, self.intelligibility, self.pronunciation,
                 self.latency_ms, self.model_size_mb, self.wav_valid]}


def check_wav_valid(wav_bytes: bytes) -> bool:
    try:
        import io
        import wave
        with wave.open(io.BytesIO(wav_bytes), "rb") as w:
            return w.getnchannels() >= 1 and w.getframerate() > 0
    except Exception:
        return False


def evaluate_voice(text: str, wav_bytes: bytes, measure_mos: bool = False,
                   measure_intelligibility: bool = False, measure_latency: bool = False,
                   model_size_mb: Optional[float] = None) -> MundariVoiceEval:
    rep = MundariVoiceEval()
    rep.wav_valid = VoiceEvalReport("wav_valid", check_wav_valid(wav_bytes), "computed")
    if measure_mos:
        rep.mos = VoiceEvalReport("MOS", "TODO", "TODO", "needs native Mundari raters; gated")
    if measure_intelligibility:
        rep.intelligibility = VoiceEvalReport("intelligibility", "TODO", "TODO", "gated")
    if measure_latency:
        rep.latency_ms = VoiceEvalReport("latency_ms", "TODO", "TODO", "measure on 2GB device")
    if model_size_mb is not None:
        rep.model_size_mb = VoiceEvalReport("model_size_mb", model_size_mb, "computed")
    return rep
