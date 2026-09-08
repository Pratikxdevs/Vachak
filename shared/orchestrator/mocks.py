"""DEV FIXTURE mock components for the orchestrator.

Clearly-fake, deterministic implementations used by unit tests and the demo.
They MUST NOT be mistaken for real inference. On Android these are replaced by
sherpa-onnx (ASR/TTS/VAD) and IndicTrans2 (NMT) via the engine provider.
"""
from __future__ import annotations

from .interfaces import (
    ASR,
    ContextEngine,
    ModelUnavailableError,
    NMT,
    Speaker,
    TTS,
    TerminologyValidator,
    ValidationResult,
    VAD,
    VadResult,
)
from .interfaces import ContextFrame


class MockVAD(VAD):
    def detect(self, audio):
        return VadResult(audio=audio, speech_detected=True)


class MockASR(ASR):
    def __init__(self, text: str = "नमस्ते, आज हम हाथी पढ़ेंगे।"):
        self.text = text

    def transcribe(self, audio):
        return self.text


class FailingASR(ASR):
    """Always raises — used to exercise retry/hard-error policy."""

    def transcribe(self, audio):
        raise RuntimeError("asr decode failed")


class UnavailableASR(ASR):
    def transcribe(self, audio):
        raise ModelUnavailableError("Hindi ASR pack not installed")


class MockContext(ContextEngine):
    def resolve(self, transcript, prior=None):
        return ContextFrame(lesson_id="L1", activity="translate", grade=1, subject="language")


class MockNMT(NMT):
    def __init__(self, suffix: str = "[DEV-FIXTURE-mund]"):  # noqa: D401
        self.suffix = suffix

    def translate(self, text, source, target):
        return f"{self.suffix} {text}"


class UnavailableNMT(NMT):
    def translate(self, text, source, target):
        raise ModelUnavailableError("Mundari MT pack not installed")


class MockValidator(TerminologyValidator):
    def validate(self, text, context=None):
        return ValidationResult(ok=True, normalized=text)


class MockTTS(TTS):
    def synthesize(self, text, language):
        return ("pcm", len(text))


class UnavailableTTS(TTS):
    def synthesize(self, text, language):
        raise ModelUnavailableError("Mundari TTS pack not installed")


class MockSpeaker(Speaker):
    def __init__(self):
        self.played = []

    def play(self, audio):
        self.played.append(audio)


__all__ = [
    "MockVAD",
    "MockASR",
    "FailingASR",
    "UnavailableASR",
    "MockContext",
    "MockNMT",
    "UnavailableNMT",
    "MockValidator",
    "MockTTS",
    "UnavailableTTS",
    "MockSpeaker",
]
