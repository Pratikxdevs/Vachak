# 02 — Why Vachak (problem → solution)

## Problem

A Hindi-medium teacher stands in front of Santali/Mundari-speaking children
(Grades 1–5, FLN mission). The teacher speaks Hindi; the children understand
Santali at home. Result: language wall, lost learning, no internet to call
Google Translate, and low-end 2 GB RAM tablets.

Constraints from the field (`docs/architecture/DEMO.md:15-19`):
- No internet in classroom.
- Must run on Android 9+, 2 GB RAM.
- Voice-to-voice must feel instant: **<3 s total** (ASR ≤1000 ms, MT ≤500 ms, TTS ≤1000 ms).
- Pedagogy must be trustworthy — not raw machine translation passed off as textbook.

## How Vachak solves it

| Problem | Vachak answer | Where |
|---|---|---|
| Teacher can't speak Santali | Push-to-talk Hindi → Santali Ol Chiki text + VITS audio | `EngineProvider.real()` in `android/app/src/main/java/com/vachak/engine/EngineProvider.kt` |
| No internet | Zero runtime network; models + content bundled or side-loaded as packs | `AndroidManifest.xml` (no INTERNET), `offline/model_registry/registry.py` |
| Weak tablets | Sequential inference, `numThreads=1`, INT8 quantized MT, preloaded engines | `VadStream.kt:60`, `SherpaOnnxTtsAdapter.kt:216` |
| Untrustworthy MT as textbook | Curriculum is precomputed AUTHOR-DRAFT, never MT output; MT only for live speech | `curriculum/lessons/sat_lessons.json`, `AGENTS.md:9` |
| Worksheets/flashcards needed daily | Template worksheets (PIL) + prebuilt PNG flashcards, Room-backed offline | `worksheet/engine.py`, `flashcard/engine.py`, `flashcard/assets/*.png` (46 PNGs) |
| Other mother tongues later | Language packs override bundled assets, no APK rebuild | `android/sync/.../PackManager.kt`, `PackInstaller.kt` |
| Teacher corrections | Offline queue → human review → verified corpus only; never auto-trains | `backend/sync/queue.py`, `backend/sync/backend.py` (`AUTO_TRAIN_ENABLED=False`) |

## Who it is for

Primary: Hindi-medium primary teachers in tribal-belt schools (Santali first,
Mundari second). Secondary: education departments side-loading language packs
per district. Judges demo it WiFi-OFF in 90–120 s (see `10-ui-revamp-demo-flow.md`).
