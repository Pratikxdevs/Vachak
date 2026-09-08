# docs/provenance

Provenance and license tracking for reused code, models, and datasets.

The authoritative record is `THIRD_PARTY_NOTICES.md` at the repo root. It lists
every external dependency (sherpa-onnx, IndicTrans2, ONNX Runtime, Vosk,
whisper.cpp, Piper, Coqui TTS, Uktam) with its license, plus models and datasets
with CC BY 4.0 / MIT / GPL-3.0 terms.

Reused cloned repos in this workspace (referenced, NOT re-cloned, disk is low):
- `sherpa-onnx` — referenced for ASR/TTS Android API shape (Apache-2.0).
- `IndicTrans2`, `indictrans2-onnx-export` — MT base + ONNX export attempt (MIT).
- `models/` — IndicTrans2 checkpoints.

No third-party LICENSE/NOTICE file was modified or removed. Any borrowed code
must gain a new entry in `THIRD_PARTY_NOTICES.md`.
