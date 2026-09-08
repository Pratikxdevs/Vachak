"""Translation adapter interfaces for SIH26042 Mundari NMT (scaffolding only).

WARNING: No Mundari model exists yet. The final model is a drop-in adapter that
will be produced once the gated Karya Hindi-Mundari dataset is licensed and
accessible. See docs/MODEL_AND_DATA_PROVENANCE.md ("DATA ACCESS PENDING").

This module defines:
  - TranslationAdapter: the interface every engine (mock, baseline, final) implements.
  - MockTranslationEngine: deterministic DEV FIXTURE, NOT real Mundari.
  - BaselineTranslationEngine: IndicTrans2 Hindi->Santali stand-in, explicitly NOT Mundari.
"""

from __future__ import annotations

import re
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import List, Optional


@dataclass
class TranslationResult:
    text: str
    engine: str
    is_final_model: bool
    note: str = ""


class TranslationAdapter(ABC):
    """Interface contract for any Hindi->Mundari translation engine.

    Implementations MUST set `is_final_model=False` for any stand-in that is not
    the production Mundari adapter (mock, baseline Santali, partial checkpoints).
    """

    name: str = "abstract"
    is_final_model: bool = False

    @abstractmethod
    def translate(self, text: str) -> str:
        """Translate one Hindi sentence to the target script. Returns target text."""
        raise NotImplementedError

    def translate_batch(self, texts: List[str]) -> List[str]:
        return [self.translate(t) for t in texts]

    def describe(self) -> TranslationResult:
        return TranslationResult(
            text="", engine=self.name, is_final_model=self.is_final_model,
            note="interface contract only",
        )


class MockTranslationEngine(TranslationAdapter):
    """DEV FIXTURE. Deterministic stand-in so the pipeline can be exercised end-to-end.

    NOT real Mundari. Produces a reversible marker transform so pipeline unit tests
    can verify a mock round-trip without any gated data or GPU.
    """

    name = "mock-mundari-dev-fixture"
    is_final_model = False

    MARKER = "⟦MOCK-MUNDARI⟧"

    def translate(self, text: str) -> str:
        if text is None:
            raise ValueError("translate() requires a non-null string")
        cleaned = text.strip()
        if cleaned == "":
            return ""
        # Deterministic, reversible stand-in: wrap cleaned Hindi with a marker token.
        return f"{self.MARKER} {cleaned}"


class BaselineTranslationEngine(TranslationAdapter):
    """Labeled stand-in using IndicTrans2 Hindi->Santali.

    EXPLICITLY NOT Mundari. Used only to validate the integration path
    (tokenization, ONNX export shape, Android bundle) before the real Mundari
    adapter exists. Requires the IndicTrans2 ONNX artifacts present under ml/models.
    """

    name = "baseline-indictrans2-hin-sat-NOT-MUNDARI"
    is_final_model = False

    def __init__(self, onnx_dir: Optional[str] = None, use_mock_if_unavailable: bool = True):
        self.onnx_dir = onnx_dir
        self._session = None
        self._use_mock = use_mock_if_unavailable
        self._mock = MockTranslationEngine()

    def _ensure_session(self):
        if self._session is not None:
            return
        try:
            from . import _it2_baseline as it2  # lazy import keeps tests torch-free
            self._session = it2.load_session(self.onnx_dir)
        except Exception as exc:  # pragma: no cover - env dependent
            if self._use_mock:
                self._session = "mock-fallback"
            else:
                raise RuntimeError(f"IndicTrans2 baseline unavailable: {exc}")

    def translate(self, text: str) -> str:
        self._ensure_session()
        if self._session == "mock-fallback":
            return self._mock.translate(text)
        from . import _it2_baseline as it2
        return it2.run(self._session, text)


def get_adapter(kind: str = "mock", **kwargs) -> TranslationAdapter:
    """Factory. kind in {'mock', 'baseline'}.

    The production Mundari adapter will be added as kind='final' once data access
    is granted; it is intentionally absent here.
    """
    if kind == "mock":
        return MockTranslationEngine()
    if kind == "baseline":
        return BaselineTranslationEngine(**kwargs)
    raise ValueError(f"unknown adapter kind: {kind!r} (final Mundari not yet available)")
