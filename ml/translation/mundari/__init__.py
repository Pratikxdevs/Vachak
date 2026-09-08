"""Mundari NMT package (scaffolding only). Exposes adapter + pipeline."""

from .adapter import (
    TranslationAdapter,
    MockTranslationEngine,
    BaselineTranslationEngine,
    get_adapter,
)
from .pipeline import MundariConfig, Corpus, run_pipeline
from .evaluate import evaluate, MundariEval

__all__ = [
    "TranslationAdapter", "MockTranslationEngine", "BaselineTranslationEngine",
    "get_adapter", "MundariConfig", "Corpus", "run_pipeline", "evaluate", "MundariEval",
]
