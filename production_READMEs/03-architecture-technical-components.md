# 03 — Architecture & Technical Components

```
android/{app,core,ml,content,sync}/  # Gradle modules (settings.gradle.kts)
shared/orchestrator/ + shared/schemas/  # contracts + sequential state machine
offline/model_registry/ + modelpacks/   # pack install/checksum/rollback
backend/api/          # POST /translate, stdlib http.server, TRANSLATION_BACKEND=satfinal
backend/sync/         # teacher-correction queue, offline pack transport
curriculum/ worksheet/ flashcard/ localization/  # content DB + generators + l10n
ml/{pipeline,translation,tts,finetune,asr,benchmarks}/  # adapters, training/export, harness
scripts/ packages/packs/  # build_content_pack.py + build_modelpack.py + pack.py
datasets/ material/   # corpora (hin_sat draft, hin_mun quarantined, FLORES eval-only)
```

## Folder map (one line each)

| Path | Role |
|---|---|
| `android/` | Compose shell; UI depends only on `EngineProvider` |
| `shared/schemas/` | `Translation/ASR/TTS/Curriculum/Worksheet/Flashcard/Pack/Sync/Benchmark` protocols |
| `shared/orchestrator/` | `VAD→ASR→Context→NMT→Validator→TTS→Speaker`, 3000 ms budget |
| `offline/model_registry/` | `registry.py` install/validate/rollback, `manifest_schema.py` pack checksum |
| `modelpacks/` | `mundari/manifest.json` example, `sat_bidi_ct2_int8/`, `mundari_phrasebook/corpus.tsv` (17,826 rows) |
| `curriculum/` | `lessons/sat_lessons.json` (15 FLN lessons, AUTHOR-DRAFT), `outcomes/nipun.json` (8 outcomes) |
| `worksheet/` | `engine.py` deterministic generator, `pdf.py` dep-free writer, `templates/*.pdf` (7) |
| `flashcard/` | `engine.py` deck builder, `assets/*.png` (46 prebuilt) |
| `localization/` | `layer.py` hi/target/bilingual triples, `languages.json` (sat default) |
| `ml/pipeline/` | `vad_stream.py`, `asr_adapter.py`, `tts_adapter.py`, `pipeline.py` (T0–T4), `latency.py` |
| `ml/translation/` | `export_onnx_it2.py`, `infer_it2.py`, `bench_onnx.py`, Mundari `pipeline.py` |
| `ml/tts/` | `finetune_sat.py` (Coqui 0.27.5), `export_onnx.py`, `dataset/prepare_santali.py` |
| `ml/finetune/` | `it2_sat_bidi*`, `it2_mundari_lora_real` (r16 pending merge), `adapter_sat_bidi/` (r64) |
| `ml/benchmarks/` | `harness.py` (chrF/WER/CER), `run.py`, `device_matrix.py` |
| `ml/asr/` | `README.md` benchmark plan (IndicConformer/Vosk/whisper.cpp) |
| `backend/api/` | `app.py` stdlib server, `translation_service.py` (mock/baseline/satfinal/final) |
| `backend/sync/` | `queue.py` JSONL, `backend.py` human gate, `sync_service.py` pack assembler |
| `scripts/` | `fetch_android_models.sh` (build-time only), `build_modelpack.py`, `build_content_pack.py`, `pack.py`, `demo_flow.py` |
| `packages/packs/` | `sat_Olck-v*.vachakpack`, manifest (totalBytes 558 MB / budget 500 MB → withinBudget:false) |
| `datasets/hin_sat/` | 10,477 pairs (silver 10,245 + gold 116 + gold_rev 116), DRAFT stopgap |
| `datasets/hin_mun/` + `datasets/_quarantine/` | Karya BY-NC-SA-FS 1.0, quarantined, never bundled |
| `material/` | 32 CSVs, FLORES-tagged rows eval-only |
| `docs/` | `phases/PHASES.md`, `PLAN-P1..P7`, `P1-size-variance.md`, `architecture/ARCHITECTURE.md`, `DEMO.md`, `provenance/` |
| `THIRD_PARTY_NOTICES.md`, `VOICE_CONSENT.md` | License + voice-consent provenance |

## Hard rules (AGENTS.md)

1. No runtime network. 2. Sequential inference only. 3. Curriculum never MT-generated.
4. Worksheets/flashcards never AI-generated. 5. IN22-Gen/Conv eval-only, never train;
   FLORES rows never back score claims. 6. `hin_mun` quarantined, never bundled;
   TTS data prototype-only until consent. 7. Piper/espeak-ng GPL-3.0 dev-only, never in APK
   (APK runtime: sherpa-onnx Apache-2.0 + ORT MIT).
