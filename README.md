# Vachak — SIH26042 Offline Vernacular Pedagogy & Real-Time Translation

**Primary target:** Santali (Ol Chiki) · **Second adapter:** Mundari (Phase 2) · **Direction:** Hindi → Santali / Mundari
**Device:** Android 9+, ~2 GB RAM · **Hard requirement:** fully offline after initial sync
**Voice-to-voice latency ≤ 3 s** — measured per run on-device, shown on every conversation item (never mocked)

This is a *replaceable-adapter* architecture. The ML model is **not** the product — the offline
classroom system is. Santali and Mundari are both first-class adapters with Santali as the default;
everything is real on-device inference (sherpa-onnx ASR/TTS runtime, ONNX INT8 Santali MT, deterministic
Mundari phrasebook) — no mocks in the production path.

## Repository layout

| Path | Phase | Owner | What it provides |
|---|---|---|---|
| `android/` | 1, 2 | A | Compose app shell, engine interfaces, audio pipeline, model registry client |
| `shared/schemas/` `shared/orchestrator/` | 1, 9 | A, E | Cross-language engine contracts + orchestrator state machine |
| `offline/model_registry/` `modelpacks/` | 2 | A | Install/uninstall/checksum/rollback of language packs |
| `curriculum/` `worksheet/` `flashcard/` `localization/` | 3, 4 | B | Curriculum DB, worksheet & flashcard generators, localization layer |
| `ml/pipeline/` | 5 | C | VAD → ASR → MT → TTS streaming adapters + T0–T4 latency instrumentation |
| `backend/api/` | 6 | C | `POST /translate` + deterministic terminology validator (satfinal default) |
| `ml/translation/` `ml/tts/` | 7, 8 | D | Santali (live) + Mundari (Phase 2) NMT/TTS adapters + training/export pipelines |
| `backend/sync/` | 10 | E | Teacher-correction queue + sync (never auto-trains unverified data) |
| `ml/benchmarks/` `docs/benchmarks/` | 11 | E | Offline + voice-pipeline benchmark harness & report template |
| `scripts/` `docs/DEMO.md` | 12 | E | 90–120 s demo flow + pack builder |

## Language adapters

| Adapter | Target | On-device engine | Status |
|---|---|---|---|
| Santali (sat_Olck, **default**) | Ol Chiki | ONNX INT8 bundle (`vachak_models/mt/`) + 49-entry GOLD pre-check | **Live** — verified decode, default on first launch, toggle persists |
| Mundari (unr_Deva, Phase 2) | Devanagari | 17k deterministic phrasebook; merged CT2 (`stripped_mt_merged`) activates automatically if a trained merge is installed | Phrasebook live; neural NMT pending LoRA merge (GPU) |

Both adapters ship in the APK and are kept. Neither is ever deleted — Phase 2 upgrades Mundari in place
without touching the Santali path.

## Live pipeline (all on-device, sequential — never parallel, 2 GB RAM)

```
Mic (16 kHz) → Silero VAD → Hindi ASR (NEMO CTC 134M, sherpa layout)
  → MT (Santali ONNX INT8 / Mundari phrasebook)
  → TTS (VITS; placeholder voice until trained Santali VITS ships — text stays source of truth)
```

- App boot preloads all three engines on IO (`ModelStatus`: ASR/MT/TTS dots in Live header — green live, amber limited, red with cause).
- Every conversation item shows measured `ASR x • MT y • TTS z • Total Ns ✓<3s` (see `LastPipelineRun`).
- A throwing model reports `[ASR:MODEL] <cause>` — model failures can never disguise as "no speech detected" again.
- Diagnostics shows measured APK + filesDir + DB bytes against the 500 MB budget (no estimates).

## Quick start (offline-capable)

```bash
# Python modules (orchestrator, translation service, curriculum, benchmarks)
/usr/bin/python3 -m unittest discover -s shared/orchestrator/tests
/usr/bin/python3 -m unittest discover -s backend/api/tests
/usr/bin/python3 -m unittest discover -s curriculum/tests
/usr/bin/python3 -m unittest discover -s ml/benchmarks/tests

# Translation service
cd backend/api && /usr/bin/python3 app.py          # POST /translate (satfinal default)

# Android
cd android && ./gradlew assembleDebug              # needs Android SDK + JDK 17
./gradlew :ml:testDebugUnitTest :app:testDebugUnitTest   # JVM unit tests
```

## Swapping a model without UI changes

Set the engine implementation in `android/.../engine/EngineProvider` (real path is `EngineProvider.real`;
`Mock*` remains for isolated unit tests only). Adapters implement the `TranslationEngine` / `ASREngine` /
`TTSEngine` contracts, so Santali, Mundari-neural, or future voices swap without touching UI code.
Language packs (`filesDir/packs/<id>/`) override bundled assets without an APK rebuild.

## Integration milestones

1. Android + Mock Translation + Mock TTS + offline DB — done (mocks kept for tests only)
2. Android + Real ASR + Mock Translation + temporary TTS — done, superseded
3. Android + ASR + Santali MT + TTS placeholder — **done (current)** — ASR layout fix verified by exact Hindi decode; Santali default; voice pending trained VITS
4. Android + Mundari neural NMT + Santali/Mundari TTS voices + offline — **Phase 2 (pending):** LoRA merge (GPU), Coqui Santali VITS training (`ml/tts/runs/santali_vits/train_plan.json`)
5. Full classroom + curriculum + worksheets + flashcards + benchmark — in progress (content AUTHOR-DRAFT until SME sign-off; timings measured, formal <3s device proof pending)

## License & provenance

- Every reused repo's `LICENSE`/`NOTICE` is preserved. See `THIRD_PARTY_NOTICES.md` and
  `docs/MODEL_AND_DATA_PROVENANCE.md`.
- The Karya Hindi–Mundari corpus and Mundari TTS assets are **non-commercial (BY-NC-SA-FS)** and
  NOT bundled. Neural Mundari activates only when licensed access + merge resolve.
- No performance metrics are fabricated; timings shown in-app are measured per run (`LastPipelineRun`).
