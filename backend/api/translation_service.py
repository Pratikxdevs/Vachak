"""6A — Backend/API translation service.

POST /translate {text, source:"hin", target:"mun", context:{grade,subject,learning_outcome}}
  -> {sourceText, targetText, confidence, terminologyWarnings[]}

Three pluggable backends, swap with NO UI change (env/flag only):
  - MockTranslationBackend: DEV FIXTURE, deterministic, no model.
  - BaselineIndicTrans2Backend: Hindi->Santali via IndicTrans2 (CT2). Clearly labeled
        NOT Mundari. Stand-in until the final Mundari model exists.
  - FinetunedSantaliBackend (FLAGSHIP, default): fine-tuned Hindi<->Santali.
  - FinalMundariTranslationBackend: stub — the Mundari model does NOT exist yet.

No latency claims are made here. Latency is measured by the pipeline harness (5D).
"""

from __future__ import annotations

import logging
import os
import sys
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Dict, List, Optional

from .terminology_validator import TerminologyValidator, ValidationReport

LOG = logging.getLogger("Vachak-MT")


@dataclass
class TranslationRequest:
    text: str
    source: str = "hin"
    target: str = "sat"
    context: Dict = field(default_factory=dict)

    def as_dict(self) -> dict:
        return {"text": self.text, "source": self.source,
                "target": self.target, "context": self.context}


@dataclass
class TranslationResponse:
    source_text: str
    target_text: str
    confidence: float
    terminology_warnings: List[dict] = field(default_factory=list)
    backend: str = ""
    is_fixture: bool = False
    note: str = ""

    def as_dict(self) -> dict:
        return {
            "sourceText": self.source_text,
            "targetText": self.target_text,
            "confidence": self.confidence,
            "terminologyWarnings": self.terminology_warnings,
            "backend": self.backend,
            "isFixture": self.is_fixture,
            "note": self.note,
        }


class TranslationBackend(ABC):
    name = "abstract"

    @abstractmethod
    def translate(self, req: TranslationRequest) -> TranslationResponse:
        ...


class MockTranslationBackend(TranslationBackend):
    """DEV FIXTURE. Deterministic Hindi->[mun] placeholder. Not a real translation.

    Lets the whole service run locally with zero model dependencies for integration,
    adapter-swap, and latency wiring. Never present as approved pedagogy.
    """
    name = "mock"

    def translate(self, req: TranslationRequest) -> TranslationResponse:
        return TranslationResponse(
            source_text=req.text,
            target_text=f"[mun] {req.text}",
            confidence=0.0,
            backend=self.name,
            is_fixture=True,
            note="DEV FIXTURE: mock translation, not a real model.",
        )


class BaselineIndicTrans2Backend(TranslationBackend):
    """Real stand-in: IndicTrans2 Hindi->Santali (CT2 INT8). Clearly NOT Mundari.

    Runs genuine inference via ml/translation/mundari/it2_ct2_baseline.py (ctranslate2 over
    the exported IndicTrans2 dist-320M INT8 checkpoint, reusing the cloned IndicTrans2
    tokenizer). Lazy import so the module imports without the model; the heavy deps
    (ctranslate2/transformers/IndicTransToolkit) are only imported on first translate().
    Falls back to a clear RuntimeError if the artifact/env is missing. This is a BASELINE
    for pipeline integration only — it does NOT produce Mundari.
    """
    name = "baseline-indictrans2-hin-sat"

    def __init__(self, model_dir: Optional[str] = None, tokenizer_dir: Optional[str] = None,
                 use_constrained_decoding: bool = False):
        self._model_dir = model_dir or os.environ.get("INDICTRANS2_MODEL_DIR", "")
        self._tokenizer_dir = tokenizer_dir or os.environ.get("INDICTRANS2_TOKENIZER_DIR", "")
        self._constrained = use_constrained_decoding
        self._engine = None

    def _ensure_loaded(self):
        if self._engine is not None:
            return
        LOG.debug("[Vachak-MT] backend=baseline ensure_loaded: importing real engine")
        ml_translation = os.path.join(
            os.path.dirname(os.path.abspath(__file__)), "..", "..", "ml", "translation"
        )
        ml_translation = os.path.abspath(ml_translation)
        if ml_translation not in sys.path:
            sys.path.insert(0, ml_translation)
        try:
            from mundari.it2_ct2_baseline import IndicTrans2CT2Baseline  # type: ignore
        except Exception as e:  # pragma: no cover - depends on environment
            raise RuntimeError(
                "IndicTrans2 CT2 baseline engine not importable in this env "
                f"(need ctranslate2/transformers/IndicTransToolkit): {e}"
            ) from e
        self._engine = IndicTrans2CT2Baseline(self._model_dir, self._tokenizer_dir)

    def translate(self, req: TranslationRequest) -> TranslationResponse:
        self._ensure_loaded()
        LOG.debug("[Vachak-MT] backend=baseline translate input=%r src=%s tgt=%s",
                  req.text, req.source, req.target)
        try:
            out = self._engine.translate([req.text], req.source, req.target)
        except Exception as e:  # pragma: no cover - env/artifact dependent
            raise RuntimeError(f"IndicTrans2 CT2 baseline inference failed: {e}") from e
        target_text = out[0] if out else ""
        note = ("BASELINE Hindi->Santali (IndicTrans2 CT2 int8, dist-320M). "
                "NOT Mundari. Dev stand-in only.")
        if (req.target or "").lower() in ("mun", "mundari"):
            note = ("WARNING: requested Mundari but baseline outputs Santali (sat_Olck), "
                    "NOT Mundari. " + note)
        LOG.debug("[Vachak-MT] backend=baseline translate output=%r", target_text)
        return TranslationResponse(
            source_text=req.text,
            target_text=target_text,
            confidence=0.0,
            backend=self.name,
            is_fixture=False,
            note=note,
        )


class FinetunedSantaliBackend(TranslationBackend):
    """Fine-tuned Hindi<->Santali (CT2 INT8, LoRA r64 merged into dist-320M).

    Trained on 9,423 verified pairs from material/ (both directions).
    Held-out scores: classroom chrF 40.4 (hi->sat) / 45.8 (sat->hi).
    Output is MACHINE-TRANSLATED draft pedagogy, never approved content.
    NOT Mundari. Select with TRANSLATION_BACKEND=satfinal.
    """
    name = "satfinal-hin-sat-bidi"

    def __init__(self, model_dir: Optional[str] = None,
                 tokenizer_dir: Optional[str] = None):
        self._model_dir = model_dir or os.environ.get(
            "SATFINAL_MODEL_DIR",
            os.path.join(os.path.dirname(os.path.abspath(__file__)),
                         "..", "..", "modelpacks", "sat_bidi_ct2_int8"))
        self._tokenizer_dir = tokenizer_dir or os.environ.get(
            "SATFINAL_TOKENIZER_DIR", "")
        self._engine = None

    def _ensure_loaded(self):
        if self._engine is not None:
            return
        ml_translation = os.path.abspath(os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            "..", "..", "ml", "translation"))
        if ml_translation not in sys.path:
            sys.path.insert(0, ml_translation)
        try:
            from mundari.it2_ct2_baseline import IndicTrans2CT2Baseline  # type: ignore
        except Exception as e:
            raise RuntimeError(
                "satfinal engine not importable in this env "
                f"(need ctranslate2/transformers/IndicTransToolkit): {e}"
            ) from e
        self._engine = IndicTrans2CT2Baseline(self._model_dir,
                                              self._tokenizer_dir)

    def translate(self, req: TranslationRequest) -> TranslationResponse:
        self._ensure_loaded()
        try:
            out = self._engine.translate([req.text], req.source, req.target)
        except Exception as e:
            raise RuntimeError(f"satfinal inference failed: {e}") from e
        return TranslationResponse(
            source_text=req.text,
            target_text=out[0] if out else "",
            confidence=0.0,
            backend=self.name,
            is_fixture=False,
            note=("MACHINE-TRANSLATED Hindi<->Santali (fine-tuned CT2 int8). "
                  "Draft only, not approved pedagogy. NOT Mundari."),
        )


class FinalMundariTranslationBackend(TranslationBackend):
    """STUB. The final Mundari MT model does not exist yet (task constraint)."""
    name = "final-mundari"

    def translate(self, req: TranslationRequest) -> TranslationResponse:
        raise NotImplementedError(
            "FinalMundariTranslationBackend is a stub. The Mundari model is not trained "
            "yet. Swap to MockTranslationBackend or BaselineIndicTrans2Backend."
        )


_BACKENDS = {
    "mock": MockTranslationBackend,
    "baseline": BaselineIndicTrans2Backend,
    "satfinal": FinetunedSantaliBackend,
    "final": FinalMundariTranslationBackend,
}


class TranslationService:
    """Selects a backend and runs terminology validation. Single swap point for callers."""

    def __init__(self, backend: TranslationBackend, validator: Optional[TerminologyValidator] = None):
        self._backend = backend
        self._validator = validator

    @classmethod
    def from_name(cls, name: str, vocabulary_csv: Optional[str] = None,
                  model_dir: Optional[str] = None) -> "TranslationService":
        name = (name or os.environ.get("TRANSLATION_BACKEND", "satfinal")).lower()
        if name not in _BACKENDS:
            raise ValueError(f"Unknown backend '{name}'. Choices: {list(_BACKENDS)}")
        if name == "baseline":
            if not model_dir:
                model_dir = os.environ.get("INDICTRANS2_MODEL_DIR", "")
            tokenizer_dir = os.environ.get("INDICTRANS2_TOKENIZER_DIR", "")
            backend = BaselineIndicTrans2Backend(model_dir, tokenizer_dir)
        elif name == "satfinal":
            backend = FinetunedSantaliBackend(
                model_dir or os.environ.get("SATFINAL_MODEL_DIR", ""),
                os.environ.get("SATFINAL_TOKENIZER_DIR", ""))
        else:
            backend = _BACKENDS[name]()
        validator = TerminologyValidator(vocabulary_csv) if vocabulary_csv and os.path.isfile(vocabulary_csv) else None
        return cls(backend, validator)

    def translate(self, req: TranslationRequest) -> TranslationResponse:
        resp = self._backend.translate(req)
        if self._validator is not None:
            report: ValidationReport = self._validator.validate(resp.target_text, req.context)
            resp.terminology_warnings = report.as_dict()["warnings"]
            if report.has_warnings:
                resp.note = (resp.note + " terminology warnings present.").strip()
        return resp
