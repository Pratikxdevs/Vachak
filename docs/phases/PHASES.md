# Vachak — Santali-Only Phases (Dispatch Map for Parallel AI Agents)

> Single language: **Santali (Ol Chiki)**. Mundari/Ho dropped. Each phase is independently assignable to an AI agent; wire via EngineContracts + language-pack paths.

## How to use

```bash
# Assign one agent per phase — they work on branches workstream/phase-N
Agent P1: Phase 1 (MT)  — ml/translation + android/ml
Agent P2: Phase 2 (TTS) — ml/tts + android/ml
Agent P3: Phase 3 (ASR)  — ml/asr + android/ml
Agent P4: Phase 4 (Curriculum) — curriculum/ + android/content
Agent P5: Phase 5 (Packs) — packages/ + android/sync
Agent P6: Phase 6 (Benchmark) — benchmarks/ + android diagnostics
Agent P7: Phase 7 (Demo) — docs/ + WiFi-OFF run
```

Wiring contract: `android/ml` adapters expose `TranslationEngine / AsrEngine / TtsEngine` behind `EngineProvider.real(context)`; `sync/` never does network I/O.

---

### Phase 0 — Shell (DONE) — do not reassign
**What exists:** Android shell (Compose/Room), SherpaAssets recursive copy, `EngineProvider.mock/real`, LatencyTracker, Vachak-* logs, whisper-tiny + vits-zh-aishell3 DEV-FIXTUREs, null AssetManager fix.
**Repo reused:** `k2-fsa/sherpa-onnx` (Apache-2.0) — `android/ml/libs/sherpa-onnx-1.13.0.aar`
**Next:** P1 replaces MockTranslationEngine.

---

### Phase 1 — On-device MT: Hin→Santali (Santali only)
**Goal:** Real IndicTrans2 distilled→ONNX int8 in `:ml` via ONNX Runtime Mobile; `Translate lesson` shows Ol Chiki.

| Reuse / Study | Repo | License | Role |
|---|---|---|---|
| **IndicTrans2** | `AI4Bharat/IndicTrans2` | MIT | Base NMT, tokenizer (`IndicTransToolkit`), training/infer pipeline |
| **COILD-MT-Corpus HIN-SAT** | IIT Patna / COILD (HF) | CC BY 4.0 | Primary fine-tune 20,603 pairs |
| **Education_v2 HIN-SAT** | COILD | CC BY 4.0 | Domain adapt |
| **OneMTBig** | `vmujadia/onemtbig` | MIT? verify | Low-resource multilingual arch ref (has Santali `sat_Olck`) |
| **ONNX Runtime** | `microsoft/onnxruntime` | MIT | Mobile inference |
| **CTranslate2 baseline** | `ml/translation/mundari/it2_ct2_baseline.py` (existing) | MIT | Lab baseline proven (`नमस्ते→ନମସ୍କାରଂ`) — do not ship CT2, export ONNX |
| **IndicVoices** (pipeline) | `AI4Bharat/IndicVoices` | CC BY 4.0 | Data pipeline reference |

**Input:** `datasets/` manifests, `ml/translation/` pipeline, `indictrans2-onnx-export/` attempts
**Output:** `assets/vachak_models/mt/{model.onnx, tokens, sp.model}` ≤180MB, `android/ml/src/.../IndicTrans2Adapter.kt`
**Interfaces:** `TranslationEngine.translate(hin:String):String` (Ol Chiki), `EngineProvider.real()`
**Guard:** No IN22 in train, quantize ≤180MB, ≤0.5s, no INTERNET permission added
**Docs:** `ml/translation/README.md`, `docs/MODEL_AND_DATA_PROVENANCE.md`

---

### Phase 2 — Santali Voice (TTS): VITS → sherpa-onnx
**Goal:** Fine-tuned Santali VITS replaces Chinese vits-zh-aishell3; `Speak` is audible Santali.

| Reuse / Study | Repo | License | Role |
|---|---|---|---|
| **sherpa-onnx** | `k2-fsa/sherpa-onnx` | Apache-2.0 | TTS runtime (OfflineTts via ONNX), AAR already vendored |
| **Coqui TTS** | `coqui-ai/TTS` | MPL-2.0 | TTS training fallback |
| **IndicVoices Santali** | `AI4Bharat/IndicVoices` + HF | CC BY 4.0 | 19,779 train Santali utterances |
| **Nirantar Santali** | `adjaysagar/nirantar` (HF) | CC BY 4.0? verify | 13,503 utt / 161h / 433 spk / 8 districts — diversity |
| **Rasa** | `ai4bharat/Rasa` (HF) | CC-BY-4.0 | TTS methodology / clean license subset |
| **Common Voice Santali** | Mozilla Common Voice | CC BY 4.0 | ~533 clips supplementary |
| **MunTTS** (reference only) | `microsoft/MunTTS-A-Text-to-Speech-System-For-Mundari` | Research ref | VITS/XTTS training script pattern — **do not use Mundari data** |
| **simple-live-speech-translate** | `rgiduthuri/simple-live-speech-translate-in-python` | MIT | Pipeline orchestration ref |

**Input:** `ml/tts/` training pipeline, `models/` checkpoints
**Output:** `vits-sat.onnx` + `tokens.txt` + `lexicon.txt` (+ `espeak-ng-data` if needed) 20–80MB; adapter reads pack path
**Interfaces:** `TtsEngine.speak(text:OlChiki):FloatArray`, pack path via `sync/` (P5)
**Guard:** Piper GPL-3.0 excluded from APK; voice consent + license in `THIRD_PARTY_NOTICES.md`; Ol Chiki validate
**Note:** Uktam is reference arch only — do not fork (too heavy for 2GB).

---

### Phase 3 — Hindi ASR E2E (Offline, Verified on Emulator)
**Goal:** Push-to-talk → real Hindi transcript; verifiable without mic.

| Reuse / Study | Repo | License | Role |
|---|---|---|---|
| **sherpa-onnx** | `k2-fsa/sherpa-onnx` | Apache-2.0 | ASR runtime (OfflineRecognizer + Silero Vad via ONNX) |
| **IndicConformerASR** | `AI4Bharat/IndicConformerASR` | MIT/Apache-2.0 | Hindi ASR benchmark candidate |
| **Indic-STT server** | `shivsinghin/Indic-STT` | MIT | IndicConformer + ONNX Runtime ref impl |
| **IndicVoices (collection pipeline)** | `AI4Bharat/IndicVoices` | CC BY 4.0 | Data collection pipeline |
| **Vosk** | `alphacep/vosk-api` | Apache-2.0 | Lightweight fallback benchmark |
| **whisper.cpp** | `ggerganov/whisper.cpp` | MIT | Whisper tiny/base benchmark |
| **Nirantar** | `adjaysagar/nirantar` | Verify | ASR robustness (geographic diversity) |

**Input:** `ml/asr/` benchmarks, `android/ml/SherpaAssets` + `VadStream`
**Output:** Benchmark pick (30–80MB model), `SherpaAsrAdapter` E2E flow, WAV-fed debug path
**Interfaces:** `AsrEngine.recognize(pcm:ShortArray):String`, `Vachak-ASR` logs, `LatencyTracker`
**Guard:** 16k mono PCM16, ≤1s, sequential execution only

---

### Phase 4 — Curriculum (FLN + NIPUN + Worksheets + Flashcards)
**Goal:** Real lessons replace MockCurriculumEngine; Room-backed, precomputed translations.

| Reuse / Study | Repo | License | Role |
|---|---|---|---|
| _(first-party)_ | `curriculum/` authoring | Vachak | FLN lessons, NIPUN mapping |
| _(first-party)_ | `android/content/` + Room | Vachak | `ContentEngine`, DB `content/` |
| _(first-party)_ | `worksheet/` + `flashcard/` | Vachak | Template PDFs + prebuilt asset PNGs |

**Hard rules:** Translations precomputed (not on-device MT); worksheets template-based (not AI-generated); flashcards prebuilt assets; never ship MACHINE_TRANSLATED as approved pedagogy
**Output:** `curriculum/` lessons/outcomes + `worksheet` templates + `flashcard` images → Room DB (10–30MB + 20–50MB)
**Interfaces:** `ContentEngine.getLesson(id):Lesson`, `getWorksheets(lesson)`, `getFlashcards(lesson)`

---

### Phase 5 — Language-Pack + Offline Installer
**Goal:** Signed offline packs (models+curriculum) installed without network.

| Reuse / Study | Repo | License | Role |
|---|---|---|---|
| _(first-party)_ | `packages/` pack builder | Vachak | Manifest + sha256 + license list |
| _(first-party)_ | `android/sync/` installer | Vachak | Verify → copy → Room register; **not a network client** |
| _(first-party)_ | Model budget table | `AGENTS.md` | Enforce ~500MB total |

**Output:** Versioned `.vachakpack` (MT+TTS+ASR+curriculum), `Manage Packs` UI, pack-path config for adapters
**Guard:** Zero `INTERNET` permission; engines reload from active pack path (enables P2 swap)

---

### Phase 6 — Budget & Latency Proof (2GB, ~500MB, <3s)
**Goal:** Prove sequential pipeline on 2GB device.

| Reuse / Study | Repo | License | Role |
|---|---|---|---|
| **ONNX Runtime Mobile** | `microsoft/onnxruntime` | MIT | Quantized inference perf |
| **sherpa-onnx benchmarks** | `k2-fsa/sherpa-onnx` | Apache-2.0 | Latency patterns |
| _(first-party)_ | `benchmarks/` + `LatencyTracker` | Vachak | `benchmarks/translation_benchmark.py` etc. |

**Output:** `benchmarks/` harness + `docs/benchmarks/BENCHMARK_REPORT.md`; Diagnostics screen green (<3s, ≤2GB RSS, ≤500MB)
**Guard:** One model resident at a time; free native handles between legs

---

### Phase 7 — Demo Acceptance (WiFi OFF)
**Goal:** Full SIH script passes offline with evidence.

**Script:** open → lesson loads → WiFi OFF → translate lesson (P1) → play audio (P2) → worksheet (P4) → flashcard (P4) → push-to-talk Hindi (P3) → show Santali → play audio → <3s (P6) → diagnostics. No hidden internet.

**Output:** `docs/demo-acceptance.md` + screens + `adb logcat -s Vachak-*` + `LatencyTracker` dumps + `THIRD_PARTY_NOTICES.md` completeness

---

## Wiring Map (for agents working in parallel)

```
packages/ ──pack──► android/sync/ ──register──► Room
                                         │
android/ml/{IndicTrans2Adapter,SherpaAsrAdapter,SherpaTtsAdapter}
   ▲ reads active pack paths ◄────────────┘
   └─► EngineProvider.real(context) ──► android/app/MainScreen (Translate/Speak/PTT)
                                            │
curriculum/ ──author──► android/content/ContentEngine (Room)
                                            │
             LatencyTracker ──► Diagnostics + benchmarks/
```

## Agent Dispatch Commands (copy-paste)

```bash
# After /clear, each agent:
"Work on Vachak Phase N — Santali only — read docs/PHASES.md Phase N, .planning/ROADMAP.md, AGENTS.md, and .planning/phases/0N-*/01-CONTEXT.md before coding. Follow repo licenses in the table; record in THIRD_PARTY_NOTICES.md."

# Execution order (respect Depends on):
Wave 1: P1 + P4 (parallel, no dep conflict)
Wave 2: P2 + P3 (need P1 shell)
Wave 3: P5 (needs P2+P4)
Wave 4: P6 (needs P1+P2+P3)
Wave 5: P7 (needs P5+P6)
```

*Last updated: 2026-08-29 Santali-only*
