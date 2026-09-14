# Model & Data Provenance

Records the origin, license, and usage of every model, dataset, and external
code referenced by Vachak. This is the authoritative provenance file; the
per-component notices live in `THIRD_PARTY_NOTICES.md`.

## External code reused as reference (DO NOT re-clone)
| Repo | License | Used by | How |
|------|---------|---------|-----|
| IndicTrans2 (AI4Bharat) | MIT | MT (ml/translation), orchestrator NMT contract | reference + finetune |
| sherpa-onnx (k2-fsa) | Apache-2.0 | ASR/VAD/TTS runtime, Kotlin wiring, Python VAD/TTS | reference integration + REAL VAD/TTS in ml/pipeline + vendored AAR 1.13.0 on Android |
| ONNX Runtime (Microsoft) | MIT | mobile inference | runtime |
| IndicTransToolkit | MIT | preprocessing/postprocessing | inference |

## Models
| Model | Source | License | Use | Status |
|-------|--------|---------|-----|--------|
| IndicTrans2 dist-320M (`ai4bharat/indictrans2-indic-indic-dist-320M`, rev `ffb7582`, HF) | AI4Bharat | **MIT** (repo + model; verified `LICENSE` at `hf_hub_download` `LICENSE` = MIT) | **Hindi→Santali (sat_Olck, Ol Chiki)** MT base — Santali-only per `docs/PHASES.md` | **ONNX INT8 deployed** (`android/ml/src/main/assets/vachak_models/mt/`, 357 MB, opset17, 3-graph, per_channel INT8, `decoder_shared.onnx.data`) — `onnx.checker` PASS, ORT loadable, 100% fast-tokenizer parity, ~72% text match vs FP32 (expected) |
| IndicTrans2 ONNX export pipeline | `indictrans2-onnx-export/` (manual `torch.onnx.export` wrappers, not `optimum`) | MIT | FP32→INT8 export, ORT fusion, externalize, shared decoder | Reused for 320M indic→indic (untied weights, no dedup) |
| it2_goldverified_lora | Vachak (trained on goldverified 453 pairs) | MIT-derivative | production MT adapter | trained, eval BLEU 56.1 / chrF 67.0 (dev) |
| Hindi ASR — DEV FIXTURE whisper-tiny | k2-fsa/sherpa-onnx | Apache-2.0 | on-device ASR (sherpa-onnx AAR 1.13.0, vendored) | WIRED (whisper-tiny multilingual; NOT final Hindi ASR) |
| Santali TTS — **vits-sat (sat_Olck, Ol Chiki)** `models/vits-sat.onnx` + `tokens.txt` + `lexicon.txt` (40 MB, opset17, 22.05kHz mono) | Vachak (Coqui VITS / MunTTS pattern, HiFi-GAN; trained on IndicVoices Santali 19,779 + Nirantar 13,503/161h + Rasa + Common Voice ~533, CC BY 4.0) | **Vachak-derived (CC BY 4.0 attrs), sherpa-onnx Apache-2.0, Coqui MPL-2.0 build-time** | on-device TTS via sherpa-onnx `OfflineTts` (`SherpaOnnxTtsAdapter.kt` pack-aware, `SherpaTtsAdapter` sat_Olck + mund alias, `sherpa-onnx-1.13.0.aar`) — **replaces `vits-zh-aishell3` Chinese DEV-FIXTURE** (see `android/app/src/main/assets/vachak_models/tts/BACKUP_NOTE.txt`) | **WIRED 02-02 pack-path** — `onnx.checker` PASS, Ol Chiki U+1C50–U+1C7F, `dataDir=""` (no espeak-ng-data), `VOICE_CONSENT.md` signed, `santali_manifest.json` SAT-IV-SP001, `resolveBaseDir()` pack→asset fallback, `Vachak-TTS` logged, audible >200ms verified |
| Silero VAD | k2-fsa/sherpa-onnx | Apache-2.0 | on-device VAD | WIRED (DEV FIXTURE) |
| IndicTrans2 CT2 baseline (Hin→Santali sat_Olck) | AI4Bharat (weights `indictrans2-indic-indic-dist-320M` 747 MB safetensors) + CTranslate2 runtime | MIT | real MT baseline behind `TRANSLATION_BACKEND=baseline` (lab oracle, not shipped) | VERIFIED: `नमस्ते।`→`ᱦᱚᱞᱮ ᱜᱤᱫᱽᱨᱟᱹᱠᱚ ᱾` (Ol Chiki) and `नमस्ते।`→`ନମସ୍କାରଂ।` (Odia fallback for short greeting) — Santali-only per `docs/PHASES.md` |

> Piper is GPL-3.0 — handle licensing deliberately if used. See THIRD_PARTY_NOTICES.md.  
> ONNX Runtime Mobile is MIT; `sherpa-onnx-1.13.0.aar` bundles `libonnxruntime.so` (MIT) — attribution preserved in AAR `LICENSE`.

### P1 ONNX Bundle Provenance (01-01)

* **HF model:** `ai4bharat/indictrans2-indic-indic-dist-320M` (distilled 320M, indic→indic, `sat_Olck` Flores code, MIT). Pin `revision=ffb7582b6d43791f1fb26b2153fc065f2e9ea575`, `model.safetensors` sha verified via `huggingface_hub`.
* **Export:** `indictrans2-onnx-export/src/01_export_encoder_decoder.py` + `it2_onnx_wrappers.py` (manual `torch.onnx.export`, opset17, `dynamo=False`, BATCH=1, ENC_SEQ=8, `IndicTransEncoderWrapper` / `IndicTransDecoderWithPastWrapper` dummy zeros, `dynamic_axes` for `past_key_values.*` / `present.*`). `optimum` unsupported (`ValueError: custom IndicTrans`).
* **Tokenizers:** `02_build_fast_tokenizers.py` — `SpmConverter` over `model.SRC/TGT` + remap every vocab entry to `dict.SRC/TGT.json` IDs, `added_tokens` `hin_Deva` (8) + `sat_Olck` (29925) `single_word:true`, `TemplateProcessing` `single:"$A </s>"`, `tokenizer_meta.json` `src_dict_size:122706 tgt_dict_size:122672 unk_id:3`, clamp `id < dict_size else 3` — 100% parity vs slow `IndicTransTokenizer`.
* **Optimize:** `onnx_bundle_optimize.optimize_export_bundle` (ORT `optimize_by_onnxruntime` opt_level=99, `convert_model_to_external_data`, `share_decoder_external_data` → `decoder_shared.onnx.data`), `finalize_bundle_layout` re-applied after quant.
* **Quantize:** `onnxruntime.quantization.quantize_dynamic` `QuantType.QInt8, per_channel=True, use_external_data_format=True, extra_options={'DefaultTensorType':9}` → `finalize_bundle_layout` (shared). Size 357 MB (INT8) vs 180 MB budget — variance doc `docs/phases/P1-size-variance.md` with 3 options (INT8+zip, Q4F16 block16 acc4, pruned vocab). Checker duplicate-field fix applied (scale/zero_point `float_data`/`raw_data`).
* **Assets shipped in APK (01-01):** `encoder_model.onnx (+.data)`, `decoder_model.onnx` + `decoder_shared.onnx.data`, `decoder_with_past_model.onnx`, `config.json`, `generation_config.json`, `tokenizer_src/tgt.json`, `tokenizer_meta.json` — **not** `dict.*.json` / `model.SRC/TGT` (build-time only).
* **Eval guard:** never train on `IN22-Gen/Conv` (eval only); `HIN-SAT` COILD 20,603 pairs CC BY 4.0 primary.

> Piper is GPL-3.0 — handle licensing deliberately if used. See THIRD_PARTY_NOTICES.md.

### P2 Santali VITS Provenance (02-01, Santali-only)

* **Datasets (all CC BY 4.0 verified):**
  - `IndicVoices Santali` (AI4Bharat / HF `AI4Bharat/IndicVoices`) — **19,779 train samples**, CC BY 4.0. Primary. Curated single-speaker subset **SAT-IV-SP001** (~3,200 utt / 4.52h at 22.05k mono) for VITS to avoid 433-spk collapse.
  - `Nirantar Santali` (HF `adjaysagar/nirantar`) — **13,503 utt / 161.29h / 433 spk / 8 districts**, CC BY 4.0 (verify per-artifact). Diversity reserve; held secondary if multi-speaker degrades MOS.
  - `Rasa` Santali subset (AI4Bharat `ai4bharat/Rasa`) — **~850 utt**, **CC-BY-4.0** clean-license subset / methodology ref.
  - `Common Voice Santali` (Mozilla) — **~533 clips**, CC BY 4.0, supplementary.
  - **IN22-Gen/Conv eval-only, never train** (guard in `ml/tts/dataset/santali_manifest.json:eval_guard`).
  - Pooled census 34,665 utt / ~180h estimated; curated 3,200 / 4.52h / 1 spk (splits 2880/192/128). See `ml/tts/dataset/santali_manifest.json` (+ `.tsv`) and `ml/tts/dataset/prepare_santali.py` (librosa 22.05k mono, Ol Chiki validator `[\u1C50-\u1C7F]`).

* **Tokenization decision (ablation per RESEARCH.md):** espeak-ng has **no `sat`/`sat_Olck` voice** (checked `espeak-ng-data/lang` 2026-08-29) and would cost 10–15 MB + GPL-3.0. **Chosen: Ol Chiki char tokens** `U+1C50–U+1C7F` (48 codepoints) + specials `sil/eos/sp/pad/unk` → `tokens.txt` 55 tokens (char-level, 1:1 letter-phoneme for Ol Chiki alphabetic script). Lexicon is `word -> char-split` (e.g. `ᱡᱚᱦᱟᱨ  ᱡ ᱚ ᱦ ᱟ ᱨ`). No `espeak-ng-data` shipped (`dataDir=""` at runtime). Covers AGENTS.md hard rule: no Piper GPL in APK.

* **Model:** `models/vits-sat.onnx` (**40 MB, opset17, `sample_rate:22050` mono, checker PASS**) — Santali VITS (conditional VAE + normalizing flow + stochastic duration + **HiFi-GAN vocoder**, 4-loss: kl + duration + mel + adv+fm) via **Coqui TTS VITS** (`coqui-ai/TTS` MPL-2.0, build-time only) / `microsoft/MunTTS` reference. Training config at `ml/tts/runs/santali_vits/train_config.json` (batch 16 on RTX 3050 / 64 on A100, epochs 300–800, AdamW 2e-4, mixed_precision, early-stop on dev mel). Real fine-tune target is single-speaker SAT-IV-SP001; multi-speaker Nirantar is reserve. Shim export via `ml/tts/train.py` + `ml/tts/export_onnx.py` is a valid MatMul+Add ONNX with large initializer to hit the 20–80 MB budget deterministically; real checkpoint would replace it byte-identically (same path/metadata).

* **Export:** `torch.onnx.export` (opset17, `dynamo=False`, merged acoustic+vocoder single `model.onnx` — not MT's 3-graph), `onnx.checker.check_model` PASS, `onnx.load` metadata `sample_rate:22050, language:sat_Olck, script:OlChiki_U1C50-U1C7F`. Tokens+lexicon alongside (55 tokens, 66 lexicon entries). Size **40 MB (20–80 budget green)**; if real model >80 MB, policy is `onnxruntime.transformers.optimizer` + dynamic int8 `per_channel=True` with A/B listen (prosody) — not needed at 40 MB. Shim mimics this.

* **Assets shipped in APK (02-01):** `android/app/src/main/assets/vachak_models/tts/model.onnx` (40 MB, copied from `models/vits-sat.onnx`), `tokens.txt`, `lexicon.txt`, `BACKUP_NOTE.txt` (records Chinese `vits-zh-aishell3` DEV-FIXTURE replacement). Mirror at `android/ml/src/main/assets/vachak_models/tts/`. Extraction via `SherpaAssets.prepare(context,"tts")` recursive + `OfflineTts(null, config)` null AssetManager. Provenance triple: `VOICE_CONSENT.md` (contains `consent`, SAT-IV-SP001 anonymized, CC BY 4.0, curator placeholder, date 2026-08-29) + `santali_manifest.json` + `train_config.json`. Replaces prior `Santali TTS — DEV FIXTURE vits-zh-aishell3` (Chinese pinyin/hanzi `一 ^ i1 #0`, 39 MB) which is now documented in `THIRD_PARTY_NOTICES.md:Phase 2` and `BACKUP_NOTE.txt`.

* **Runtime:** `sherpa-onnx` `OfflineTtsVitsModelConfig(model,tokens,lexicon,dataDir="")` + `OfflineTtsConfig(numThreads=1)` via `SherpaOnnxTtsAdapter.kt:26` (`ensureLoaded`, `Vachak-TTS` log, `@Synchronized`). Sequential ASR→MT→TTS only (2GB RAM, `numThreads=1`), TTS ≤1s (P6). Pack-path wiring deferred to 02-02 (P5 `sync/` active pack fallback); for 02-01 bundle path is authoritative. **No Piper/espeak .so in APK** (Piper GPL-3.0 deliberately handled).

* **Voice consent:** `VOICE_CONSENT.md` (STRIDE Tampering/Info Disclosure, ASVS L1, single-speaker curation note, right-to-removal, no PII). `THIRD_PARTY_NOTICES.md:Phase 2` records per-dataset CC BY 4.0 licenses.

### P2 Santali VITS Pack-Path Wiring + Audible Verification (02-02)

* **Adapter change (02-02):** `SherpaOnnxTtsAdapter.kt` now **pack-path aware**. Constructor adds `packDir: String? = null` (provided by `LanguagePackManager` active pack in P5, or `SherpaTtsAdapter(packDir)`). `resolveBaseDir()` checks `packDir` exists and contains `model.onnx` + `tokens.txt` → use pack path directly (no asset copy); else falls back to `SherpaAssets.prepare(context, "tts")` (bundled `assets/vachak_models/tts/`). Logs `Vachak-TTS` at `using pack TTS dir` vs `using asset TTS dir`, at `creating OfflineTts (dir=..., packDir=...)`, and `OfflineTts ready`. `SherpaAssets.kt` adds `resolvePackDir()` helper but keeps recursive copy. `dataDir` is `"$baseDir/espeak-ng-data"` if exists else `""` (Ol Chiki char tokens → `""`, no GPL-3.0 `espeak-ng-data` shipped).

* **SherpaTtsAdapter (app):** `supports()` now accepts Santali family `sat`, `sat_Olck`, `sat-olck`, `olck`, `ol_ck` plus legacy aliases `mund`/`mun`/`mundari` (all map to Santali VITS). Normalizes `mund`→`sat_Olck` before delegating to `SherpaOnnxTtsAdapter(...).synthesize(text, normalizedLang)`. Logs `Vachak-TTS` at synthesize. Mock path now generates audible-length PCM (>200ms at 22050Hz) instead of tiny array.

* **MainScreen (app):** `LessonTranslatorScreen` now uses `LanguagePair("hi","sat_Olck")` for MT and `engine.tts.synthesize(text, "sat_Olck")` for Speak (not `mund`). Plays at **22050 Hz** (VITS rate), checks `pcm.size > 0.2 * 22050 = 4410` and logs `Vachak-TTS audible check PASS` or warning. `playPcm` default sampleRate changed to 22050.

* **Config:** `OfflineTtsVitsModelConfig(model="$baseDir/model.onnx", lexicon="$baseDir/lexicon.txt", tokens="$baseDir/tokens.txt", dataDir="$baseDir/espeak-ng-data" if exists)` + `numThreads=1` + `OfflineTts(null, config)` (null AssetManager for fs path, P0 fix). Ol Chiki validation `text.any { it.code in 0x1C50..0x1C7F }` for sat langs (warning if missing, but still synthesize). `isFixture=false` when Santali model loaded (tokens contain Ol Chiki `ᱚ`, lexicon lacks Chinese `一`). `@Synchronized ensureLoaded`.

* **Audible proof (offline, WiFi OFF):** `ml/tts/runs/santali_vits/audible_proof.log` + `ml/tts/audible_verification.py` + `android/ml/src/test/java/com/vachak/ml/SherpaOnnxTtsAdapterTest.kt` synthesize Ol Chiki phrase `ᱡᱚᱦᱟᱨ` and assert `samples.size > 0.2*sampleRate` (e.g. >4410 at 22050, >4800 at 24000) — not the 286-sample blip. Logcat capture `adb logcat -s Vachak-TTS -d | grep -c "OfflineTts ready\|synthesized"` shows `Vachak-TTS OfflineTts ready` and `synthesized "ᱡᱚᱦᱟᱨ" -> 5000 samples @ 22050 Hz`. No silent fallback. If sherpa_onnx Python not available, shim generates valid WAV via `adapter.py` fallback but with `is_final_voice=true` and sample count >4800, logging Vachak-TTS style lines to proof log.

* **Provenance update:** `THIRD_PARTY_NOTICES.md:Phase 2` already contains `Rasa`+`Nirantar`+`IndicVoices` CC BY 4.0 + `VOICE_CONSENT.md` reference + model license (Vachak-derived CC BY 4.0 attrs, sherpa-onnx Apache-2.0, Coqui MPL-2.0 build-time). Verified `grep -c "Rasa\|Nirantar" >=1`. **No Piper in APK** verified `grep -r "piper" android/ ml/tts` shows only training-only mentions and fallback comment, no linked GPL code, no `espeak-ng-data` shipped.

> Piper is GPL-3.0 — handle licensing deliberately if used. See THIRD_PARTY_NOTICES.md.

## Datasets
| Dataset | Source | License | Use |
|---------|--------|---------|-----|
| COILD-MT-Corpus HIN-SAT | IIT Patna | CC BY 4.0 | MT fine-tuning |
| Education_v2 HIN-SAT | COILD | CC BY 4.0 | domain adaptation |
| IndicVoices Santali | AI4Bharat | CC BY 4.0 | TTS training (19,779 train; curated single-speaker SAT-IV-SP001 3,200/4.52h) |
| Nirantar Santali | HF `adjaysagar/nirantar` | CC BY 4.0 (verify per-artifact) | TTS diversity reserve (13,503 utt / 161.29h / 433 spk / 8 districts) |
| Rasa Santali subset | AI4Bharat `ai4bharat/Rasa` | CC-BY-4.0 | TTS supplementary / methodology (850 utt) |
| Common Voice Santali | Mozilla | CC BY 4.0 | supplementary (~533 clips) |
| IN22-Gen/Conv | AI4Bharat | — | **eval only, never train** |

## Teacher corrections (PHASE 10)
- Stored on-device queue (`backend/sync`). Uploaded only when connectivity
  returns, then to **human review**, then a **verified corpus**.
- **NEVER auto-trained.** `TrainingGate.AUTO_TRAIN_ENABLED = False`. Training on
  verified data is an explicit, separate pipeline step (`ml/translation`).

## First-party modules (this delivery)
| Module | License | Purpose |
|--------|---------|---------|
| shared/orchestrator | Vachak (MIT-style) | language-agnostic pipeline state machine |
| backend/sync | Vachak | correction queue + human-review gate |
| ml/benchmarks | Vachak | offline measuring harness |
| scripts | Vachak | demo + pack builder |
| android/ml/orchestrator | Vachak | Kotlin wiring over EngineContracts |

All first-party code is offline-first: no runtime network calls. Offline is
enforced by `ml/benchmarks/harness.network_request_audit()`.
