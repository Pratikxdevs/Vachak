"""5B — ASR adapter.

References the Indic-STT pattern (IndicConformer + ONNX Runtime Mobile). Exposes a
swappable `AsrAdapter` interface so the final Hindi ASR model can be dropped in
without touching callers. A deterministic `MockHindiAsrAdapter` is provided as a
DEV FIXTURE so the pipeline runs locally before the real model exists.

Per AGENTS.md: sequential pipeline (ASR -> MT -> TTS); never parallel on 2GB RAM.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List, Optional

SAMPLE_RATE = 16000


class AsrResult:
    def __init__(self, text: str, confidence: float, is_fixture: bool = False):
        self.text = text
        self.confidence = confidence
        self.is_fixture = is_fixture

    def __repr__(self):
        return f"AsrResult(text={self.text!r}, confidence={self.confidence}, fixture={self.is_fixture})"


class AsrAdapter(ABC):
    """Transcribe a speech segment (float waveform) to text."""

    @abstractmethod
    def transcribe(self, samples, sample_rate: int) -> AsrResult:
        ...


class MockHindiAsrAdapter(AsrAdapter):
    """DEV FIXTURE. Returns a fixed Hindi sentence from a fixture table.

    NOT a real recognizer. Used for local integration, latency wiring, and tests.
    Swap to IndicConformerAsrAdapter (or any AsrAdapter) without code changes.
    """

    def __init__(self, fixture_text: Optional[str] = None):
        from . import sample_data  # local import to avoid cycle
        self._fixture = fixture_text or sample_data.SAMPLE_HINDI_UTTERANCE

    def transcribe(self, samples, sample_rate: int = SAMPLE_RATE) -> AsrResult:
        if sample_rate != SAMPLE_RATE and sample_rate <= 0:
            raise ValueError("sample_rate must be > 0")
        # DEV FIXTURE: we cannot verify content without a model. Signal clearly.
        return AsrResult(text=self._fixture, confidence=0.0, is_fixture=True)


class IndicConformerAsrAdapter(AsrAdapter):
    """Reference stub for the Indic-STT pattern (IndicConformer via ONNX Runtime Mobile).

    Lazy-imports onnxruntime and the model only when `transcribe` is first called, so
    this module imports cleanly without the model present. The real model is a training
    deliverable and is NOT provided here. Point `model_path` at the exported .onnx.
    """

    def __init__(self, model_path: str, tokenizer_path: Optional[str] = None,
                 num_threads: int = 1):
        self._model_path = model_path
        self._tokenizer_path = tokenizer_path
        self._num_threads = num_threads
        self._session = None
        self._tokenizer = None

    def _ensure_loaded(self):
        if self._session is not None:
            return
        try:
            import onnxruntime as ort  # type: ignore
        except ImportError as e:  # pragma: no cover
            raise RuntimeError("onnxruntime is required for IndicConformerAsrAdapter") from e
        if not __import__("os").path.isfile(self._model_path):
            raise RuntimeError(
                f"Final/reference ASR model not found: {self._model_path}. "
                "Train/export the IndicConformer Hindi model, or use MockHindiAsrAdapter."
            )
        so = ort.SessionOptions()
        so.intra_op_num_threads = self._num_threads
        so.inter_op_num_threads = self._num_threads
        self._session = ort.InferenceSession(self._model_path, so,
                                             providers=["CPUExecutionProvider"])
        # Tokenizer wiring is model-specific; left as a clear extension point.
        self._tokenizer = None

    def transcribe(self, samples, sample_rate: int = SAMPLE_RATE) -> AsrResult:
        self._ensure_loaded()
        # Reference: feed features -> session -> decode. The decode/feature code is
        # model-specific and intentionally NOT implemented here (no final model).
        raise NotImplementedError(
            "IndicConformer decode graph is model-specific and ships with the trained "
            "model. Implement against your exported .onnx, then this stub becomes live."
        )
