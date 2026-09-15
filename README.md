# Vachak — SIH26042 · Offline Hindi → Santali Classroom System

**Vachak is a fully offline Android tablet app that lets a Hindi-speaking teacher speak Hindi
and get instant Santali (Ol Chiki) text + audio — plus precomputed bilingual lessons,
worksheets, and flashcards for Grades 1–5.**

| | |
|---|---|
| **Default pair** | Hindi → Santali Ol Chiki (`sat_Olck`) |
| **Second adapter** | Mundari, deterministic 17k phrasebook (Phase-2, neural merge pending) |
| **Device** | Android 9+ (minSdk 28), 2 GB RAM, ~500 MB budget |
| **Network** | **None at runtime** — `AndroidManifest.xml` declares no INTERNET permission. Any runtime network call is a bug. |
| **Latency bar** | Voice-to-voice **< 3 s**, measured per run on-device (never mocked) |

Full production story: [`production_READMEs/`](production_READMEs/) (10-chapter spine + deep dives, § Documentation below).

---

## 1. The problem

A Hindi-medium teacher stands in front of Santali-speaking children (Grades 1–5, FLN mission).
The teacher speaks Hindi; the children understand Santali at home. Result: a language wall —
and no internet to call Google Translate, on low-end 2 GB tablets. Field constraints:

- No internet in the classroom · Android 9+, 2 GB RAM · voice-to-voice must feel instant (< 3 s)
- Pedagogy must be trustworthy — raw machine translation passed off as textbook is unacceptable

## 2. How Vachak solves it

| Problem | Vachak answer |
|---|---|
| Teacher can't speak Santali | Push-to-talk Hindi → Santali Ol Chiki text + VITS audio (`EngineProvider.real()`) |
| No internet | Zero runtime network; models + content bundled or side-loaded as signed packs |
| Weak tablets | Sequential inference, `numThreads=1`, INT8-quantized MT, preloaded engines |
| Untrustworthy MT as textbook | Curriculum is precomputed AUTHOR-DRAFT, never MT output; MT is for live speech only |
| Daily worksheets/flashcards | Authored chapter bundles (hi + Ol Chiki + art) + textbook PDFs, Room/pack-backed, offline |
| Other mother tongues later | Language packs override bundled assets — no APK rebuild |
| Teacher corrections | Offline queue → human review → verified corpus only; never auto-trains |

## 3. Live pipeline (sequential — never parallel, 2 GB RAM)

```
Mic (16 kHz) → Silero VAD → Hindi ASR (NeMo CTC, sherpa layout)
  → MT (Santali ONNX INT8 / Mundari phrasebook)
  → VITS TTS → Speaker
```

- Every stage `numThreads=1` (`VadStream.kt`, `IndicConformerAsrAdapter.kt`, `SherpaOnnxTtsAdapter.kt`).
- Every conversation item shows measured `ASR x • MT y • TTS z • Total` via `LastPipelineRun`.
- Model failure surfaces `[ASR:MODEL] <cause>` — never "no speech detected".
- Python reference: `shared/orchestrator/` · on-device mirror: `android/ml/orchestrator/`.

## 4. Demo (90–120 s, WiFi OFF)

| Beat | What the judge sees |
|---|---|
| Problem | शिक्षक हिंदी बोलते हैं, बच्चे संताली जानते हैं — भाषा की दीवार |
| Offline proof | WiFi OFF + diagnostics: 0 network requests |
| Classroom | Open FLN lesson (precomputed bilingual) |
| Voice → Translation → Speech | Push-to-talk Hindi → Santali Ol Chiki on screen → VITS audio |
| Learning | Worksheet (opens in-app: authored sheet or textbook PDF) + flashcards |
| Performance | Diagnostics: measured latency vs < 3 s, bytes vs budget |

Demo bar: WiFi OFF → lesson → translate → audio → worksheet → flashcards → PTT → Santali → audio → < 3 s → diagnostics.
Full script: [`production_READMEs/demo/judge-demo-script.md`](production_READMEs/demo/judge-demo-script.md).

## 5. Repository layout

```
android/{app,core,ml,content,sync}/  # Gradle modules — UI depends only on EngineProvider
shared/orchestrator/ + shared/schemas/  # contracts + sequential state machine
offline/model_registry/               # pack install/checksum/rollback authority
backend/api/                          # POST /translate, stdlib http.server, zero deps
backend/sync/                         # teacher-correction queue (never auto-trains)
curriculum/ worksheet/ flashcard/ localization/  # content DB + generators + l10n
ml/{pipeline,translation,tts,finetune,asr,benchmarks}/  # adapters, training/export, harness
scripts/ packages/packs/              # pack builders + demo
santali_organized/                    # textbook PDFs + authored chapter bundles (G1/G2/G3)
datasets/ material/                   # corpora (hin_sat draft; hin_mun QUARANTINED, never bundled)
docs/                                 # phases, architecture, benchmarks, provenance (canonical)
production_READMEs/                   # structured production story (this README's source)
```

| Path | Role |
|---|---|
| `android/` | Compose shell; `EngineProvider` is the engine swap point (swap needs no UI change) |
| `shared/schemas/` | Translation / ASR / TTS / Curriculum / Worksheet / Flashcard / Pack / Sync / Benchmark protocols |
| `shared/orchestrator/` | `VAD→ASR→Context→NMT→Validator→TTS→Speaker`, 3000 ms budget |
| `offline/model_registry/` | `registry.py` install/validate/rollback, per-file sha256, manifest schema |
| `curriculum/` | `lessons/sat_lessons.json` (15 FLN lessons, AUTHOR-DRAFT) + `outcomes/nipun.json` (8 outcomes) |
| `santali_organized/` + `scripts/convert_authored_chapters.py` | Authored G1/G2/G3 chapters (hi + Ol Chiki + webp) → pack layout, DRAFT-verbatim |
| `worksheet/` / `flashcard/` | Deterministic template generator + dep-free PDF writer / prebuilt decks |
| `ml/` | VAD/ASR/MT/TTS adapters, LoRA fine-tunes, ONNX export, benchmark harness |
| `backend/api/` | `POST /translate` (`TRANSLATION_BACKEND=satfinal` flagship hin↔sat bidi) |
| `backend/sync/` | JSONL correction queue + human gate (`AUTO_TRAIN_ENABLED=False`) |
| `scripts/` | `fetch_android_models.sh` (build-time only), pack builders, `demo_flow.py` |

## 6. On-device models

| Stage | Model | Size | Source |
|---|---|---|---|
| VAD | Silero VAD ONNX | ~1–2 MB | k2-fsa/sherpa-onnx release (build-time fetch) |
| ASR | NeMo EncDecCTC Hindi, sherpa layout | ~140 MB | NeMo → ONNX (build-time fetch, absent from repo) |
| MT (Santali) | IndicTrans2-dist-320M → ONNX INT8, 3-graph | 314–357 MB | ai4bharat base, team LoRA-r64 fine-tune + export |
| TTS (Santali) | Coqui VITS, 22050 Hz, Ol Chiki char tokens | ~110 MB | team fine-tune (`ml/tts/finetune_sat.py`) |
| Mundari | Deterministic phrasebook | ~3.7 MB | 17,826-row lookup; neural merge pending |

Dev-fixtures (never production): whisper-tiny ASR, `vits-zh` + espeak TTS shim, `Mock*` engines.

## 7. Training journey (~50 h reported)

- **MT (~30 h, Colab):** IndicTrans2-dist-320M (MIT) → LoRA-r64 bidi on verified pairs → merge → CT2 INT8 (held-out chrF 40.4 hi→sat / 45.8 sat→hi) → manual `torch.onnx.export` (opset17) → dynamic INT8.
- **TTS (~20 h, Lightning):** Coqui 0.27.5 on ~7.5 h Santali speech → `model.onnx + tokens + 231-entry lexicon`, `dataDir=""`.
- **ASR:** integrated, not retrained (adapter + VAD gating + honest errors).
- **Mundari:** phrasebook now; LoRA-r16 neural merge pending (excluded from APK until ready).

> Hour counts are team-reported (Colab/Lightning dashboards), not stored in git.

## 8. Content: curriculum, worksheets, flashcards

- **Curriculum (AUTHOR-DRAFT, never MT output):** 15 FLN lessons + 8 NIPUN outcomes, precomputed hi + Ol Chiki (U+1C50–U+1C7F), CC BY 4.0. SME sign-off required before APPROVED.
- **Authored chapters (Grades 1/2/3):** hi + Ol Chiki worksheets (10 items), 30-card decks with Ol Chiki backs, webp page art — converted deterministically, DRAFT-verbatim.
- **Pool PDFs:** smallest textbook PDFs shared by reference across remaining slots (dedup, not duplication).
- **Packs:** `sat_Olck-v*.vachakpack` (manifest + per-file sha256) install without APK rebuild; missing assets → `EngineResult.Err`, never crash.

## 9. Android engine & diagnostics

- Modules `:app :core :ml :content :sync` (AGP 8.5.2, Kotlin 1.9.24, JDK 17, targetSdk 35; ABIs arm64-v8a + x86_64). `:ml` uses sherpa AAR `compileOnly`, `:app` as `implementation`.
- `EngineProvider.real()` = Room content + ASR/VAD + ONNX MT + VITS TTS; `mock()` for unit tests only.
- **Diagnostics (measured, never estimated):** per-item latency vs < 3 s, APK + filesDir + DB bytes vs budget, `GIT_SHA` provenance, pack SHA, ASR fingerprint.
- **Tests:** `bash scripts/run_offline_tests.sh` (blocks sockets — any network = fail) · `./gradlew :ml:testDebugUnitTest :app:testDebugUnitTest` · `/usr/bin/python3 -m unittest discover -s <suite>` for `shared/orchestrator`, `backend/api`, `curriculum`, `ml/benchmarks`, `shared/schemas`, `offline/model_registry`.

## 10. Backend API & sync

- `POST /translate` `{text, source, target, context{grade,subject,learningOutcome}}` → `{sourceText, targetText, confidence, terminologyWarnings[], backend, …}`; `GET /health`.
- Backends: `mock` (fixture) · `baseline` (CT2 hi→sat) · **`satfinal` (default)** · `final` (stub). Run: `cd backend/api && /usr/bin/python3 app.py`.
- Sync: file-backed JSONL correction queue → human review → VERIFIED-only corpus export. Transport is side-loaded packs, never sockets.

## 11. Benchmarks (hard rule)

Dev-machine latency is **never** reported as Android latency — the harness's `forbid_dev_as_android` guard fails such reports. Per-device templates in [`production_READMEs/benchmarks/`](production_READMEs/benchmarks/); targets BLEU ≥ 50 / WER ≤ 15% / MOS ≥ 3.5 / < 3 s.

## 12. Quick start

```bash
# Python suites (use /usr/bin/python3, run from repo root)
/usr/bin/python3 -m unittest discover -s shared/orchestrator/tests
/usr/bin/python3 -m unittest discover -s backend/api/tests
/usr/bin/python3 -m unittest discover -s curriculum/tests
bash scripts/run_offline_tests.sh

# Translation service
cd backend/api && /usr/bin/python3 app.py   # POST /translate, satfinal default

# Android (needs SDK + JDK 17)
cd android && ./gradlew assembleDebug
./gradlew :ml:testDebugUnitTest :app:testDebugUnitTest
```

## 13. License & provenance (read before reusing anything)

- **Model license ≠ repo license.** Record repo + model + dataset + voice-consent provenance before merging anything external.
- Full registry: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) + [`docs/provenance/`](docs/provenance/) (canonical).
- `datasets/hin_mun` (Karya **BY-NC-SA-FS 1.0, non-commercial**) is **quarantined, never bundled**. `IN22-Gen/Conv` and FLORES-tagged rows are **eval-only, never train**.
- TTS training data has **no consent on file — prototype-only** (see [`VOICE_CONSENT.md`](VOICE_CONSENT.md)) until consent is filed or retraining lands on CC BY 4.0 corpora.
- Piper/espeak-ng are GPL-3.0 — training/dev-machine only, **never in the APK** (runtime: sherpa-onnx Apache-2.0 + ORT MIT).
- Fonts (Noto Sans Ol Chiki, Noto Sans Devanagari, Lexend) bundled offline under OFL.

## 14. Documentation index

Start here, in order — [`production_READMEs/`](production_READMEs/):

1. `01-what-is-vachak.md` — one definition + user flow
2. `02-why-vachak.md` — problem → solution table
3. `03-architecture-technical-components.md` — full technical map + hard rules
4. `04-ml-pipeline.md` — VAD → ASR → MT → TTS in detail
5. `05-models-repos-licenses.md` — models, repos, licenses
6. `06-training-journey.md` — compute log + stage timelines
7. `07-android-offline-engine.md` — modules, EngineProvider, packs, diagnostics, tests
8. `08-content-curriculum.md` — lessons, worksheets, flashcards, localization
9. `09-backend-api-sync.md` — API + correction queue
10. `10-ui-revamp-demo-flow.md` — UI revamp + 90 s demo script

Deep dives: `architecture/` (contracts, specs, latency) · `ml/` (provenance, sprints) ·
`benchmarks/` (runner contract, device reports) · `demo/` (judge script, WiFi-off acceptance) ·
`legal/` (reading copies; canonical files at repo root) · `story/` (why it exists + build journey) ·
`logs/` (dev log, changelog) · `content/` · `ui/` (+ mockups) · `phases/` · `00-source-map.md` (every doc accounted for).

> Evidence rule kept from the source docs: every claim cites a repo path. Training-hour
> numbers are team-reported, marked as such.
