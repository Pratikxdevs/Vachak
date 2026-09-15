# Roadmap: Vachak SIH26042 — Santali (Ol Chiki) Only

## Overview

Santali-only offline tablet: Hindi teacher speaks → offline Hindi ASR → IndicTrans2 Hin→Santali MT (ONNX) → Santali VITS voice (Ol Chiki), plus FLN curriculum, NIPUN mapping, template worksheets and flashcard assets, all packed as signed offline installers and proven <3s on 2GB with WiFi OFF. Mundari dropped.

## Phases

- [x] **Phase 1: On-device MT (Hin→Santali)** — IndicTrans2 dist-320M → fine-tune → ONNX int8 → :ml adapter (2026-08-29)
- [x] **Phase 2: Santali Voice (TTS)** — VITS fine-tuned on IndicVoices/Nirantar/Rasa → sherpa-onnx
- [x] **Phase 3: Hindi ASR E2E** — Offline Hindi ASR (whisper-tiny/IndicConformer benchmark) → push-to-talk verified (2026-08-29)
- [x] **Phase 4: Curriculum Content** — FLN lessons + NIPUN + worksheets + flashcards in Room
- [x] **Phase 5: Language-Pack + Offline Installer** — Signed packs (models+curriculum), sync/ is installer not client (2026-08-29)
- [ ] **Phase 6: Budget & Latency Proof** — <3s sequential, 2GB RAM, ~500MB storage on device
- [ ] **Phase 7: Demo Acceptance** — WiFi-OFF full script with evidence
- [x] **Phase 8: Live Wiring MVP** — Wire AudioRecord→VAD→ASR→MT→TTS to overhauled LiveScreen (offline, sequential, <3s) so speaking Hindi shows live transcription + Santali translation + audio (2026-08-29)
- [ ] **Phase 9: ML Wiring Fix — Correct Full-Sentence Translation** — Fix garbage/same-output wiring, proper BPE+ONNX pipeline, full-sentence correctness (latency deferred)

## Phase Details

### Phase 1: On-device MT (Hin→Santali)
**Goal**: Replace MockTranslationEngine with real IndicTrans2 distilled+quantized ONNX in :ml
**Depends on**: Nothing (shell already loads; Python CT2 baseline proves model)
**Requirements**: MT-01, MT-02, MT-03
**Success Criteria**:
  1. "Translate lesson" shows real Santali Ol Chiki from on-device model, not [DEV-FIXTURE]
  2. APK offline, MT slice 100–180MB, zero INTERNET calls (audited)
  3. On-device MT ≤0.5s (LatencyTracker)
**Plans**: 3 plans

Plans:
- [x] 01-01: Export IndicTrans2 dist-320M → ONNX + int8 quant (≤180MB, arm64-v8a) — 357 MB INT8, variance doc, onnx.checker PASS, 100% tokenizer parity
- [x] 01-02: :ml IndicTrans2 Adapter (SP tokenize → ORT infer → Ol Chiki decode) + EngineProvider wiring — ORT 1.18, IndicProcessorPort, Vachak-MT, :ml/:app green
- [x] 01-03: Offline verification, latency gate, no-network audit — IndicTrans2AdapterTest 8/8, TranslationEngineTest, benchmarks/translation_benchmark.py, BENCHMARK_REPORT P1 row, no INTERNET

### Phase 2: Santali Voice (TTS)
**Goal**: Real Santali VITS (not Chinese DEV-FIXTURE) → audible Ol Chiki speech via sherpa-onnx
**Depends on**: Phase 1
**Requirements**: TTS-01, TTS-02
**Success Criteria**:
  1. "Speak" plays audible Santali for a Santali phrase (not 12ms blip)
  2. TTS slice 20–80MB, loads from pack path, offline, no Piper in APK
  3. Voice consent + CC-BY-4.0 dataset licenses recorded
**Plans**: 2 plans

Plans:
- [x] 02-01: Fine-tune Santali VITS (IndicVoices 19,779 + Nirantar 13,503 + Rasa) → export ONNX/tokens/lexicon
- [x] 02-02: SherpaOnnxTtsAdapter pack-path wiring + provenance + audible verification

### Phase 3: Hindi ASR E2E
**Goal**: Push-to-talk → real Hindi transcript on-device, verifiable on emulator without mic
**Depends on**: Phase 1
**Requirements**: ASR-01, ASR-02
**Success Criteria**:
  1. Push-to-talk populates ASR result with plausible Hindi
  2. WAV-fed ASR debug path works on emulator/headless
  3. ASR ≤1s, Silero VAD gated, no network
**Plans**: 1 plan

Plans:
- [x] 03-01: ASR E2E flow (PCM16→VAD→decode→UI) + WAV fallback + LatencyTracker — SherpaAsrAdapter bridged Short/32768, VAD gated, MainScreen ASR result + WAV Load WAV→ASR, RECORD_AUDIO guard, LatencyTracker ≤1s

### Phase 4: Curriculum Content
**Goal**: Real FLN lessons + NIPUN mapping + template worksheets + prebuilt flashcards in Room
**Depends on**: Phase 1
**Requirements**: CUR-01, CUR-02, CUR-03
**Success Criteria**:
  1. Lesson list/detail from Room (no DEV-FIXTURE), precomputed Santali translations stored
  2. Template worksheets render as PDF/print; flashcards as prebuilt assets (no runtime gen)
  3. Every lesson has NIPUN outcome ID + descriptor
**Plans**: 1 plan

Plans:
- [x] 04-01: Author curriculum/ → ContentEngine (Room) + worksheet/flashcard pipeline

### Phase 5: Language-Pack + Offline Installer
**Goal**: Signed offline packs (models+curriculum) via sync/ installer; no network permission
**Depends on**: Phase 2, Phase 4
**Requirements**: PACK-01, PACK-02
**Success Criteria**:
  1. packages/ builder emits versioned pack (manifest + sha256 + license list)
  2. sync/ verifies hash, copies to app data, registers in Room, engines reload from pack paths
  3. No android.permission.INTERNET; app runs fully after pack install with WiFi OFF
**Plans**: 1 plan

Plans:
- [x] 05-01: Pack builder + sync installer + Manage Packs UI — sat_Olck-v0.1.0.vachakpack 347M + manifest sha256/licenses, PackInstaller zip-slip + Room + no network, adapters pack-aware reload, ManagePacksScreen SAF

### Phase 6: Budget & Latency Proof
**Goal**: Prove <3s E2E sequential on 2GB, ~500MB total, within model budget table
**Depends on**: Phase 1, Phase 2, Phase 3
**Requirements**: PERF-01, PERF-02
**Success Criteria**:
  1. Diagnostics shows ASR≤1s + MT≤0.5s + TTS≤1s = total <3s, sequential only
  2. Peak RSS ≤2GB, APK+pack ≤500MB (budget table green)
  3. Reproducible script under benchmarks/ + report
**Plans**: 1 plan

Plans:
- [x] 06-01: Sequential enforcement + LatencyTracker + benchmark harness + report

### Phase 7: Demo Acceptance
**Goal**: WiFi-OFF full demo passes with evidence, no hidden internet
**Depends on**: Phase 5, Phase 6
**Requirements**: DEMO-01
**Success Criteria**:
  1. Script passes: open→lesson→WiFi OFF→translate→play audio→worksheet→flashcard→PTT→Santali→audio→<3s→diagnostics
  2. Evidence: screens + adb logcat -s Vachak-* + LatencyTracker + pack manifest
  3. docs/demo-acceptance.md signed off; THIRD_PARTY_NOTICES complete
**Plans**: 1 plan

Plans:
- [ ] 07-01: Demo run + evidence capture + acceptance doc

### Phase 8: Live Wiring MVP
**Goal**: Wire AudioRecord→VAD→ASR→MT→TTS to overhauled LiveScreen (offline, sequential, <3s) so speaking Hindi shows live transcription + Santali translation + audio
**Depends on**: Phase 3, Phase 5
**Requirements**: ASR-01, PERF-01, DEMO-01
**Success Criteria**:
  1. FAB hold-to-talk captures 16k mono PCM16 → VAD-gated ASR shows real Hindi transcription live (not fixture) in LiveScreen DualLangCard
  2. Translate shows real Santali Ol Chiki (sat_Olck) from IndicTrans2 with Ol Chiki validation tick, ≥1s total not mocked
  3. Play Audio synthesizes Santali at 22050Hz via AudioTrack audible >200ms, sequential lock, LatencyTracker T0→T4 <3s logged Vachak-*, no INTERNET, pack-aware
**Plans**: 3 plans

Plans:
- [x] 08-01: Audio capture + ASR live transcription wiring — LiveScreen FAB → AudioRecord 16k mono PCM16 + RECORD_AUDIO launcher + VAD → SherpaAsrAdapter → hindiText DualLangCard, Vachak-ASR/VAD + T0→T1 ≤1s
- [x] 08-02: MT live translation wiring (sat_Olck + Ol Chiki) — sat_Olck translate + OlChikiValidator + Vachak-MT + T2 ≤500, no delay mock
- [x] 08-03: TTS playback + sequential orchestration + latency/pack/offline — 22050Hz AudioTrack audible >200ms + sequential lock + T0→T4 <3s Vachak-Latency + pack-aware + no INTERNET

### Phase 9: ML Wiring Fix — Correct Full-Sentence Translation
**Goal**: Fix faulty ML wiring so Hindi input from app (LiveScreen input + mic) flows correctly through preprocess→BPE→encoder→decoder+pastKV→postprocess and outputs correct, varied Santali Ol Chiki for full sentences (not garbage/same-output). Latency deferred.
**Depends on**: Phase 1, Phase 8
**Requirements**: MT-01, MT-02, MT-03
**Success Criteria**:
  1. Hindi full sentences (e.g. "बच्चों, पाँच आम गिनो।", "मैं ठीक हूँ", "एक", "पानी") typed or spoken in LiveScreen translate to distinct, correct Santali Ol Chiki (verified via curated GOLD + python parity), not repetitive/hallucinated same output; "नमस्ते" curated → "ᱡᱚᱦᱟᱨ"
  2. End-to-end pipeline traced: App input → IndicProcessorPort preprocess → BPE encode (merges 245k) → ORT encoder [1,seq,512] → greedy decoder with real past KV → batchDecode Metaspace → postprocess, with logs Vachak-MT tokenize/encode/decode + OlChikiValidator PASS
  3. APK built again with fixed :ml IndicTrans2Adapter + SherpaAssets mt marker + offline fonts, manual verify Live typing + mic produce varied Santali
**Plans**: 3 plans (research mapper + BPE/ONNX fix + curated+gating verification)

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. On-device MT | 3/3 | Complete | 2026-08-29 |
| 2. Santali Voice | 2/2 | Complete | 2026-08-29 |
| 3. Hindi ASR E2E | 1/1 | Complete | 2026-08-29 |
| 4. Curriculum | 1/1 | Complete | 2026-08-29 |
| 5. Language-Pack | 1/1 | Complete | 2026-08-29 |
| 6. Benchmark | 1/1 | Complete | 2026-08-29 |
| 7. Demo | 0/1 | Not started | - |
| 8. Live Wiring MVP | 3/3 | Complete | 2026-08-29 |
| 9. ML Wiring Fix | 3/3 | Complete | 2026-08-31 |

### Phase 10: Dual-Language 100% No-Leak (Organisable)
**Goal**: No functional leak - every page supports Santali + Mundari adapters, live voice playback, debug UI, and close remaining 20-25%
**Depends on**: Phase 9
**Requirements**: MT-01, TTS-01, ASR-01, PERF-01, DEMO-01
**Success Criteria**:
  1. All pages adapter-switchable Santali<->Mundari with no hard-coded lang
  2. Live PTT Hindi->Santali/Mundari Ol Chiki via merged LoRA CT2 223M + VITS 22050Hz <3s sequential, mic closes
  3. Debug UI live latency/budget + log viewer, 2GB <3s captured, SME DRAFT watermark
**Plans**: 3 plans

### Phase 12: PDF Worksheets — Ship santali_organized PDFs (30 slots)
**Goal**: Real worksheets for 5 grades x 6 chapters = 30 slots from two sources — authored `/1` + `/3` chapter bundles (JSON + webp, DRAFT-verbatim via build-time converter) wired explicitly for Grades 1/3, plus 20 pool slots by reference from 3–5 unique smallest PDFs (<=25 MB); everything opens in-app; decks intact except the 9 supplied G1/G3 ones
**Depends on**: Phase 4, Phase 5
**Requirements**: WS-01, WS-02, WS-03, WS-04, WS-05, WS-06 (phase-local IDs, see .planning/phases/12-pdf-worksheets/12-CONTEXT.md)
**Success Criteria**:
  1. All 30 chapter slots (incl. reserved filling-and-lifting + G1/G3/G5 6th-slots) resolve to shipped content and open in-app offline with proper hi/Ol Chiki titles, never null/blank
  2. Unique pool bytes <=25 MB + authored ≈2 MB in sat_Olck-v0.3.0.vachakpack; DRAFT preserved, AUTO_EXTRACTED for pool-derived, provenance recorded; no INTERNET, no new deps
  3. Non-explicit decks byte-identical (only 9 supplied DRAFT decks change); JSON worksheet path preserved as fallback
**Plans**: 2 plans (converter+pool content/pack, reader+thumbnails+viewer)

### Phase 13: Chapter-page cleanup (2 options + live counts)
**Goal**: Chapter page shows ONLY Worksheets + Flashcards (no gallery/Read/questions); worksheet "needs review" badge + answer-key sermon removed; grade rows show live pack counts in demo order
**Depends on**: Phase 12
**Requirements**: CU-01, CU-02, CU-03 (phase-local IDs, see .planning/phases/13-chapter-cleanup/13-01-PLAN.md)
**Success Criteria**:
  1. Chapter page renders header + 2 cards, nothing else; routes unchanged
  2. Worksheets open with zero review chrome; decks/screens/manifest/ml untouched
  3. Grade rows reflect live installed/bundled pack data, pack order preserved
**Plans**: 1 plan (executed 2026-09-16, compile + unit tests green)

### Phase 14: Pack truth — richest source wins (answers off, single-pack APK)
**Goal**: Kill stale-pack shadowing (identical v0.2.0 decks can never surface over v0.3.0); worksheets show questions only (no answers, no review chrome); deck faces fit; single-pack APK under the 750 MB user cap
**Depends on**: Phase 12, Phase 13
**Requirements**: PT-01, PT-02, PT-03, PT-04 (phase-local IDs, see .planning/phases/14-pack-truth/14-01-PLAN.md)
**Success Criteria**:
  1. Richest-source rule in bundle + counts (score = questions + cards + 1000×pdf)
  2. Zero answers/review chrome on worksheets; scroll-fit decks, flip unchanged
  3. Clean APK 646.4 MB with only v0.3.0 (18.9 MB), < 750 MB
**Plans**: 1 plan (executed 2026-09-16, clean build measured)

### Phase 15: Content viewer — direct-asset PDFs + full-size images
**Goal**: Pool PDFs open in-app via 3 independent paths (installed → bundled → direct assets); worksheet images fully visible; text-only sheets open
**Depends on**: Phase 14
**Requirements**: CV-01, CV-02, CV-03 (phase-local IDs, see .planning/phases/15-content-viewer/15-01-PLAN.md)
**Success Criteria**:
  1. Every pool slot opens its PDF readably with zero packs installed
  2. Worksheet images border-to-border; text-only worksheets open
  3. Clean APK 663.8 MB < 750 MB
**Plans**: 1 plan (executed 2026-09-16, clean build measured)
