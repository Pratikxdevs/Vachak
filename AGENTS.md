# AGENTS.md — SIH26042 Vernacular Pedagogy

## What this project is

Offline Android tablet app for Hindi-medium teachers to deliver Santali (Ol Chiki) instruction. Smart India Hackathon 26042, Government of Jharkhand.

**Core constraint:** Fully offline after initial sync. Android 9+, 2GB RAM, ~500MB total storage.

## Hard rules

- No runtime network calls. All inference local.
- Sequential model execution: ASR → MT → TTS. Never parallel (RAM limit).
- Curriculum translations should be precomputed, not computed on device.
- Worksheets are template-based, not AI-generated.
- Flashcards are prebuilt assets, not generated.
- Piper is GPL-3.0. Handle licensing deliberately.
- Never train on IN22 test sentences. Keep eval splits separate.
- Never ship MACHINE_TRANSLATED curriculum content as approved pedagogy.

## Tech stack

| Layer | Technology |
|-------|-----------|
| Android app | Kotlin, Jetpack Compose, Room |
| ASR | IndicConformer / Vosk / whisper.cpp (benchmark on 2GB device) |
| Translation | IndicTrans2 distilled 320M → fine-tune → quantize |
| TTS | Custom Santali VITS/Piper via sherpa-onnx |
| ML runtime | ONNX Runtime Mobile, sherpa-onnx |
| Database | Room/SQLite |
| minSdk | 28 (Android 9) |
| ABI | arm64-v8a first |

## Repository layout

```
sih26042/
├── android/          # Kotlin + Compose app
│   ├── app/          # Main application module
│   ├── core/         # Shared utilities
│   ├── ml/           # ML integration layer
│   ├── content/      # Lesson/content database
│   └── sync/         # Package installer (not network client)
├── ml/               # Python ML training pipelines
│   ├── translation/  # IndicTrans2 fine-tuning
│   ├── asr/          # ASR benchmarking
│   └── tts/          # Santali TTS training
├── datasets/         # Parallel corpora, manifests, splits
├── curriculum/       # FLN lessons, outcomes, worksheets, flashcards
├── packages/         # Pack builder for content distribution
├── benchmarks/       # Translation/ASR/TTS/android benchmarks
└── docs/             # Architecture, evaluation reports
```

## Model budget (hard target ~500MB)

| Component | Budget |
|-----------|--------|
| App APK | 40–70 MB |
| Hindi ASR | 30–80 MB |
| MT (IndicTrans2) | 100–180 MB |
| Santali TTS | 20–80 MB |
| Tokenizers/runtime | 20–50 MB |
| Curriculum content | 10–30 MB |
| Flashcard images | 20–50 MB |
| Safety margin | 30–50 MB |

## Datasets (verified)

| Dataset | Size | License | Use |
|---------|------|---------|-----|
| COILD-MT-Corpus HIN-SAT | 20,603 pairs | CC BY 4.0 | Primary MT fine-tuning |
| Education_v2 HIN-SAT | Education domain | CC BY 4.0 | Domain adaptation |
| IndicVoices Santali | 19,779 train samples | CC BY 4.0 | TTS training |
| Common Voice Santali | ~533 clips | CC BY 4.0 | Supplementary TTS/ASR |
| IN22-Gen/Conv | Eval only | — | Benchmarking, never train on |

## Key external repos

- **sherpa-onnx** (k2-fsa) — ASR/TTS runtime, Android integration. Use this.
- **IndicTrans2** (AI4Bharat) — Translation model + fine-tuning. Use this.
- **IndicConformerASR** (AI4Bharat) — Hindi ASR benchmark candidate.
- **ONNX Runtime** (Microsoft) — Mobile inference engine.
- **Vosk** — Lightweight Hindi ASR fallback.
- **whisper.cpp** — Whisper tiny/base benchmark.
- **Piper** (OHF-Voice) — TTS architecture. GPL-3.0 license.
- **Coqui TTS** — TTS training fallback.
- **Uktam** — Reference architecture only. Do not fork (too heavy for 2GB devices).

## Voice pipeline (sequential)

```
Push-to-talk → Hindi ASR → Text normalize → IndicTrans2 MT → Ol Chiki validate → Santali TTS → AudioTrack
```

Latency budget: ASR ≤1s, MT ≤0.5s, TTS ≤1s, total <3s.

## What we build (vs reuse)

**Reuse:** ASR runtime, MT inference, ONNX execution, Android audio pipeline, TTS runtime.

**Build:** Fine-tuned Santali TTS model, FLN curriculum content, NIPUN outcome mapping, worksheet templates, flashcard assets, language-pack system, 2GB optimization, <3s benchmark.

## Dev workflow

```bash
# Android
cd android && ./gradlew assembleDebug

# ML training
cd ml/translation && python train.py
cd ml/tts && python train.py

# Benchmarks
cd benchmarks && python translation_benchmark.py
```

## Demo acceptance test

The SIH demo must show: app opens → lesson loads → WiFi OFF → translate lesson → play Santali audio → generate worksheet → flashcards → push-to-talk Hindi → show recognized text → show Santali → play audio → show <3s latency → diagnostics. No hidden internet.

## License tracking

Before merging any external code/model: record repo license, model license, dataset license, voice consent. Keep THIRD_PARTY_NOTICES.md. Do not assume model inherits repo license.
