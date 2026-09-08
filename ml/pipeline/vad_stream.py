"""5A — Streaming audio: Microphone -> 10-100ms chunks -> VAD -> speech segments.

Reference implementation uses sherpa-onnx Silero VAD. A pure-Python energy-based
mock VAD is provided so the module runs locally WITHOUT a model or microphone
(DEV FIXTURE — not a substitute for the real neural VAD).

Pipeline shape (per AGENTS.md voice pipeline):
    Push-to-talk -> Hindi ASR -> ... -> Santali TTS -> AudioTrack
This module covers the first stage: raw mic chunks -> speech segments.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Callable, List, Optional

SAMPLE_RATE = 16000
# 10-100ms chunks are within spec; 100ms is the reference granularity.
DEFAULT_CHUNK_MS = 100


@dataclass
class VadSegment:
    """A detected speech segment plus its absolute timeline within the stream."""

    samples: List[float]
    sample_rate: int
    start_sec: float
    end_sec: float

    @property
    def duration_sec(self) -> float:
        return self.end_sec - self.start_sec


class VadStream:
    """Abstract streaming VAD. Implementations accumulate waveform and emit segments."""

    def accept_waveform(self, samples, sample_rate: int) -> None:
        raise NotImplementedError

    def is_speech_detected(self) -> bool:
        raise NotImplementedError

    def pop_segment(self) -> Optional[VadSegment]:
        raise NotImplementedError

    def flush(self) -> None:
        """Force-emit any buffered speech (call at end of stream)."""
        return None


class SherpaOnnxVadStream(VadStream):
    """Real VAD reference using sherpa-onnx Silero VAD.

    Requires `sherpa-onnx` and a silero_vad.onnx model file. Model is NOT shipped
    by this repo (download from k2-fsa/sherpa-onnx releases). Lazy import so this
    module is importable even when sherpa-onnx is absent.
    """

    def __init__(self, model_path: str, sample_rate: int = SAMPLE_RATE,
                 buffer_size_in_seconds: float = 30.0):
        try:
            import sherpa_onnx  # type: ignore
        except ImportError as e:  # pragma: no cover - environment dependent
            raise RuntimeError(
                "sherpa-onnx is not installed. Install it or use MockVadStream for a "
                "local DEV FIXTURE run."
            ) from e
        if not os.path.isfile(model_path):
            raise RuntimeError(f"VAD model not found: {model_path}")
        cfg = sherpa_onnx.VadModelConfig()
        cfg.silero_vad.model = model_path
        cfg.sample_rate = sample_rate
        self._sample_rate = sample_rate
        self._vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds)
        self._stream_start = 0.0

    def accept_waveform(self, samples, sample_rate: int) -> None:
        if sample_rate != self._sample_rate:
            raise ValueError("chunk sample_rate must match model sample_rate")
        self._vad.accept_waveform(samples)

    def is_speech_detected(self) -> bool:
        return self._vad.is_speech_detected()

    def pop_segment(self) -> Optional[VadSegment]:
        if self._vad.empty():
            return None
        seg = self._vad.front
        self._vad.pop()
        n = len(seg.samples)
        end = self._stream_start + n / self._sample_rate
        out = VadSegment(
            samples=list(seg.samples), sample_rate=self._sample_rate,
            start_sec=self._stream_start, end_sec=end,
        )
        self._stream_start = end
        return out

    def flush(self) -> None:
        self._vad.flush()


class MockVadStream(VadStream):
    """DEV FIXTURE VAD. Energy-threshold gating, NOT a neural voice activity detector.

    Lets the full pipeline run locally (no model, no mic) for integration tests and
    demos. DO NOT present mock VAD accuracy as a benchmark.
    """

    def __init__(self, sample_rate: int = SAMPLE_RATE, threshold: float = 0.02,
                 min_speech_samples: int = 1600, pad_samples: int = 1600):
        self._sample_rate = sample_rate
        self._threshold = threshold
        self._min_speech = min_speech_samples
        self._pad = pad_samples
        self._buf: List[float] = []
        self._speaking = False
        self._speech_start = 0.0
        self._stream_pos = 0.0
        self._seg_start_idx = 0

    def accept_waveform(self, samples, sample_rate: int) -> None:
        if sample_rate != self._sample_rate:
            raise ValueError("chunk sample_rate must match mock sample_rate")
        energy = (sum(s * s for s in samples) / max(1, len(samples))) ** 0.5
        now = self._stream_pos
        self._stream_pos += len(samples) / self._sample_rate
        if energy >= self._threshold:
            if not self._speaking:
                self._speaking = True
                self._seg_start_idx = max(0, len(self._buf) - self._pad)
                self._speech_start = (now - self._pad / self._sample_rate)
            self._buf.extend(samples)
        else:
            if self._speaking:
                self._buf.extend(samples[-self._pad:] if len(samples) > self._pad else samples)
                if (len(self._buf) - self._seg_start_idx) >= self._min_speech:
                    # keep; will be popped on flush
                    pass

    def is_speech_detected(self) -> bool:
        return self._speaking

    def pop_segment(self) -> Optional[VadSegment]:
        if not self._speaking:
            return None
        # close current segment
        seg = self._buf[self._seg_start_idx:]
        start = self._speech_start
        end = start + len(seg) / self._sample_rate
        out = VadSegment(samples=list(seg), sample_rate=self._sample_rate,
                         start_sec=start, end_sec=end)
        self._buf = []
        self._speaking = False
        return out

    def flush(self) -> None:
        self._speaking = False


def run_microphone_vad(vad: VadStream, on_segment: Callable[[VadSegment], None],
                       chunk_ms: int = DEFAULT_CHUNK_MS, device: Optional[int] = None,
                       mic_sample_rate: Optional[int] = None) -> None:
    """Blocking demo: read mic in chunks, feed VAD, emit segments. Ctrl-C to stop.

    Uses sounddevice (lazy import). Falls back to a file/producer hook if no mic.
    """
    try:
        import sounddevice as sd  # type: ignore
    except ImportError as e:  # pragma: no cover
        raise RuntimeError("sounddevice not installed; pass a producer to stream_chunks()") from e
    sr = mic_sample_rate or int(os.environ.get("SHERPA_ONNX_MIC_SAMPLE_RATE", SAMPLE_RATE))
    chunk = int(chunk_ms / 1000.0 * sr)
    if device is not None:
        sd.default.device[0] = device
    print(f"[vad] streaming mic @ {sr}Hz, {chunk_ms}ms chunks. Ctrl-C to stop.")
    with sd.InputStream(channels=1, dtype="float32", samplerate=sr) as s:
        while True:
            samples, _ = s.read(chunk)
            samples = [float(x) for x in samples.reshape(-1)]
            if sr != vad._sample_rate:
                samples = _resample(samples, sr, vad._sample_rate)
            vad.accept_waveform(samples, vad._sample_rate)
            seg = vad.pop_segment()
            if seg is not None:
                on_segment(seg)


def _resample(samples, src_sr: int, dst_sr: int):
    # DEV FIXTURE: nearest-neighbour resample. Real impl should use librosa/soxr.
    if src_sr == dst_sr:
        return samples
    ratio = dst_sr / src_sr
    n = int(len(samples) * ratio)
    return [samples[min(len(samples) - 1, int(i / ratio))] for i in range(n)]


def stream_chunks(producer, vad: VadStream, on_segment: Callable[[VadSegment], None],
                  chunk_ms: int = DEFAULT_CHUNK_MS, sample_rate: int = SAMPLE_RATE) -> None:
    """Drive VAD from any chunk producer (test/CI friendly). Producer yields float chunks."""
    chunk = int(chunk_ms / 1000.0 * sample_rate)
    for samples in producer:
        if len(samples) < chunk:
            vad.accept_waveform(list(samples), sample_rate)
            continue
        vad.accept_waveform(list(samples), sample_rate)
        seg = vad.pop_segment()
        if seg is not None:
            on_segment(seg)
    vad.flush()
    seg = vad.pop_segment()
    while seg is not None:
        on_segment(seg)
        seg = vad.pop_segment()
