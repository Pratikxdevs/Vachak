# Vachak — SIH26042 Vernacular Pedagogy

## What This Is

Offline Android tablet app for Hindi-medium teachers to deliver **Santali (Ol Chiki)** instruction. Android 9+, 2GB RAM, ~500MB total storage. No runtime network calls — all inference local, sequential ASR→MT→TTS. Santali only; Mundari dropped.

## Core Value

A teacher opens a lesson offline, sees Santali translation in Ol Chiki, plays real Santali speech, and runs the full classroom demo in <3s on a 2GB device.

## Requirements

### Validated

- Sherpa-onnx ASR (whisper-tiny Hindi) + Silero VAD loads and records (mic stable, null AssetManager fix)
- Sherpa-onnx VITS TTS loads and synthesizes (espeak-ng-data, recursive asset copy)
- Python MT baseline proven: IndicTrans2 CTranslate2 int8 Hindi→Santali (नमस्ते→ନମସ୍କାରଂ) — proves model, not APK path

### Active

- On-device MT (IndicTrans2 distilled 320M → fine-tune on COILD HIN-SAT → quantize → ONNX Runtime Mobile)
- Real Santali voice (VITS fine-tuned on IndicVoices Santali + Nirantar + Rasa/CC-BY-4.0)
- ASR E2E verification (push-to-talk → Hindi transcript, WAV fallback for emulator)
- Curriculum content (FLN + NIPUN + template worksheets + prebuilt flashcards in Room)
- Language-pack system + offline package builder/installer (sync/ is installer, not network client)
- <3s / 2GB / 500MB benchmark, sequential execution only
- Full demo acceptance run WiFi-OFF with evidence, no hidden internet

### Out of Scope

- Mundari / Ho — explicitly dropped; single language Santali only
- Online translation/cloud TTS, parallel model execution, AI-generated worksheets, Piper in app (GPL-3.0 training-only), training on IN22 test

## Context

Brownfield: android/ (Kotlin/Compose/Room/ml/content/sync), ml/ (translation/asr/tts pipelines), datasets/ (COILD 20,603 HIN-SAT, Education_v2, IndicVoices Santali 19,779, Common Voice Santali ~533, IN22 eval-only), models/indictrans2_ct2_int8, sherpa-onnx vendored AAR 1.13.0, IndicTrans2 + OneMTBig cloned.

## Constraints

- Offline only, sequential ASR→MT→TTS, 2GB RAM, ~500MB storage, minSdk 28, ABI arm64-v8a → x86_64 for emulator
- Track THIRD_PARTY_NOTICES.md + MODEL_AND_DATA_PROVENANCE.md; never ship MACHINE_TRANSLATED as approved pedagogy
- Verify santali corpora are CC BY 4.0 before training (COILD, IndicVoices, Rasa); IN22 never in train

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Santali only | User directive; strongest CC-BY-4.0 data ecosystem | — Active |
| IndicTrans2 dist-320M → ONNX Runtime Mobile | AGENTS.md mandated; 100–180MB budget | — Pending P1 |
| sherpa-onnx + ONNX Runtime Mobile for ASR/TTS | Already WIRED, Apache-2.0/MIT | ✓ Good |
| Vendored AAR + null AssetManager | Fixes fs vs asset crash | ✓ Good |
| Template worksheets, prebuilt flashcards | AGENTS.md hard rule | — Pending P4 |

---
*Last updated: 2026-08-29 Santali-only pivot*
