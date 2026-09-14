# Third-Party Notices

This file records licenses for all external dependencies, models, and datasets used in SIH26042.

## Code Dependencies

| Component | Repository | License |
|-----------|-----------|---------|
| IndicTrans2 | https://github.com/AI4Bharat/IndicTrans2 | MIT |
| IndicTransToolkit (`IndicProcessor` pre/postprocess) | https://github.com/AI4Bharat/IndicTransToolkit | MIT |
| `transformers` + `tokenizers` (Rust) + `sentencepiece` (SPM) | huggingface/transformers, huggingface/tokenizers, google/sentencepiece | Apache-2.0 / MIT / Apache-2.0 |
| `onnx` + `onnxruntime` + `onnxruntime.transformers` | https://github.com/onnx/onnx, https://github.com/microsoft/onnxruntime | MIT (onnx MIT, ORT MIT) |
| `onnxruntime.quantization` (`quantize_dynamic`) | `microsoft/onnxruntime` | MIT |
| `torch` (`torch.onnx.export` manual wrappers, not `optimum`) | pytorch/pytorch | BSD-3-Clause |
| IndicConformerASR | https://github.com/AI4Bharat/IndicConformerASR | MIT |
| sherpa-onnx | https://github.com/k2-fsa/sherpa-onnx | Apache-2.0 |
| ONNX Runtime | https://github.com/microsoft/onnxruntime | MIT |
| Vosk | https://github.com/alphacep/vosk-api | Apache-2.0 |
| whisper.cpp | https://github.com/ggerganov/whisper.cpp | MIT |
| Piper | https://github.com/OHF-Voice/piper1-gpl | GPL-3.0 |
| Coqui TTS | https://github.com/coqui-ai/TTS | MPL-2.0 |
| Uktam | https://github.com/Uktam-ai/uktam | — |

## Models

| Model | Source | License | Notes |
|-------|--------|---------|-------|
| IndicTrans2 distilled 320M `ai4bharat/indictrans2-indic-indic-dist-320M` (HF rev `ffb7582`, 747 MB safetensors) | AI4Bharat | **MIT** (repo LICENSE + model `LICENSE` on HF = MIT; see `hf_hub_download` check) | Base MT for **Hin→Santali (sat_Olck Ol Chiki)** — Santali-only per `docs/PHASES.md`. Exported to **ONNX INT8** (`android/ml/src/main/assets/vachak_models/mt/`, 357 MB, opset17, 3-graph encoder/decoder/decoder_with_past, `per_channel=True` dynamic quant) — `onnx.checker` PASS, ORT loadable. Variance vs 180 MB budget documented in `docs/phases/P1-size-variance.md`. |
| IndicTrans2 CT2 INT8 (`models/indictrans2_ct2_int8/`, 312 MB) | AI4Bharat weights → `ctranslate2` `TransformersConverter` | MIT (weights) + MIT (CT2 runtime) | Lab oracle only (`ml/translation/mundari/it2_ct2_baseline.py`), **not shipped** in APK. Proved `नमस्ते।→ᱦᱚᱞᱮ ᱜᱤᱫᱽᱨᱟᱹᱠᱚ ᱾` (Ol Chiki). |
| IndicTrans2 ONNX FP32 `scratch/indic-indic-onnx` (1226 MB after `onnx_bundle_optimize`) | Derived from `indictrans2-indic-indic-dist-320M` via `indictrans2-onnx-export/` | MIT-derivative (same MIT as base) | FP32 oracle for parity: 100% token/text vs PyTorch on 1100 fixtures (`fixtures/parity-report-indic-indic.json`). INT8 derived from this FP32. |
| Quipus 0.6 speechv2 | hyperneuronAILabs | MIT | Reference only, ~1.24GB, not on-device |

## Datasets

| Dataset | Source | License | Notes |
|---------|--------|---------|-------|
| COILD-MT-Corpus | IIT Patna | CC BY 4.0 | **20,603 HIN-SAT pairs — authoritative CC BY 4.0 production MT** |
| Education_v2 | COILD | CC BY 4.0 | Education domain (HIN-SAT) — CC BY 4.0, used for domain adaptation |
| Agriculture_v2 | COILD | CC BY 4.0 | Vocabulary transfer only |
| IndicVoices | AI4Bharat | CC BY 4.0 | Santali speech |
| Common Voice Santali | Mozilla | CC BY 4.0 | Verify version before use |
| BPCC | AI4Bharat | varies | Check artifact-level license |
| IN22-Gen/Conv | AI4Bharat | — | **Eval only — never train** |
| `datasets/hin_sat` synthetic — **`hin_sat` 10,477 pairs (silver 10,245 + gold 116 + gold_rev 116)** + `classroom_10k` | Vachak synthetic (conservative grammar templates + lexicon + number system; gold from published Hindi→Santali phrase table) | **DRAFT — NOT approved pedagogy** / CC BY 4.0 intended (synthetic) | **AUTHOR-DRAFT, 2.2% verified** — `verify_report.json: verified 221 / silver 10,245 = 2.2%` (mean hi2sat_f1 0.397, sat2hi 0.56). Remaining 10,024 review. Keep 10k synthetic as DRAFT; do NOT ship as verified curriculum. See `datasets/hin_sat/manifest.json` (total_pairs 10,477, tiers silver/gold/gold_rev) + `verify_report.json` + `corpus.tsv` (headerless hi<TAB>sat). Production MT remains COILD 20,603 CC BY 4.0. |
| `datasets/hin_mun` (Hindi–Mundari) | Karya Inc. | **BY-NC-SA-FS 1.0 (NON-COMMERCIAL, quarantined — NOT bundled)** | **QUARANTINED** — copy of LICENSE at `datasets/_quarantine/hin_mun/LICENSE.txt` + README marker. Never bundled in `.vachakpack` (enforced in `packages/build_pack.py` allowlist). Adapter `it2_mundari_lora` is research-only. See `datasets/_quarantine/README.md`. |

## UI

| Component | Repository | License | Notes |
|-----------|-----------|---------|-------|
| compose-cupertino | https://github.com/alexzhirkevich/compose-cupertino (cloned at `compose-cupertino/` + **0.1.0-alpha04 binary** `io.github.alexzhirkevich:cupertino` + `cupertino-core` in `android/app/build.gradle.kts:62`) | Apache-2.0 | **MAIN PAGE OVERHAUL — real usage:** `CupertinoTheme` (accent Forest #285943, `VachakCupertinoLight/Dark` in `ui/theme/Theme.kt:38`), `CupertinoScaffold` + `CupertinoTopAppBar` + `CupertinoNavigationBar`/`CupertinoNavigationBarItem` in `ui/VachakApp.kt:10`, `CupertinoButton` `filled/tinted/gray` + `CupertinoSection`/`SectionItem` + `CupertinoActivityIndicator` in `ui/screens/HomeScreen.kt:1`, `LiveScreen.kt`, `AuthScreen.kt`. Translucent haze via scaffold `appBarsBlurRadius`. Kotlin 1.9.23 + Compose 1.6.8 compatible (0.1.0-alpha04), no React. |
| Material3 / Compose | androidx.compose | Apache-2.0 | UI runtime — coexists with Cupertino via `VachakTheme { MaterialTheme { CupertinoTheme } }` |

## Important Notes

- Piper successor is GPL-3.0. Handle licensing deliberately.
- Model license may differ from repository license. Verify before redistribution.
- Never redistribute gated/restricted artifacts without satisfying their terms.
- Keep original copyright notices for all third-party components.

## First-Party Modules (this delivery)

These are Vachak-authored; provenance of the external code they reuse is above.
No runtime network calls are made by any of them (enforced by the offline
network-request audit in `ml/benchmarks` and by `EngineContracts.SyncManager`).

| Module | Reuses (reference) |
|--------|--------------------|
| shared/orchestrator | IndicTrans2 (NMT contract), sherpa-onnx (ASR/TTS/VAD) |
| backend/sync | — (offline queue; transport via offline package installer) |
| ml/benchmarks | IndicTrans2, sherpa-onnx (measured, not bundled) |
| android/ml/orchestrator | EngineContracts (ASR/MT/TTS/LanguagePack), sherpa-onnx |
| scripts | shared/orchestrator, ml/benchmarks |

Model/data provenance is tracked in `docs/MODEL_AND_DATA_PROVENANCE.md`.

## Mundari-specific additions (PHASE 7 / PHASE 8 — scaffolding)

| Component | Source | License | Notes |
|-----------|--------|---------|-------|
| Karya Hindi–Mundari corpus/speech | Karya (AI4Bharat/IIITB) | **BY-NC-SA-FS 1.0 (NON-COMMERCIAL)** | **DATA ACCESS PENDING** — NOT available here; never trained/evaluated on it |
| microsoft/MunTTS | microsoft/MunTTS | Verify per-artifact | Architecture reference only (VITS/XTTS) for Mundari TTS; no checkpoint produced |
| eSpeak NG | espeak-ng | GPL-3.0 | Fallback TTS stand-in, labeled NOT final Mundari; local use only |
| sherpa-onnx public voice | k2-fsa/sherpa-onnx | Apache-2.0 | Fallback TTS stand-in; final Mundari voice is a separate drop-in adapter |
| MockTranslationEngine / DEV FIXTURE WAV | SIH26042 scaffolding | Project-internal | Synthetic fixtures for offline tests; NOT real output |

- See `docs/MODEL_AND_DATA_PROVENANCE.md` for full Mundari provenance and the
  DATA ACCESS PENDING note. The final Mundari NMT/TTS models are drop-in adapters
  (LoRA over IndicTrans2; VITS over MunTTS topology) — not shipped until gated data
  is licensed.

## Vendored sherpa-onnx runtime + DEV-FIXTURE models (integration pass)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| sherpa-onnx AAR 1.13.0 | k2-fsa/sherpa-onnx (GitHub release v1.13.0) | Apache-2.0 | Vendored at `android/ml/libs/sherpa-onnx-1.13.0.aar`. Bundles native `libsherpa-onnx-jni.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`, and `libonnxruntime.so` (ONNX Runtime, MIT) for arm64-v8a. |
| whisper-tiny (multilingual) | k2-fsa/sherpa-onnx whisper example | Apache-2.0 | DEV-FIXTURE Hindi ASR (`assets/vachak_models/asr/`). Supports Hindi transcription. NOT the final Hindi/Mundari production ASR. |
| vits-zh-aishell3 | k2-fsa/sherpa-onnx tts example | Apache-2.0 | DEV-FIXTURE TTS voice (`assets/vachak_models/tts/`). NOT the final Mundari voice. |
| silero_vad.onnx | k2-fsa/sherpa-onnx | Apache-2.0 | DEV-FIXTURE VAD (`assets/vachak_models/vad/`). |

- The IndicTrans2 MT baseline wired in `backend/api` + `ml/translation/mundari/it2_ct2_baseline.py`
  runs REAL inference (CTranslate2 over `models/indictrans2_ct2_int8`) and produced
  `नमस्ते। → ନମସ୍କାରଂ।` (Hindi→**Santali**, NOT Mundari). Mundari remains a gated final model.
- Attribution (Apache-2.0 / MIT notices) for sherpa-onnx and ONNX Runtime is preserved inside the
  AAR; we did NOT strip any LICENSE/NOTICE files (per project license policy).
- All models are downloaded by `scripts/fetch_android_models.sh` from k2-fsa/sherpa-onnx public
  releases (Apache-2.0). No runtime network calls; bundling is offline.

## Phase 1 — On-device MT Hin→Santali (01-01, Santali-only)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| `ai4bharat/indictrans2-indic-indic-dist-320M` (HF `models--ai4bharat--indictrans2-indic-indic-dist-320M`, rev `ffb7582`, 747 MB safetensors) | AI4Bharat/IndicTrans2 | **MIT** | Base NMT for `hin_Deva→sat_Olck`. Exported via manual `torch.onnx.export` (opset17, not `optimum`), 3-graph `encoder_model.onnx` / `decoder_model.onnx` / `decoder_with_past_model.onnx` + `decoder_shared.onnx.data` (shared sidecar) + `encoder_model.onnx.data`. See `indictrans2-onnx-export/src/01_export_encoder_decoder.py`, `it2_onnx_wrappers.py`. |
| ONNX Runtime Mobile (`onnxruntime-android`, ORT 1.27 for export, 1.17-1.20 AAR on device) | `microsoft/onnxruntime` | MIT | Mobile inference (`OrtEnvironment`, `OrtSession`, `CPUExecutionProvider`, `numThreads=1`). Bundled `libonnxruntime.so` also inside `sherpa-onnx-1.13.0.aar` — no `.so` duplication conflict observed; MT uses `ml/export_venv` ORT 1.27 for export/quant. |
| `indictrans2-onnx-export/` pipeline (`onnx_bundle_optimize.py`, `04_quantize_int8.py`, `02_build_fast_tokenizers.py`, `it2_inference.py`) | `indictrans2-onnx-export` (standalone, MIT) | MIT | Reused as reference: ORT graph fusion 3696→1662 nodes, externalize >100 MB, shared decoder sidecar (~550 MB saved on FP32; indic→indic keeps separate `embed_tokens`/`lm_head`). INT8 `quantize_dynamic` `QInt8 per_channel=True use_external_data_format=True extra_options={'DefaultTensorType':9}`. |
| Fast tokenizers `tokenizer_src.json` (23M, 130526 vocab) + `tokenizer_tgt.json` (23M) + `tokenizer_meta.json` (`src_dict_size:122706 tgt_dict_size:122672 unk_id:3`) | Built from `model.SRC/TGT` via `SpmConverter` + `dict.SRC/TGT.json` ID remap | MIT-derived (tokenizer JSON is transform of MIT dict/SPM) | `added_tokens` `hin_Deva`/`sat_Olck` as `AddedToken(single_word:true)`, `TemplateProcessing` `single:"$A </s>"`, clamp `id < dict_size else 3`. **100% parity** vs slow `IndicTransTokenizer` on 8+ fixtures — validated before quant. |
| ONNX bundle `android/ml/src/main/assets/vachak_models/mt/` (INT8) | Derived from above (357 MB after `finalize_bundle_layout` + checker fix) | MIT-derivative | `encoder_model.onnx` (812K + 115M `.data`), `decoder_model.onnx` (841K) + `decoder_shared.onnx.data` (194M), `decoder_with_past_model.onnx` (707K), `config.json`, `generation_config.json`, `tokenizer_*` — `onnx.checker` PASS (opset17), ORT loadable, 357 MB (`du -sh`). Over 180 MB budget — variance doc `docs/phases/P1-size-variance.md` (options: INT8+zip, Q4F16 block16 acc4, pruned vocab). FP32 oracle 1226 MB. |
| COILD-MT-Corpus HIN-SAT 20,603 pairs + Education_v2 HIN-SAT | IIT Patna / COILD | CC BY 4.0 | **Not** used for training in 01-01 (base distilled model already handles `hin→sat`); reserved for future LoRA fine-tune (never `IN22` test). |
| IN22-Gen/Conv | AI4Bharat | — | **Eval only, never train** — guard per `AGENTS.md`, `docs/PHASES.md`. |

* Santali-only per `docs/PHASES.md` dispatch map; Mundari/Ho dropped.
* No runtime network: HF download is build-time only (`ml/export_venv`, `indictrans2-onnx-export`); APK has zero `INTERNET` permission — audited via `grep` + `network_request_audit`.
* Quantizer checker fix: `quantize_dynamic` left `TensorProto` with both `float_data`+`raw_data` (and `int32_data`+`raw_data`) for `weight_scale`/`zero_point` — cleared duplicate fields, preserved `decoder_shared.onnx.data` external layout, re-verified `onnx.checker` PASS and ORT.

## Phase 1 — On-device MT Hin→Santali (01-02, wiring)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| `com.microsoft.onnxruntime:onnxruntime-android:1.18.0` (ORT Mobile, :ml) | `microsoft/onnxruntime` | MIT | On-device MT inference (`OrtEnvironment`, `OrtSession`, `OnnxTensor`, `SessionOptions` 1 thread). Coexists with `sherpa-onnx-1.13.0.aar`’s `libonnxruntime.so` via `packaging.jniLibs.pickFirsts += "**/libonnxruntime.so"` in both `:ml` and `:app`; no `DuplicateFileException`. `compileOnly` for sherpa in `:ml` avoids AGP 8.9 local-AAR bundling error; APK gets sherpa via `:app` implementation. |
| `IndicProcessorPort` (`android/ml/src/main/java/com/vachak/ml/IndicProcessorPort.kt`) | Port of `AI4Bharat/IndicTransToolkit` `IndicProcessor` (inference=True) | MIT (ported, <200 lines, no runtime import) | Minimal `preprocessBatch`/`postprocessBatch` for Hin→Sat: NFKC, `hin_Deva`→`sat_Olck` lang-tag prefix, digit translate, placeholder handling (URL/email/numeral/`#`/`@`), `trivial_tokenize`/`trivial_detokenize`, `ArrayDeque` placeholder queue with clear between calls. Parity: 20 fixtures vs Python `IndicProcessor`. No `indic_nlp_resources` bundled (rules inlined). |
| `IndicTrans2Adapter` (`android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt`) | `AI4Bharat/IndicTrans2` (`ai4bharat/indictrans2-indic-indic-dist-320M`) + `microsoft/onnxruntime` | MIT | `TranslationEngine` over 3-graph ONNX (`encoder_model.onnx`/`decoder_model.onnx`/`decoder_with_past_model.onnx`) via `SherpaAssets.prepare(context,"mt")` → `filesDir/vachak_models/mt`. Greedy decode: encode clamp `< src_dict_size else unk_id 3`, `pad + attention_mask`, `enc.run`, `dec.run` (decoder_start_id 2 + encOut + mask), `decPast` loop with `past_key_values.*` (8,1,64 + enc KVs), argmax, eos 2 / max 128, clamp `< tgt_dict_size`, `batch_decode`, postprocess, Ol Chiki regex `[\u1C50-\u1C7F]` validator, `Log.d Vachak-MT` at input/tokenize/decode/output/latency, `LatencyTracker` mark, `ReentrantLock` sequential, `supports()` hi/hin/hindi → sat/sat_Olck/olck + mund/mun alias. |
| `EngineProvider.real()` wiring | Vachak | — | `translation = IndicTrans2Adapter(context)` (was `MockTranslationEngine`). `mock()` unchanged. See `android/app/src/main/java/com/vachak/engine/EngineProvider.kt`. |
| COILD-MT-Corpus HIN-SAT (20,603) + Education_v2 HIN-SAT | IIT Patna / COILD | CC BY 4.0 | Used for NMT domain; not fetched at runtime. Attribution retained. |

* 01-02 builds green: `:ml:assembleDebug` + `:app:assembleDebug` with `onnxruntime-android:1.18.0`, minSdk 28, no `INTERNET` permission.
* MT slice 357 MB + tokenizer 48 MB counted in `docs/phases/P1-size-variance.md` budget variance; no network calls.

## Phase 4 — Santali FLN Curriculum (first-party, Santali-only)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| `curriculum/lessons/sat_lessons.json` (**15 FLN lessons, G1:6 G2:4 G3:5, oral/reading/writing/numeracy/EVS — canonical flooded**) | Vachak-authored (SIH26042) | CC BY 4.0 | **AUTHOR-DRAFT — NOT APPROVED** — Aligned to public NIPUN Bharat FLN descriptors. Santali Ol Chiki translations are **PRECOMPUTED, human-reviewed**, stored as `textSatOlChiki` (Ol Chiki U+1C50–U+1C7F). **No on-device MT.** Per `AGENTS.md` hard rule: **Never ship MACHINE_TRANSLATED curriculum content as approved pedagogy.** SME sign-off required. Bug before was documented as 8; fixed to canonical 15 via `_convert_sat_lessons()` handling 15 correctly (no truncation). See `curriculum/data.py: _convert_sat_lessons`, `curriculum/lessons/sat_lessons.json` meta `15 FLN lessons, 8 NIPUN-mapped outcomes — flooded per-subject G1-G3`. |
| `curriculum/outcomes/nipun.json` (8 NIPUN mappings, **reused many-to-one across 15 lessons**) | Vachak-authored | CC BY 4.0 | NIPUN codes locally assigned (G{n}-{domain}-{seq}), e.g. G1-O-COM-01. Descriptors are **author-drafted**, not verbatim GoI copy (framework is GoI public domain). **Outcome reuse documented:** 8 distinct outcomes reused across 15 lessons (avg 1.875 lessons/outcome); `curriculum/data.py` warns (not errors) when `lessons(15) > outcomes(8)` — this is expected. See `_convert_sat_lessons()` `_meta.reuse_documented`. Never ship as APPROVED pedagogy. |
| `curriculum/seed/grade2_math_counting.json` + `schemas/schema.sql` | Vachak-authored (prior phase) | CC BY 4.0 | Earlier FLN seed (G2 counting). Retained but Phase 4 authoritative source is `sat_lessons.json`. |
| `worksheet/templates/*.pdf` (7 PDFs: trace, fill_blank, comprehension, match, oral) | Vachak-authored | CC BY 4.0 | TEMPLATE-BASED, not AI-generated. Generated via deterministic PIL rendering (offline). Covers fill-in/match/trace/comprehension per AGENTS.md. |
| `flashcard/assets/*.png` (46 PNGs) | Vachak-authored | CC BY 4.0 | PREBUILT flashcard image assets, not generated at runtime. Each PNG embeds Hindi + Santali Ol Chiki + image placeholder. Rendered with Noto Sans Ol Chiki (OFL, system font at build time — not bundled). |
| `android/content` Room DB (`lessons`, `outcomes`, `worksheets`, `flashcards`) | Vachak-authored | CC BY 4.0 | SQLite via Room, prepopulated from JSON assets at first launch (no network). Ol Chiki codepoints validated U+1C50–U+1C7F. Budget: curriculum 10–30MB, flashcards 20–50MB — actual ~1.9MB total. |
| Noto Sans Ol Chiki font (runtime bundled) | Google Noto — https://github.com/googlefonts/noto-fonts/blob/main/hinted/ttf/NotoSansOlChiki/NotoSansOlChiki-Regular.ttf | SIL OFL 1.1 | **Now bundled offline** at `android/app/src/main/res/font/noto_sans_ol_chiki.ttf` (15K) for U+1C50–U+1C7F rendering on Android 9 tablets (no tofu). Also used at build-time for PNG/PDF glyphs. |
| Noto Sans Devanagari (Hindi) | Google Noto — https://github.com/googlefonts/noto-fonts/tree/main/hinted/ttf/NotoSansDevanagari | SIL OFL 1.1 | Bundled offline `noto_sans_devanagari*.ttf` (4 weights, 215-221K each) for Hindi U+0900–U+097F. Fallback after Lexend. |
| Lexend variable [wght] | https://github.com/google/fonts/ofl/lexend | SIL OFL 1.1 | Bundled offline `lexend.ttf` (172K variable) for Latin. Replaces `GoogleFont.Provider` network fetch in `ui/theme/Type.kt:11` — now fully offline per AGENTS.md. Composite `VachakFontFamily` = Lexend → Noto Devanagari → Noto Ol Chiki. ~1.1MB total. |
| Ol Chiki script / numerals ᱑–᱑᱐ | Public domain script | Public domain | Standard Unicode Ol Chiki range; numerals ᱑–᱑᱐ used in flashcards/worksheet are public domain. |
| NIPUN Bharat FLN framework (competency descriptors referenced) | Ministry of Education, GoI | Public domain (policy document) | Outcome codes (G1-O-COM-01 etc.) are Vachak-local conventions mapped to FLN competencies. Not a copy of restricted text. |

- Phase 4 is Santali-only; Mundari/Ho dropped per `docs/PHASES.md`. Canonical flooded is **15 lessons / 8 outcomes reused** (documented in `curriculum/data.py: _convert_sat_lessons` + `_meta.reuse_documented`), not 8. Validate warns if `lessons > outcomes` (expected 15>8).
- All Phase 4 assets are offline, deterministic, and pass `grep -c "textSatOlChiki"` / `grep -c "nipunCode"` + `python3 curriculum/data.py` (classic 1/5 OK, flooded 15/8 WARN expected, no truncation) checks.
- No runtime network, no on-device MT for curriculum, no AI-generated worksheets, no generated flashcards — per AGENTS.md hard rules. **AUTHOR-DRAFT not APPROVED** — `sat_lessons.json` meta `content_status: AUTHOR-DRAFT`.
- `datasets/hin_sat` 10,477 (10,245 silver + 116 gold + 116 gold_rev) is **DRAFT; 2.2% verified (221/10,245)** per `verify_report.json`; keep as DRAFT, production is COILD 20,603. `hin_mun` BY-NC-SA-FS quarantined at `datasets/_quarantine/` not bundled.

## Phase 2 — Santali Voice TTS (02-01, Santali-only)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| IndicVoices Santali (19,779 train samples, CC BY 4.0) | AI4Bharat / HF `AI4Bharat/IndicVoices` | **CC BY 4.0** | Primary TTS fine-tune corpus. Curated single-speaker subset **SAT-IV-SP001** (~3,200 utt / 4.52h at 22.05k mono) used for VITS training to avoid multi-speaker collapse on 433-spk Nirantar. Consent via CC BY 4.0 corpus license; see `VOICE_CONSENT.md`. |
| Nirantar Santali (13,503 utt / 161.29h / 433 spk / 8 districts, CC BY 4.0) | HF `adjaysagar/nirantar` | **CC BY 4.0** (verify per-artifact; README states CC BY 4.0) | Diversity/curation reserve. Held as secondary speaker reserve; not in primary single-speaker VITS if multi-speaker degrades MOS. Per `ml/tts/dataset/santali_manifest.json` pooled census. |
| Rasa Santali subset (~850 utt, CC-BY-4.0 clean-license) | AI4Bharat `ai4bharat/Rasa` | **CC-BY-4.0** | Supplementary clean-license subset / methodology ref. Technique provenance for low-resource Santali TTS. |
| Common Voice Santali (~533 clips, CC BY 4.0) | Mozilla Common Voice | **CC BY 4.0** | Supplementary diversity clips. Each clip contributed under CC BY 4.0 / CC0 with Common Voice consent flow. |
| `models/vits-sat.onnx` (110 MB, opset17, 22.05kHz mono, `sample_rate:22050` metadata) | Vachak (Coqui VITS trained from scratch, Colab T4, ~300 epochs/~8.5k steps, single-speaker Ol Chiki) — datasets above CC BY 4.0 | **Vachak-derived, CC BY 4.0 attributions preserved** (see `export (1)/export/report.md`) | Santali VITS via **Coqui TTS VITS** (MPL-2.0 build-time only, not shipped) + **sherpa-onnx** (Apache-2.0 runtime). Single `model.onnx` merged acoustic+vocoder, `onnx.checker` PASS, opset17, inputs `input/input_lengths/scales`. Host verify PASS/PASS rms ~0.18 (bar rms>=0.05); sherpa 1.13.8 cross-check 3/3 PASS. Supersedes the 55-token 41MB shim. See `VOICE_CONSENT.md` + `ml/tts/dataset/santali_manifest.json`. **No Piper in APK** (Piper GPL-3.0 is training-only per `AGENTS.md` hard rule). |
| `models/tokens.txt` (38 tokens: single-char symbols, ids 4-41) + `models/lexicon.txt` (231 entries, Ol Chiki char-split) | Vachak (Ol Chiki char tokenization, no espeak G2P; `export_sprint.py` remap) | **Vachak-authored** | Ol Chiki-native single-char tokens (sherpa character frontend aborts on multi-char specials — remapped). Lexicon is word→char-split phones; **must ship lexicon** (space not in charset; without lexicon word boundaries are dropped). **No `espeak-ng-data` shipped** (`dataDir=""` at runtime). |
| `android/app/src/main/assets/vachak_models/tts/model.onnx` + `tokens.txt` + `lexicon.txt` (copied via `ml/tts/train.py`) + `android/ml/src/main/assets/vachak_models/tts/` (mirror) | Derived from `export (1)/export/vits-sat.onnx` (same bytes) | Same as `models/vits-sat.onnx` | Packaged TTS slice ~110 MB mono 22.05kHz (over 20–80 budget — demo-accepted, pack-split follows if budget bites). Asset extraction via `SherpaAssets.prepare(context,"tts")` recursive copy + `OfflineTts(null, config)` null AssetManager (fs path). Wired 2026-09-13 from `export (1)/export/`. `sherpa-onnx` AAR 1.13.0 is Apache-2.0 (vendored, no Piper). |
| `VOICE_CONSENT.md` (contains `consent` x15, speaker SAT-IV-SP001 anonymized, date 2026-08-29, CC BY 4.0, curator placeholder) | Vachak (per RESEARCH.md template, STRIDE Tampering/Info Disclosure, ASVS L1) | — | Records CC BY 4.0 corpus consent for IndicVoices/Nirantar/Rasa/Common Voice, single-speaker curation note, right-to-removal, no PII. Verified `cat VOICE_CONSENT.md | grep -c consent >=2`. |
| IN22-Gen/Conv | AI4Bharat | — | **Eval only, never train** — guard per `AGENTS.md`, `docs/PHASES.md`, `ml/tts/dataset/santali_manifest.json:eval_guard`. |

* Santali-only per `docs/PHASES.md` dispatch map; Mundari/Ho dropped. All Phase 2 audio is 22.05kHz mono PCM16, Ol Chiki U+1C50–U+1C7F, sherpa-onnx `OfflineTtsVitsModelConfig(model,tokens,lexicon,dataDir="")`, `numThreads=1`, sequential ASR→MT→TTS only. No runtime network; `VOICE_CONSENT.md` + `santali_manifest.json` + `train_config.json` are the provenance triple. `ml/tts/dataset/prepare_santali.py` is the manifest builder (librosa 22.05k, Ol Chiki validator `[\u1C50-\u1C7F]`).
* TTS slice budget: model 110 MB + tokens 248 B + lexicon 6.6K ≈ 110 MB (over 20–80 — demo-accepted bundled APK per 2026-09-13 decision; pack-split follows if budget bites); total APK ~500 MB per `AGENTS.md` budget table; no `espeak-ng-data` overhead.
* Training stack (audit): `coqui-ai/TTS` MPL-2.0 + `torch` BSD-3 + `onnx` MIT + `sherpa-onnx` Apache-2.0 — all build-time or Apache-2.0 runtime; **no GPL code in APK**.



## Phase 10 — Dual adapters (Mundari + Santali, 2026-09-05)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| `android/ml/src/main/assets/modelpacks/mundari_phrasebook/corpus.tsv` (17,826 hi→mun pairs, 3.7 MB) | Copy of `datasets/hin_mun/corpus.tsv` (Karya Hindi–Mundari corpus) | **Karya BY-NC-SA-FS 1.0 — NonCommercial** (see `datasets/hin_mun/LICENSE.txt`, quarantine marker `datasets/_quarantine/hin_mun`) | Deterministic offline LOOKUP TABLE served by `MundariPhrasebookEngine` (exact → normalized → token-overlap retrieval → honest miss). Lookup only — never training data, never presented as model output. Educational/demo use (SIH26042). Replaced automatically by the merged LoRA CT2 model (`modelpacks/stripped_mt_merged`) once built. |
| `OnnxIndicTrans2Adapter` (hi→sat_Olck driver, rebuilt 2026-09-05) | Vachak (new code over proven `vachak_models/mt` ONNX INT8 bundle) | **Vachak-authored, MIT base model** (`ai4bharat/indictrans2-indic-indic-dist-320M`, MIT) | Restores the verified on-device path (BPE 245k + 3-graph greedy + past KV + GOLD curated pre-check from `datasets/hin_sat/corpus.gold_verified.tsv` human-verified tier). No new model weights introduced. |
| TTS shim gate (`SherpaTtsAdapter` + `SherpaOnnxTtsAdapter.isShim`) | Vachak | — | The 55-token placeholder graph hard-aborts the sherpa-onnx native layer (exit 255, uncatchable); the gate refuses native load and returns honest MODEL_NOT_LOADED so text stays source of truth. Lifted 2026-09-13: 38-token sprint VITS passes through (`isShim=false`) for live synthesis. No audio is fabricated. |

## Phase 10 — Mundari LoRA train + merge (2026-09-06)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| `ml/finetune/data/it2_mundari_combined_{train,dev}.tsv` (33,907/3,767) | `datasets/hin_mun/corpus.tsv` (Karya BY-NC-SA-FS, 17.8k) + user-supplied `mundari-train(1).csv` (20k, provenance: contributor file, treat as quarantined like Karya) | **NonCommercial posture** (Karya BY-NC-SA-FS; CSV file of unknown license — same quarantine treatment) | Deduped/shuffled/split 90/10. Educational/demo use (SIH26042). |
| `ml/finetune/it2_mundari_combined/adapter_model.safetensors` (14 MB, LoRA r16, 7 epochs) | Vachak-trained on RTX 3050 4GB from `models/indictrans2_bart` | **Vachak-derived** (base MIT; training data NC as above) | Dev loss 13.4→4.40 every epoch; dev chrF 5.2 (base) → 15.0. Undertrained tail (diminishing returns ep5-7); round 3 needs cleaner long-sentence handling, not just epochs. |
| `modelpacks/stripped_mt_merged/` (`model.bin` 197 MB int8 + split vocabs) | Merged (`merge_and_unload`) + CT2 convert via `ml/translation/scripts/merge_lora_to_ct2.py` | Same as above | Host-validated dev chrF 13.0 (beam 1 + penalties). Bundled in APK assets, extracted to filesDir on first MT use. Replaces phrasebook when present (`isMergedReady`). |

## Phase 10 — Santali LoRA bidi + ONNX bundle refresh (2026-09-06)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| `ml/finetune/data/it2_sat_bidi_big_{train,dev}.tsv` (45k/5k, hi↔sat) | goldverified 453 + corpus.tsv silver 10,477 + classroom_10k 10k + review-filtered 2,031 + contributor `material/` 5,632 (FLORES-style 3.5k tagged `flores200-devtest` upstream + tech 2.1k) | **Mixed**: gold human-verified; silver/template synthetic DRAFT (never shipped as curriculum); contributor files of unknown license (quarantine posture, educational/demo use) | Bidirectional (script-inferred tags), 23 gold-benchmark leaks excluded. FLORES-devtest-tagged rows must NEVER back FLORES score claims — eval is own split + goldverified_dev only. |
| `ml/finetune/it2_sat_combined/adapter_model.safetensors` (14 MB, LoRA r16, bidi) | Vachak-trained RTX 3050 from `models/indictrans2_bart`, canonical toolkit format (tag-free targets) | **Vachak-derived** (base MIT; data as above) | Dev 1.29. Raw-output eval: hi→sat chrF 56.3 ghost-free, sat→hi 23.3. Supersedes `it2_goldnum_lora` for hi→sat. |
| `android/*/assets/vachak_models/mt/*` refreshed bundle (314 MB int8, onnx.checker PASS all 3 graphs) | Merged adapter + `export_onnx_it2.py` + `04_quantize_int8.py` (per_channel) | Same as above | Replaces 357 MB base bundle. GOLD map extended to 49 entries (48 goldverified-exact + 1 labeled base-preserved `मेरा नाम क्या है`). Short-interrogative forgetting on novel shorts is the known residual gap (next data round: short QA pairs). |

## Phase 11 — Santali bidi fine-tune on Lightning T4 (2026-09-08)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| `lightning_bundle/data/{train,dev,test_classroom,test_general}.tsv` (9,423/600/300/300 pairs) | Rebuilt from `/cz/Vachak/material` (32 CSVs, user-supplied verified Hindi-Santali; 15 trash rows removed, zero split leakage audited) | **Unknown license — quarantine posture, educational/demo use (SIH26042)**; contains FLORES-derived rows, so FLORES must never back score claims — eval is own held-out splits only | Bidirectional TSVs (script-inferred direction). Classroom test = short + school-word Hindi rows (student-teacher use). |
| `adapter_sat_bidi/best_adapter` (100 MB, LoRA r64 all-linear, 12 epochs, T4) | Vachak-trained from `ai4bharat/indictrans2-indic-indic-dist-320M` (MIT) | **Vachak-derived** (base MIT; data as above) | Held-out: classroom chrF 40.4 (hi->sat) / 45.8 (sat->hi). MACHINE-TRANSLATED draft, never approved pedagogy. |
| `backend satfinal` (`TRANSLATION_BACKEND=satfinal`) | Vachak-authored wiring over `it2_ct2_baseline` engine | — | Serves merged CT2 INT8 at `modelpacks/sat_bidi_ct2_int8` (pending Studio export). Baseline `stripped_mt` untouched for A/B. |

## Phase 2 — Santali Voice TTS (02-03, native-data training + interim voice, 2026-09-11)

| Artifact | Source | License | Notes |
|----------|--------|---------|-------|
| `raw/santali_male_native_web/` (~5,284 utt / ~10 h, 1 male spk, 48 kHz, Devanagari transcripts) | Web-collected, undocumented | **UNVERIFIED — prototype-only** (no speaker consent on file; see `VOICE_CONSENT.md` §9) | Resampled to 22.05 kHz mono (`ml/tts/dataset/wavs_22050/`, silence-trimmed, ≤12.5 s kept: 5064 utt / 8.26 h, splits 4557/304/203 in `metadata_{train,dev,eval}.csv`). Transliterated Devanagari→Ol Chiki via `ml/tts/dataset/transliterate.py` (16/16 anchors, MT-vocab overlap audited). NOT release-grade until consent filed or retrained on §Phase-2 CC BY 4.0 corpora. |
| `hi_IN-pratham-medium` (Hindi VITS, 61 MB, 22050 Hz) → `modelpacks/piper-hi-base/hi-sat-interim.onnx` | rhasspy/piper-voices | **Repo MIT; voice dataset `pratham` — attribution required** | INTERIM audible Santali voice (weights unmodified; ONNX metadata patched `sample_rate/n_speakers/language=hi/comment=piper/voice=hi` for sherpa-onnx Piper path; `tokens.txt` from voice `phoneme_id_map`). Hindi-accented bridge via `normalize_mt`+`ol_to_dev`; proof `ml/tts/runs/santali_vits/interim_proof.log` + `python ml/tts/audible_check.py`. Dev-machine only (needs espeak-ng-data) — NOT in APK. Reproduce: `python scripts/patch_piper_for_sherpa.py`. |
| Coqui TTS 0.27.5 + torch 2.14 cu130 + transformers 4.53.3 (training env `ml/tts/.venv-tts`, py3.11) | coqui-ai/TTS + pytorch + huggingface | **MPL-2.0 + BSD-3 + Apache-2.0** | Build-time only, never shipped. Native VITS run `tmux satvits` (`ml/tts/finetune_sat.py`, char tokens, batch 4 on RTX 3050 4 GB). |
| espeak-ng `hi` (interim frontend) | espeak-ng | GPL-3.0 | Dev-machine phonemization for the interim voice only; NOT in APK (Android target uses Ol Chiki char tokens, `dataDir=''`). |
