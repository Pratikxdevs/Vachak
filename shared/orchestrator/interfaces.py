"""Language-agnostic pipeline component interfaces for the Vachak voice pipeline.

These interfaces define the contract the orchestrator depends on. They are
deliberately framework-free (stdlib only) so the same orchestrator logic can be
unit-tested offline and ported to Kotlin/Android (see android/ml/orchestrator).

Pipeline order (sequential — RAM constrained 2GB devices):
    audio -> VAD -> ASR -> ContextEngine -> NMT -> TerminologyValidator -> TTS -> Speaker
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any


class ModelUnavailableError(RuntimeError):
    """Raised when a required model/language-pack is not installed on device.

    Treated specially by the orchestrator: it surfaces a clear error AND a
    language-pack install action instead of silently degrading.
    """


# ---------------------------------------------------------------------------
# Component interfaces
# ---------------------------------------------------------------------------
@dataclass
class VadResult:
    audio: Any  # speech segment (e.g. PCM buffer / path)
    start_ms: int = 0
    end_ms: int = 0
    speech_detected: bool = True


class VAD(ABC):
    """Voice activity detection — trim silence, return the speech segment."""

    @abstractmethod
    def detect(self, audio: Any) -> VadResult:
        ...


class ASR(ABC):
    """Speech-to-text. Returns source-language transcript (Hindi)."""

    @abstractmethod
    def transcribe(self, audio: Any) -> str:
        ...


@dataclass
class ContextFrame:
    lesson_id: str | None = None
    activity: str | None = None
    grade: int | None = None
    subject: str | None = None
    # Free-form hints the context engine resolved (e.g. terminology domain).
    hints: dict[str, Any] | None = None


class ContextEngine(ABC):
    """Resolves classroom/lesson context that conditions translation + TTS."""

    @abstractmethod
    def resolve(self, transcript: str, prior: ContextFrame | None = None) -> ContextFrame:
        ...


class NMT(ABC):
    """Neural machine translation: source text -> target text (Mundari/Ol Chiki)."""

    @abstractmethod
    def translate(self, text: str, source: str, target: str) -> str:
        ...


@dataclass
class ValidationResult:
    ok: bool
    normalized: str
    issues: list[str] | None = None


class TerminologyValidator(ABC):
    """Validates translated text against the curriculum terminology glossary.

    On failure the orchestrator may fall back to showing the source text.
    """

    @abstractmethod
    def validate(self, text: str, context: ContextFrame | None = None) -> ValidationResult:
        ...


class TTS(ABC):
    """Text-to-speech. Returns audio (e.g. PCM buffer) for the speaker."""

    @abstractmethod
    def synthesize(self, text: str, language: str) -> Any:
        ...


class Speaker(ABC):
    """Plays synthesized audio to the student."""

    @abstractmethod
    def play(self, audio: Any) -> None:
        ...


__all__ = [
    "ModelUnavailableError",
    "VAD",
    "VadResult",
    "ASR",
    "ContextEngine",
    "ContextFrame",
    "NMT",
    "TerminologyValidator",
    "ValidationResult",
    "TTS",
    "Speaker",
]
