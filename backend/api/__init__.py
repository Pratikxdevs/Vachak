"""Vachak translation API package (Phase 6)."""

from .translation_service import (
    TranslationRequest,
    TranslationResponse,
    TranslationBackend,
    MockTranslationBackend,
    BaselineIndicTrans2Backend,
    FinalMundariTranslationBackend,
    TranslationService,
)
from .terminology_validator import TerminologyValidator, ValidationReport, TerminologyWarning
from .app import build_service, handle_translate, create_fastapi_app, serve_stdlib

__all__ = [
    "TranslationRequest", "TranslationResponse", "TranslationBackend",
    "MockTranslationBackend", "BaselineIndicTrans2Backend", "FinalMundariTranslationBackend",
    "TranslationService",
    "TerminologyValidator", "ValidationReport", "TerminologyWarning",
    "build_service", "handle_translate", "create_fastapi_app", "serve_stdlib",
]
