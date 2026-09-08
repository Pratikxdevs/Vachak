"""PHASE 1 — Python interface contracts (PEP 544 Protocols) for the Vachak
service/backend side. These mirror the Kotlin interfaces in
android/app/.../engine/EngineContracts.kt so the Android app and any Python
backend/orchestration tool share one vocabulary.

These are INTERFACES ONLY. No model is trained or loaded here. Mock adapters
live in tests/ and prove implementations are swappable.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import List, Protocol, runtime_checkable


class EngineError(str, Enum):
    UNSUPPORTED_LANGUAGE = "UNSUPPORTED_LANGUAGE"
    MODEL_NOT_LOADED = "MODEL_NOT_LOADED"
    MODEL_LOAD_FAILED = "MODEL_LOAD_FAILED"
    TIMEOUT = "TIMEOUT"
    IO_ERROR = "IO_ERROR"
    INVALID_INPUT = "INVALID_INPUT"
    OFFLINE_REQUIRED = "OFFLINE_REQUIRED"


@dataclass
class Ok:
    value: object


@dataclass
class Err:
    code: EngineError
    message: str


# A minimal Result union; real code may use a tagged union / exceptions.
Result = "Ok | Err"


@dataclass
class LanguagePair:
    source: str
    target: str


@runtime_checkable
class TranslationEngine(Protocol):
    def supports(self, pair: LanguagePair) -> bool: ...
    def load_model(self, pack_id: str) -> Result: ...
    def translate(self, text: str, pair: LanguagePair) -> Result: ...


@runtime_checkable
class ASREngine(Protocol):
    def supports(self, language: str) -> bool: ...
    def load_model(self, pack_id: str) -> Result: ...
    def transcribe(self, pcm16: bytes, sample_rate_hz: int) -> Result: ...


@runtime_checkable
class TTSEngine(Protocol):
    def supports(self, language: str) -> bool: ...
    def load_model(self, pack_id: str) -> Result: ...
    def synthesize(self, text: str, language: str) -> Result: ...


@dataclass
class LessonRef:
    id: str
    title: str
    grade: int


@dataclass
class Lesson:
    id: str
    title: str
    grade: int
    source_text_hi: str
    translated_text: str
    precomputed: bool = True


@dataclass
class Outcome:
    id: str
    description: str
    nipun_mapped: bool


@runtime_checkable
class CurriculumEngine(Protocol):
    def list_lessons(self, grade: int) -> Result: ...
    def get_lesson(self, id: str) -> Result: ...
    def get_outcomes(self, lesson_id: str) -> Result: ...


@dataclass
class WorksheetItem:
    prompt: str
    answer_key: str


@dataclass
class Worksheet:
    id: str
    lesson_id: str
    template: str
    items: List[WorksheetItem]


@runtime_checkable
class WorksheetEngine(Protocol):
    def generate(self, lesson_id: str, template: str) -> Result: ...


@dataclass
class Flashcard:
    front: str
    back: str
    image_asset: str


@dataclass
class FlashcardDeck:
    lesson_id: str
    cards: List[Flashcard]


@runtime_checkable
class FlashcardEngine(Protocol):
    def list_deck(self, lesson_id: str) -> Result: ...


@dataclass
class PackInfo:
    id: str
    language: str
    version: str
    min_android: int
    size_bytes: int


@runtime_checkable
class LanguagePackManager(Protocol):
    def install(self, pack_path: str) -> Result: ...
    def uninstall(self, pack_id: str) -> Result: ...
    def installed(self) -> Result: ...
    def validate(self, pack_id: str) -> Result: ...
    def rollback(self, pack_id: str) -> Result: ...
    def storage_used_bytes(self) -> Result: ...


@dataclass
class SyncReport:
    pack_id: str
    version: str
    timestamp_ms: int
    applied: bool


@runtime_checkable
class SyncManager(Protocol):
    def is_network_allowed(self) -> bool: ...
    def install_package(self, path: str) -> Result: ...
    def last_sync(self) -> Result: ...


@dataclass
class BenchmarkReport:
    asr_ms: int
    mt_ms: int
    tts_ms: int
    total_ms: int
    within_budget: bool


@runtime_checkable
class BenchmarkRunner(Protocol):
    def run(self, pair: LanguagePair, sample: str) -> Result: ...
