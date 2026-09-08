"""Orchestrator — owns the full request lifecycle (PHASE 9A).

This is the language-agnostic reference implementation. It drives the sequential
voice pipeline, measures per-stage latency, and applies the graceful error
policy (PHASE 9C). The Kotlin wiring in android/ml/orchestrator mirrors it
against the existing EngineContracts types.

Design notes:
  * Sequential execution only (RAM budget on 2GB devices). Never parallelize
    ASR/MT/TTS.
  * Every stage is timed with perf_counter so the benchmark harness can reuse
    the same code path on-device.
  * A `ModelUnavailableError` from any component triggers the language-pack
    action rather than a generic failure.
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Optional

from .classroom_state import ClassroomState
from .errors import Degradation, ErrorPolicy, decide
from .interfaces import (
    ASR,
    ContextEngine,
    ModelUnavailableError,
    NMT,
    Speaker,
    TTS,
    TerminologyValidator,
    VAD,
    VadResult,
)


class PipelineStage(str, Enum):
    CAPTURE = "capture"
    VAD = "vad"
    ASR = "asr"
    CONTEXT = "context"
    TRANSLATE = "translate"
    VALIDATE = "validate"
    SYNTHESIZE = "synthesize"
    SPEAK = "speak"
    DONE = "done"


class RequestStatus(str, Enum):
    OK = "ok"
    DEGRADED = "degraded"        # succeeded via fallback (e.g. showed source text)
    FAILED = "failed"


@dataclass
class StageResult:
    stage: str
    ok: bool
    ms: float
    error: Optional[str] = None
    action: Optional[str] = None


@dataclass
class PipelineResult:
    status: RequestStatus
    transcript: Optional[str] = None
    translation: Optional[str] = None
    audio: Any | None = None
    # What the UI should actually present to the teacher/student.
    present_text: Optional[str] = None
    present_audio: Any | None = None
    prompt_language_pack: bool = False
    error: Optional[str] = None
    stages: list[StageResult] = field(default_factory=list)
    total_ms: float = 0.0

    @property
    def within_budget_ms(self) -> float:
        return self.total_ms


@dataclass
class Components:
    vad: Optional[VAD] = None
    asr: Optional[ASR] = None
    context: Optional[ContextEngine] = None
    nmt: Optional[NMT] = None
    validator: Optional[TerminologyValidator] = None
    tts: Optional[TTS] = None
    speaker: Optional[Speaker] = None


class Orchestrator:
    def __init__(
        self,
        components: Components,
        state: Optional[ClassroomState] = None,
        policy: Optional[ErrorPolicy] = None,
        latency_budget_ms: float = 3000.0,
    ) -> None:
        self.c = components
        self.state = state or ClassroomState()
        self.policy = policy or ErrorPolicy()
        self.latency_budget_ms = latency_budget_ms

    # -- helpers -----------------------------------------------------------
    def _time(self, fn, *args, **kw):
        t0 = time.perf_counter()
        out = fn(*args, **kw)
        return out, (time.perf_counter() - t0) * 1000.0

    def _is_model_unavailable(self, exc: Exception) -> bool:
        return isinstance(exc, ModelUnavailableError)

    # -- main entry --------------------------------------------------------
    def process_utterance(self, audio: Any) -> PipelineResult:
        """Full lifecycle: audio in, audio/text out (or graceful degradation)."""
        stages: list[StageResult] = []
        t0_all = time.perf_counter()

        # 1. VAD
        try:
            seg, ms = self._time(self._run_vad, audio)
        except Exception as exc:  # pragma: no cover - vad rarely fails
            stages.append(StageResult(PipelineStage.VAD, False, 0, str(exc)))
            return self._finalize(RequestStatus.FAILED, stages, t0_all, error=str(exc))
        stages.append(StageResult(PipelineStage.VAD, True, ms))

        # 2. ASR (with retry)
        transcript: Optional[str] = None
        asr_ok = False
        last_err: Optional[str] = None
        for attempt in range(self.policy.asr.retries + 1):
            try:
                transcript, ms = self._time(self.c.asr.transcribe, seg.audio)
                stages.append(StageResult(PipelineStage.ASR, True, ms))
                asr_ok = True
                break
            except Exception as exc:
                last_err = str(exc)
                deg, msg = decide(PipelineStage.ASR, attempt, self.policy,
                                  self._is_model_unavailable(exc))
                stages.append(StageResult(PipelineStage.ASR, False, 0, str(exc), deg.value))
                if deg is Degradation.RETRY:
                    continue
                # model unavailable or out of retries -> fail
                if deg is Degradation.PROMPT_LANGUAGE_PACK:
                    return self._finalize(RequestStatus.FAILED, stages, t0_all,
                                          error=msg, prompt_pack=True)
                return self._finalize(RequestStatus.FAILED, stages, t0_all, error=msg)
        if not asr_ok:
            return self._finalize(RequestStatus.FAILED, stages, t0_all, error=last_err)

        self.state.record(PipelineStage.ASR, status="ok", transcript=transcript)

        # 3. Context
        ctx = self._run_context(transcript)
        stages.append(StageResult(PipelineStage.CONTEXT, True, 0.0))

        # 4. Translate (fallback: show source)
        translation: Optional[str] = None
        try:
            translation, ms = self._time(self._run_translate, transcript, ctx)
            stages.append(StageResult(PipelineStage.TRANSLATE, True, ms))
            self.state.record(PipelineStage.TRANSLATE, status="ok", translation=translation)
        except Exception as exc:
            deg, msg = decide(PipelineStage.TRANSLATE, 0, self.policy,
                              self._is_model_unavailable(exc))
            stages.append(StageResult(PipelineStage.TRANSLATE, False, 0, str(exc), deg.value))
            if deg is Degradation.PROMPT_LANGUAGE_PACK:
                return self._finalize(RequestStatus.FAILED, stages, t0_all,
                                      error=msg, prompt_pack=True)
            # SHOW_SOURCE fallback
            self.state.record(PipelineStage.TRANSLATE, status="degraded", error=msg,
                              action=deg.value)
            return self._finalize(RequestStatus.DEGRADED, stages, t0_all,
                                  transcript=transcript, present_text=transcript, error=msg)

        # 5. Validate
        validation_ok = True
        if self.c.validator is not None:
            try:
                vr, ms = self._time(self.c.validator.validate, translation, ctx)
                stages.append(StageResult(PipelineStage.VALIDATE, vr.ok, ms,
                                          None if vr.ok else "; ".join(vr.issues or [])))
                validation_ok = vr.ok
                translation = vr.normalized
            except Exception as exc:  # validation failure -> show source
                stages.append(StageResult(PipelineStage.VALIDATE, False, 0, str(exc)))
                validation_ok = False
        if not validation_ok:
            self.state.record(PipelineStage.VALIDATE, status="degraded",
                              translation=translation, action="show_source")
            return self._finalize(RequestStatus.DEGRADED, stages, t0_all,
                                  transcript=transcript, present_text=transcript,
                                  error="terminology validation failed")

        # 6. Synthesize (fallback: show translation)
        audio_out: Any | None = None
        try:
            audio_out, ms = self._time(self._run_tts, translation)
            stages.append(StageResult(PipelineStage.SYNTHESIZE, True, ms))
            self.state.record(PipelineStage.SYNTHESIZE, status="ok", audio=audio_out)
        except Exception as exc:
            deg, msg = decide(PipelineStage.SYNTHESIZE, 0, self.policy,
                              self._is_model_unavailable(exc))
            stages.append(StageResult(PipelineStage.SYNTHESIZE, False, 0, str(exc), deg.value))
            if deg is Degradation.PROMPT_LANGUAGE_PACK:
                return self._finalize(RequestStatus.FAILED, stages, t0_all,
                                      error=msg, prompt_pack=True)
            self.state.record(PipelineStage.SYNTHESIZE, status="degraded",
                              translation=translation, action=deg.value)
            return self._finalize(RequestStatus.DEGRADED, stages, t0_all,
                                  transcript=transcript, translation=translation,
                                  present_text=translation, error=msg)

        # 7. Speak
        try:
            _, ms = self._time(self._run_speak, audio_out)
            stages.append(StageResult(PipelineStage.SPEAK, True, ms))
        except Exception as exc:  # speaking failing still leaves audio available
            stages.append(StageResult(PipelineStage.SPEAK, False, 0, str(exc)))
            self.state.record(PipelineStage.SPEAK, status="degraded",
                              translation=translation, audio=audio_out)
            return self._finalize(RequestStatus.DEGRADED, stages, t0_all,
                                  transcript=transcript, translation=translation,
                                  present_audio=audio_out,
                                  error="playback failed; audio ready")

        self.state.record(PipelineStage.DONE, status="ok", audio=audio_out)
        return self._finalize(RequestStatus.OK, stages, t0_all,
                              transcript=transcript, translation=translation,
                              audio=audio_out, present_audio=audio_out)

    # -- stage runners (kept tiny so failures are isolated) -----------------
    def _run_vad(self, audio):
        if self.c.vad is None:
            return VadResult(audio=audio)
        return self.c.vad.detect(audio)

    def _run_context(self, transcript):
        if self.c.context is None:
            return None
        return self.c.context.resolve(transcript)

    def _run_translate(self, transcript, ctx):
        return self.c.nmt.translate(
            transcript, self.state.source_language, self.state.target_language
        )

    def _run_tts(self, translation):
        return self.c.tts.synthesize(translation, self.state.target_language)

    def _run_speak(self, audio_out):
        if self.c.speaker is not None:
            self.c.speaker.play(audio_out)

    # -- finalize ----------------------------------------------------------
    def _finalize(self, status, stages, t0_all, *, transcript=None, translation=None,
                  audio=None, present_text=None, present_audio=None, error=None,
                  prompt_pack=False) -> PipelineResult:
        total = (time.perf_counter() - t0_all) * 1000.0
        res = PipelineResult(
            status=status,
            transcript=transcript if transcript is not None else self.state.transcript,
            translation=translation if translation is not None else self.state.translation,
            audio=audio,
            present_text=present_text,
            present_audio=present_audio,
            prompt_language_pack=prompt_pack,
            error=error,
            stages=stages,
            total_ms=total,
        )
        self.state.record(
            PipelineStage.DONE if status is RequestStatus.OK else "error",
            status=status.value, error=error,
            action="prompt_language_pack" if prompt_pack else None,
        )
        return res


__all__ = [
    "Orchestrator",
    "Components",
    "PipelineStage",
    "RequestStatus",
    "StageResult",
    "PipelineResult",
]
