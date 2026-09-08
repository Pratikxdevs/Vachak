"""TTS adapter interfaces for SIH26042 Mundari TTS (scaffolding only).

WARNING: No Mundari neural voice exists yet. The final model is a drop-in VITS/XTTS
adapter (referencing microsoft/MunTTS architecture) that will be produced once the
gated Mundari speech resources are licensed. See docs/MODEL_AND_DATA_PROVENANCE.md
("DATA ACCESS PENDING").

This module defines:
  - TTSAdapter: interface every voice engine implements.
  - FallbackTTSAdapter: eSpeak NG / sherpa-onnx public voice stand-in, explicitly
    NOT the final Mundari neural voice.
"""

from __future__ import annotations

import shutil
import subprocess
import wave
from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


@dataclass
class SynthResult:
    wav_bytes: bytes
    engine: str
    is_final_voice: bool
    note: str = ""


class TTSAdapter(ABC):
    """Interface contract for any text->speech engine producing 16-bit PCM WAV."""

    name: str = "abstract"
    is_final_voice: bool = False
    sample_rate: int = 22050

    @abstractmethod
    def synthesize(self, text: str) -> bytes:
        """Return WAV audio bytes for the given text."""
        raise NotImplementedError

    def synth_to_file(self, text: str, path: str) -> SynthResult:
        data = self.synthesize(text)
        Path(path).write_bytes(data)
        return SynthResult(data, self.name, self.is_final_voice, "written to file")


class FallbackTTSAdapter(TTSAdapter):
    """NOT the final Mundari neural voice.

    Uses eSpeak NG (Hindi/Indic voice) or a sherpa-onnx *public* voice as a
    pronunciation stand-in so the Android AudioTrack path and export pipeline can
    be validated. Output is clearly labeled NOT Mundari. If no engine is present,
    a minimal DEV FIXTURE WAV is emitted so offline pipeline tests still run.
    """

    name = "fallback-espeak-ng-NOT-MUNDARI"
    is_final_voice = False
    sample_rate = 22050

    def __init__(self, voice: str = "hi", use_espeak: bool = True,
                 dev_fixture_if_unavailable: bool = True):
        self.voice = voice
        self.use_espeak = use_espeak and shutil.which("espeak-ng") is not None
        self.dev_fixture = dev_fixture_if_unavailable

    def synthesize(self, text: str) -> bytes:
        if not text or not text.strip():
            raise ValueError("synthesize() requires non-empty text")
        if self.use_espeak:
            return self._espeak(text)
        if self.dev_fixture:
            return self._dev_fixture_wav(text)
        raise RuntimeError("No TTS engine available and dev fixture disabled")

    def _espeak(self, text: str) -> bytes:
        proc = subprocess.run(
            ["espeak-ng", "-v", self.voice, "--stdout", text],
            capture_output=True, check=True,
        )
        return proc.stdout

    def _dev_fixture_wav(self, text: str) -> bytes:
        """Minimal valid WAV (DEV FIXTURE) so pipeline tests run offline."""
        sr = self.sample_rate
        buf = bytearray()
        for i in range(sr):  # 1 second of silence, marked by header note
            buf += b"\x00\x00"
        wav = bytearray()
        # RIFF/WAV header for mono 16-bit PCM
        wav += b"RIFF"
        wav += (36 + len(buf)).to_bytes(4, "little")
        wav += b"WAVEfmt "
        wav += (16).to_bytes(4, "little")
        wav += (1).to_bytes(2, "little")          # PCM
        wav += (1).to_bytes(2, "little")          # mono
        wav += sr.to_bytes(4, "little")
        wav += (sr * 2).to_bytes(4, "little")     # byte rate
        wav += (2).to_bytes(2, "little")          # block align
        wav += (16).to_bytes(2, "little")         # bits
        wav += b"data"
        wav += len(buf).to_bytes(4, "little")
        wav += buf
        return bytes(wav)


def get_adapter(kind: str = "fallback", **kwargs) -> TTSAdapter:
    if kind == "fallback":
        return FallbackTTSAdapter(**kwargs)
    raise ValueError(f"unknown TTS adapter kind: {kind!r} (final Mundari not yet available)")
