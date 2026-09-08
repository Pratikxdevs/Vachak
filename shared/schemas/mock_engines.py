"""DEV FIXTURE mock engines for the Python side. Mirror the Kotlin mocks in
android/app/.../engine/mock/MockEngines.kt. Output is clearly fake and must not
be treated as real inference. Swap for real adapters via the same injection
point without changing caller code.
"""
from __future__ import annotations

from .engines import (
    ASREngine, BenchmarkReport, BenchmarkRunner, CurriculumEngine, EngineError,
    Err, Flashcard, FlashcardDeck, FlashcardEngine, LanguagePair, LanguagePackManager,
    Lesson, LessonRef, Ok, Outcome, PackInfo, SyncManager, SyncReport, TTSEngine,
    TranslationEngine, Worksheet, WorksheetEngine, WorksheetItem,
)


class MockTranslationEngine:
    def supports(self, pair: LanguagePair) -> bool:
        return pair.source == "hi" and pair.target == "mund"
    def load_model(self, pack_id: str):
        return Ok(None)
    def translate(self, text: str, pair: LanguagePair):
        if not self.supports(pair):
            return Err(EngineError.UNSUPPORTED_LANGUAGE, f"pair {pair} unsupported")
        return Ok(f"[DEV-FIXTURE-mund] {text}")


class MockASREngine:
    def supports(self, language: str) -> bool:
        return language == "hi"
    def load_model(self, pack_id: str):
        return Ok(None)
    def transcribe(self, pcm16: bytes, sample_rate_hz: int):
        if sample_rate_hz <= 0:
            return Err(EngineError.INVALID_INPUT, "sample_rate_hz must be > 0")
        if not pcm16:
            return Err(EngineError.INVALID_INPUT, "empty pcm")
        return Ok("[DEV-FIXTURE-asr] नमस्ते")


class MockTTSEngine:
    def supports(self, language: str) -> bool:
        return language == "mund"
    def load_model(self, pack_id: str):
        return Ok(None)
    def synthesize(self, text: str, language: str):
        if not self.supports(language):
            return Err(EngineError.UNSUPPORTED_LANGUAGE, language)
        return Ok(b"\x00\x01" * len(text))  # placeholder pcm bytes


class MockCurriculumEngine:
    def list_lessons(self, grade: int):
        return Ok([LessonRef("L1", f"DEV-FIXTURE lesson {grade}", grade)])
    def get_lesson(self, id: str):
        return Ok(Lesson(id, "DEV-FIXTURE", 1, "हिन्दी पाठ", "[DEV-FIXTURE-mund] lesson"))
    def get_outcomes(self, lesson_id: str):
        return Ok([Outcome("O1", "DEV-FIXTURE outcome", True)])


class MockWorksheetEngine:
    def generate(self, lesson_id: str, template: str):
        return Ok(Worksheet(f"W_{lesson_id}", lesson_id, template,
                            [WorksheetItem("DEV-FIXTURE prompt", "DEV-FIXTURE key")]))


class MockFlashcardEngine:
    def list_deck(self, lesson_id: str):
        return Ok(FlashcardDeck(lesson_id, [Flashcard("हाथी", "[DEV-FIXTURE] elephant", "asset://elephant.png")]))


class MockLanguagePackManager:
    def __init__(self):
        self._store: dict[str, PackInfo] = {}
    def install(self, pack_path: str):
        info = PackInfo(pack_path, "mund", "0.1.0", 28, 0)
        self._store[info.id] = info
        return Ok(info)
    def uninstall(self, pack_id: str):
        if self._store.pop(pack_id, None) is None:
            return Err(EngineError.IO_ERROR, "not installed")
        return Ok(None)
    def installed(self):
        return Ok(list(self._store.values()))
    def validate(self, pack_id: str):
        return Ok(pack_id in self._store)
    def rollback(self, pack_id: str):
        if pack_id not in self._store:
            return Err(EngineError.IO_ERROR, "no prior version")
        return Ok(self._store[pack_id])
    def storage_used_bytes(self):
        return Ok(sum(i.size_bytes for i in self._store.values()))


class MockSyncManager:
    def is_network_allowed(self) -> bool:
        return False
    def install_package(self, path: str):
        return Ok(SyncReport(path, "0.1.0", 0, True))
    def last_sync(self):
        return Ok(None)


class MockBenchmarkRunner:
    def run(self, pair: LanguagePair, sample: str):
        r = BenchmarkReport(200, 120, 300, 620, True)
        return Ok(r)
