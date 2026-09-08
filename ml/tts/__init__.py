"""Mundari TTS package (scaffolding only). Exposes adapter + pipeline."""

from .adapter import TTSAdapter, FallbackTTSAdapter, get_adapter
from .pipeline import TTSConfig, run_pipeline
from .evaluate import evaluate_voice, MundariVoiceEval

__all__ = [
    "TTSAdapter", "FallbackTTSAdapter", "get_adapter",
    "TTSConfig", "run_pipeline", "evaluate_voice", "MundariVoiceEval",
]
