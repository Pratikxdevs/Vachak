# SIH26042 — Vernacular Pedagogy & Real-Time Translation

**AI-Powered Vernacular Pedagogy and Real-Time Translation Tool for Mother Tongue-Based Primary Education**

Government of Jharkhand · Smart India Hackathon 26042

## What this does

An offline Android tablet app that lets Hindi-medium teachers deliver Santali (Ol Chiki) instruction to tribal children without learning Santali and without internet.

**Core flow:** Teacher speaks Hindi → tablet recognizes speech → translates to Santali → synthesizes Santali audio → student hears it. All in <3 seconds, fully offline.

## Key features

- Hindi → Santali real-time voice translation (push-to-talk)
- Bilingual lesson browser (FLN curriculum, NIPUN-aligned)
- Auto-generated bilingual worksheets (template-based)
- Visual bilingual flashcards (prebuilt assets)
- Fully offline operation on 2GB RAM Android 9+ tablets

## Architecture

```
┌─────────────────────────────────────────────┐
│              ANDROID TABLET                  │
│                                              │
│  Push-to-talk → Hindi ASR → MT → TTS → 📢   │
│                                              │
│  Lessons (precomputed) │ Worksheets │ Cards  │
│                                              │
│            NO INTERNET NEEDED                │
└─────────────────────────────────────────────┘
```

See `docs/` for detailed architecture.

## Repository structure

| Directory | Purpose |
|-----------|---------|
| `android/` | Kotlin + Jetpack Compose Android app |
| `ml/` | Python ML training pipelines (MT, ASR, TTS) |
| `datasets/` | Parallel corpora, manifests, train/eval splits |
| `curriculum/` | FLN lessons, NIPUN outcomes, worksheets, flashcards |
| `packages/` | Content pack builder for distribution |
| `benchmarks/` | Translation, ASR, TTS, and Android benchmarks |
| `docs/` | Architecture, evaluation reports, product specs |

## Quick start

```bash
# Android app
cd android && ./gradlew assembleDebug

# ML training
cd ml/translation && python train.py
cd ml/tts && python train.py

# Benchmarks
cd benchmarks && python translation_benchmark.py
```

## Tech stack

- **Android:** Kotlin, Jetpack Compose, Room, minSdk 28
- **ML runtime:** ONNX Runtime Mobile, sherpa-onnx
- **Translation:** IndicTrans2 distilled 320M → fine-tune → quantize
- **ASR:** IndicConformer / Vosk / whisper.cpp (benchmark on 2GB device)
- **TTS:** Custom Santali VITS/Piper via sherpa-onnx

## Target device

- Android 9+ (minSdk 28)
- 2GB RAM
- ~500MB total storage (app + models + content)
- arm64-v8a

## Datasets

| Dataset | Use | License |
|---------|-----|---------|
| COILD-MT-Corpus HIN-SAT | MT fine-tuning | CC BY 4.0 |
| Education_v2 HIN-SAT | Domain adaptation | CC BY 4.0 |
| IndicVoices Santali | TTS training | CC BY 4.0 |
| Common Voice Santali | Supplementary TTS | CC BY 4.0 |

## License

See `THIRD_PARTY_NOTICES.md` for third-party licenses.

## Links

- [Problem Statement](https://sih.gov.in/)
- [NIPUN Bharat](https://nipunbharat.education.gov.in/)
- [IndicTrans2](https://github.com/AI4Bharat/IndicTrans2)
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
