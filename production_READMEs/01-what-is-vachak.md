# 01 — What is Vachak

**One-line definition:** Vachak (SIH26042) is a fully offline Android tablet app that lets a
Hindi-speaking teacher speak Hindi and get instant Santali (Ol Chiki) text + audio,
plus precomputed bilingual lessons, worksheets, and flashcards for Grades 1–5.

- Default pair: Hindi → Santali Ol Chiki (`sat_Olck`).
- Phase-2: Mundari (`unr_Deva` / `mun`) as deterministic 17k phrasebook lookup,
  neural merge pending (`modelpacks/stripped_mt_merged`).
- Fully offline after install. Android 9+ (minSdk 28), 2 GB RAM, ~500 MB budget.
- Sources: `AGENTS.md:1-3`, `README.md:1-10`.

## The pipeline (sequential, never parallel)

```
Mic → Silero VAD → Hindi ASR (NEMO CTC 134M sherpa layout)
    → MT (Santali ONNX INT8 / Mundari phrasebook)
    → VITS TTS → Speaker
```

Mirror implementations:
- Python reference: `shared/orchestrator/orchestrator.py`
- Kotlin on-device: `android/ml/orchestrator/Orchestrator.kt`
- Streaming adapters: `ml/pipeline/`

Sequential is a RAM rule: `numThreads=1` in `VadStream.kt`,
`IndicConformerAsrAdapter.kt`, `SherpaOnnxTtsAdapter.kt`. Never parallel.

## What it is NOT

- Not a chatbot. No runtime network calls — `AndroidManifest.xml` declares
  **no INTERNET permission**. Any network call at runtime is a bug.
- Curriculum is **never** produced by on-device MT at runtime.
  `curriculum/lessons/sat_lessons.json` is AUTHOR-DRAFT, human-reviewed, precomputed.
- Worksheets are template-based (PIL). Flashcards are prebuilt PNGs.
  Never AI-generated at runtime.

## The 10-second user flow (demo bar)

`WiFi OFF → lesson loads → translate → Santali audio → worksheet → flashcards →`
`push-to-talk Hindi → recognized text → Santali → audio → measured <3s total → diagnostics.`

Every conversation item shows measured `ASR x • MT y • TTS z • Total` via
`LastPipelineRun` — never mocked timings (`android/ml/.../LatencyTracker.kt`,
`android/app/.../ui/components/LiveComponents.kt:62-67`).
