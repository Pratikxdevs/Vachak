"""Graceful error-handling policy for the voice pipeline (PHASE 9C).

Each stage failure maps to a deterministic, teacher-friendly degradation:

  ASR failure      -> retry (bounded) then surface a clear recognition error.
  Translation fail -> show the SOURCE (Hindi) text so teaching continues.
  TTS failure      -> show the TRANSLATED text so the meaning is still conveyed.
  Model unavailable -> clear error + language-pack install action.

The policy is data, not control flow, so it is unit-testable and shared
verbatim between the Python orchestrator and the Kotlin wiring.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Optional


class Degradation(Enum):
    RETRY = "retry"
    SHOW_SOURCE = "show_source"          # show Hindi text
    SHOW_TRANSLATION = "show_translation"  # show target text
    PROMPT_LANGUAGE_PACK = "prompt_language_pack"
    HARD_ERROR = "hard_error"


@dataclass
class StagePolicy:
    retries: int = 0
    degradation: Degradation = Degradation.HARD_ERROR
    user_message: str = ""


class ErrorPolicy:
    """Per-stage failure policy. Tune in one place; used by the orchestrator."""

    def __init__(
        self,
        asr_retries: int = 2,
        asr_message: str = "आवाज़ साफ़ नहीं सुनी गई। कृपया दोबारा बोलें।",
        translate_message: str = "अनुवाद उपलब्ध नहीं; हिंदी पाठ दिखाया जा रहा है।",
        tts_message: str = "आवाज़ नहीं बनी; अनुवादित पाठ दिखाया जा रहा है।",
        model_message: str = "भाषा पैक अभी इंस्टॉल नहीं है। कृपया भाषा पैक इंस्टॉल करें।",
    ) -> None:
        self.asr = StagePolicy(
            retries=asr_retries, degradation=Degradation.RETRY, user_message=asr_message
        )
        self.translate = StagePolicy(degradation=Degradation.SHOW_SOURCE, user_message=translate_message)
        self.tts = StagePolicy(degradation=Degradation.SHOW_TRANSLATION, user_message=tts_message)
        self.model = StagePolicy(degradation=Degradation.PROMPT_LANGUAGE_PACK, user_message=model_message)

    def for_stage(self, stage: str) -> StagePolicy:
        return getattr(self, stage, StagePolicy())


def decide(stage: str, attempt: int, policy: ErrorPolicy, model_unavailable: bool = False):
    """Return the action to take for a stage failure.

    Returns (Degradation, message). `model_unavailable` short-circuits to the
    language-pack prompt regardless of retries.
    """
    sp = policy.for_stage(stage)
    if model_unavailable:
        return Degradation.PROMPT_LANGUAGE_PACK, policy.model.user_message
    if sp.degradation is Degradation.RETRY and attempt < sp.retries:
        return Degradation.RETRY, sp.user_message
    return sp.degradation, sp.user_message


__all__ = ["ErrorPolicy", "Degradation", "StagePolicy", "decide"]
