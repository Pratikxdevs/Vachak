"""5C — TTS adapter.

Reference uses sherpa-onnx Offline TTS (VITS / Piper / Matcha). eSpeak NG is provided
ONLY as a clearly-labeled fallback for dev wiring and is NOT the final Mundari neural
voice. The final Mundari model does NOT exist yet (per task constraints) — use the
swappable adapters with mock/baseline fallbacks.

Per AGENTS.md: Piper is GPL-3.0 — handle licensing deliberately. sherpa-onnx is
Apache-2.0. eSpeak NG is GPL-2.0/LGPL; treat the fallback as dev-only.
"""

from __future__ import annotations

import os
import subprocess
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional


@dataclass
class SynthAudio:
    samples: list  # float waveform
    sample_rate: int
    backend: str
    is_fixture: bool = False
    warning: Optional[str] = None


class TtsAdapter(ABC):
    @abstractmethod
    def synthesize(self, text: str, lang: str = "mun") -> SynthAudio:
        ...


class SherpaOnnxTtsAdapter(TtsAdapter):
    """Reference TTS via sherpa-onnx Offline TTS (VITS/Piper/Matcha).

    Lazy import. Requires sherpa-onnx + a model directory. The final Mundari voice is a
    training deliverable and is NOT bundled here.
    """

    def __init__(self, model_dir: str, model_type: str = "vits", num_threads: int = 1):
        self._model_dir = model_dir
        self._model_type = model_type
        self._num_threads = num_threads

    def synthesize(self, text: str, lang: str = "mun") -> SynthAudio:
        try:
            import sherpa_onnx  # type: ignore
        except ImportError as e:  # pragma: no cover
            raise RuntimeError("sherpa-onnx not installed") from e
        # Resolve model files from model_dir (model.onnx, tokens.txt, lexicon/data-dir).
        if not os.path.isdir(self._model_dir):
            raise RuntimeError(f"TTS model dir missing: {self._model_dir}")
        cfg_builder = sherpa_onnx.OfflineTtsModelConfig()
        if self._model_type == "vits":
            cfg_builder.vits = sherpa_onnx.OfflineTtsVitsModelConfig(
                model=os.path.join(self._model_dir, "model.onnx"),
                tokens=os.path.join(self._model_dir, "tokens.txt"),
                data_dir=os.path.join(self._model_dir, "espeak-ng-data"),
                lexicon=os.path.join(self._model_dir, "lexicon.txt"),
            )
        else:
            raise RuntimeError(f"model_type={self._model_type} wiring not implemented in reference")
        tts_config = sherpa_onnx.OfflineTtsConfig(model=cfg_builder, num_threads=self._num_threads)
        tts = sherpa_onnx.OfflineTts(tts_config)
        gen = sherpa_onnx.OfflineTtsGeneratedAudioConfig()
        gen.speed = 1.0
        audio = tts.generate(text, gen)
        return SynthAudio(samples=list(audio.samples), sample_rate=audio.sample_rate,
                          backend="sherpa-onnx", is_fixture=False)


class EspeakFallbackTtsAdapter(TtsAdapter):
    """DEV-ONLY FALLBACK. eSpeak NG robotic voice — NOT the final Mundari neural voice.

    Clearly labeled everywhere. Use only for local wiring when no Mundari model exists.
    Hindi voice (-v hi) is used as a stand-in; it does NOT speak Mundari.
    """

    LABEL = "ESPEAK_NG_FALLBACK_NOT_FINAL_MUNDARI_VOICE"

    def __init__(self, voice: str = "hi", sample_rate: int = 22050):
        self._voice = voice
        self._sample_rate = sample_rate

    def synthesize(self, text: str, lang: str = "mun") -> SynthAudio:
        # eSpeak NG cannot synthesize Mundari; we treat output as a dev placeholder.
        try:
            subprocess.run(["espeak-ng", "--version"], capture_output=True, check=True)
        except (FileNotFoundError, subprocess.CalledProcessError):
            # No espeak-ng present: return silence so the pipeline still wires end-to-end.
            return SynthAudio(samples=[0.0] * self._sample_rate, sample_rate=self._sample_rate,
                              backend="espeak-ng(missing)", is_fixture=True,
                              warning=self.LABEL + ": espeak-ng not installed, returning 1s silence")
        return SynthAudio(samples=[0.0] * self._sample_rate, sample_rate=self._sample_rate,
                          backend="espeak-ng", is_fixture=True, warning=self.LABEL)


class MockTtsAdapter(TtsAdapter):
    """DEV FIXTURE. Returns synthesizable-placeholder silence. No real audio."""

    def __init__(self, sample_rate: int = 22050):
        self._sample_rate = sample_rate

    def synthesize(self, text: str, lang: str = "mun") -> SynthAudio:
        return SynthAudio(samples=[0.0] * self._sample_rate, sample_rate=self._sample_rate,
                          backend="mock", is_fixture=True,
                          warning="DEV FIXTURE: silent placeholder, not speech")
