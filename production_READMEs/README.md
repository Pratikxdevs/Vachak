# Vachak — Production READMEs

Offline Hindi → Santali (Ol Chiki) classroom system for Hindi-medium teachers. SIH26042.

This folder is the structured production story of Vachak: what it is, why it exists,
what it is made of, what models/repos went into it, how long training took,
and the full journey (ASR → MT → TTS → UI revamp → offline packs).

## Reading order

1. `01-what-is-vachak.md` — what Vachak is (one definition + user flow)
2. `02-why-vachak.md` — problem it solves, how it solves it
3. `03-architecture-technical-components.md` — entire technical map
4. `04-ml-pipeline.md` — VAD → ASR → MT → TTS in detail
5. `05-models-repos-licenses.md` — all models, GitHub repos, licenses
6. `06-training-journey.md` — compute log: Colab 30h + Lightning 20h, ASR, MT, TTS timelines
7. `07-android-offline-engine.md` — Android modules, EngineProvider, packs, diagnostics
8. `08-content-curriculum.md` — lessons, worksheets, flashcards, localization rules
9. `09-backend-api-sync.md` — POST /translate + teacher-correction queue
10. `10-ui-revamp-demo-flow.md` — UI revamp + 90-second demo script

> Evidence rule: every claim cites a repo path (e.g. `android/app/.../EngineProvider.kt`).
> Training-hour numbers in `06` are team-reported (Colab/Lightning dashboards),
> not stored in git — marked as such.
