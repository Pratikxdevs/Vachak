"""SIH26042 speech+translation pipeline package (Phase 5 scaffolding).

Exposes swappable adapters so final models can be dropped in without caller changes.
No final Hindi ASR / Mundari TTS / Mundari MT model is bundled — use the *Mock* /
*Baseline* adapters for local runs.
"""

from .vad_stream import (
    VadSegment,
    VadStream,
    SherpaOnnxVadStream,
    MockVadStream,
    run_microphone_vad,
    stream_chunks,
)
from .asr_adapter import AsrAdapter, MockHindiAsrAdapter, IndicConformerAsrAdapter, AsrResult
from .tts_adapter import (
    TtsAdapter,
    SherpaOnnxTtsAdapter,
    EspeakFallbackTtsAdapter,
    MockTtsAdapter,
    SynthAudio,
)
from .latency import LatencyTracker, LatencyRecorder, LatencySample

__all__ = [
    "VadSegment", "VadStream", "SherpaOnnxVadStream", "MockVadStream",
    "run_microphone_vad", "stream_chunks",
    "AsrAdapter", "MockHindiAsrAdapter", "IndicConformerAsrAdapter", "AsrResult",
    "TtsAdapter", "SherpaOnnxTtsAdapter", "EspeakFallbackTtsAdapter", "MockTtsAdapter",
    "SynthAudio",
    "LatencyTracker", "LatencyRecorder", "LatencySample",
]
