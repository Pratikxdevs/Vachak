"""Vachak voice-pipeline orchestrator (language-agnostic reference).

Public surface:
  * interfaces  — pipeline component contracts (VAD/ASR/Context/NMT/Validator/TTS/Speaker)
  * errors     — graceful degradation policy (PHASE 9C)
  * classroom_state — live session state model (PHASE 9B)
  * orchestrator — request-lifecycle state machine (PHASE 9A)
  * mocks      — DEV FIXTURE components for tests / demo

The Kotlin wiring under android/ml/orchestrator mirrors `orchestrator` against
the existing EngineContracts types. See README.md in this folder.
"""
from .classroom_state import ClassroomState
from .errors import Degradation, ErrorPolicy, StagePolicy, decide
from .interfaces import (
    ASR,
    ContextEngine,
    ContextFrame,
    ModelUnavailableError,
    NMT,
    Speaker,
    TTS,
    TerminologyValidator,
    ValidationResult,
    VAD,
    VadResult,
)
from .orchestrator import (
    Components,
    Orchestrator,
    PipelineResult,
    PipelineStage,
    RequestStatus,
    StageResult,
)

__all__ = [
    "ClassroomState",
    "Degradation",
    "ErrorPolicy",
    "StagePolicy",
    "decide",
    "ASR",
    "ContextEngine",
    "ContextFrame",
    "ModelUnavailableError",
    "NMT",
    "Speaker",
    "TTS",
    "TerminologyValidator",
    "ValidationResult",
    "VAD",
    "VadResult",
    "Components",
    "Orchestrator",
    "PipelineResult",
    "PipelineStage",
    "RequestStatus",
    "StageResult",
]

from .mocks import (  # noqa: E402,F401
    MockVAD,
    MockASR,
    FailingASR,
    UnavailableASR,
    MockContext,
    MockNMT,
    UnavailableNMT,
    MockValidator,
    MockTTS,
    UnavailableTTS,
    MockSpeaker,
)
