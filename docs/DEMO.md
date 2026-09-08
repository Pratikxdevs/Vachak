# DEMO — 90–120s Judge-Facing Flow

**App:** Vachak (SIH26042) — offline Hindi→Mundari (Ol Chiki) classroom app.
**Goal:** prove it works fully offline, in <3s, on a 2GB Android 9 tablet.

> This document describes the live on-device demo. The dev-machine run
> (`scripts/demo_flow.py`) exercises the *flow* with DEV-FIXTURE mocks only;
> on-device latency/battery are tracked as PENDING until measured on hardware.

## Preconditions
- Tablet: Android 9, 2GB RAM, arm64-v8a.
- WiFi **OFF** (airplane-mode or WiFi disabled) before launch.
- Language packs pre-side-loaded (Hindi ASR, Mundari MT, Mundari TTS).

## Script (≈90–120s)

| # | Beat | What the judge sees | ~Sec |
|---|------|---------------------|------|
| P | Problem | "शिक्षक हिंदी बोलते हैं, बच्चे संताली जानते हैं — भाषा की दीवार।" | 10 |
| O | Offline proof | Show WiFi OFF + diagnostics: 0 network requests (offline audit PASS). | 12 |
| C | Classroom | Open FLN lesson (precomputed bilingual translation). | 12 |
| V | Voice | Push-to-talk: teacher speaks Hindi. | 12 |
| T | Translation | Hindi → Santali (Ol Chiki) appears on screen. | 15 |
| S | Speech | Santali audio plays (TTS). | 15 |
| L | Learning content | Generate bilingual worksheet + show flashcards (template/prebuilt). | 18 |
| R | Performance | Diagnostics: pipeline latency vs <3s budget, RAM used. | 15 |
| I | Impact / multi | Show language-pack install path for other mother tongues. | 11 |

## Running the dev fixture
```bash
python scripts/demo_flow.py            # narrated, runs pipeline once with mocks
python scripts/demo_flow.py --script   # print the timed script only
python scripts/pack.py --lessons scripts/sample_data --packId fln_hi_mund_v1
```

## Acceptance (from AGENTS.md)
app opens → lesson loads → WiFi OFF → translate → play Santali audio →
worksheet → flashcards → push-to-talk Hindi → recognized text → Santali →
audio → <3s latency → diagnostics. **No hidden internet.**

## Known limitations (demo)
- Dev-machine timings are DEV FIXTURE, not Android latency.
- Mocks produce clearly-fake `[DEV-FIXTURE …]` output; replace via EngineProvider
  with sherpa-onnx / IndicTrans2 before the live judge run.
- TTS MOS / real WER / BLEU require on-device measurement — see benchmarks.
