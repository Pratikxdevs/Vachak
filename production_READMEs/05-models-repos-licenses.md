# 05 — Models, GitHub Repos & Licenses

## On-device models (production)

| Stage | Model | Size | Files | Source |
|---|---|---|---|---|
| VAD | Silero VAD ONNX | ~1–2 MB | `silero_vad.onnx` | k2-fsa/sherpa-onnx release |
| ASR | NeMo EncDecCTCModelBPE Hindi (sherpa layout) | ~140 MB | `model.onnx + tokens.txt` | NeMo → ONNX, build-time fetch |
| MT (Santali) | IndicTrans2-dist-320M → ONNX INT8 3-graph | 314–357 MB | `encoder_model.onnx + decoder_model.onnx + decoder_with_past_model.onnx + .data + gold.tsv` | ai4bharat base, team fine-tune + export |
| TTS (Santali) | Coqui VITS | ~110 MB | `model.onnx + tokens.txt + lexicon.txt (231 entries)` | team fine-tune, `ml/tts/finetune_sat.py` |
| Mundari | Deterministic phrasebook | ~3.7 MB | `corpus.tsv` (17,826 rows) | quarantined Karya copy, lookup only |

Dev-fixtures (never prod): `whisper-tiny` ASR, `vits-zh-aishell3` + `espeak-ng-data` TTS,
55-token TTS shim, `Mock*` engines. See `scripts/fetch_android_models.sh`.

## GitHub repos / libraries used

- `k2-fsa/sherpa-onnx 1.13.0` (Apache-2.0) — vendored AAR at
  `android/ml/libs/sherpa-onnx-1.13.0.aar` (`compileOnly` in `:ml`,
  `implementation` in `:app`), `pickFirsts **/libonnxruntime.so`.
- `com.microsoft.onnxruntime:onnxruntime-android 1.24.3` (MIT,
  `android/gradle/libs.versions.toml`) + preload in `MainActivity.kt:38`.
- `AI4Bharat/IndicTrans2` (`indictrans2-indic-indic-dist-320M`, MIT) +
  `IndicTransToolkit` — MT base. Vendored/copied under `IndicTrans2/` in repo root for export.
- `CTranslate2 v4.4.0` — future Mundari CT2 path only (`ctranslate2_jni.cpp`).
- `coqui-ai/TTS 0.27.5` (MPL-2.0, build-time only) — VITS training (`ml/tts/finetune_sat.py`).
- `rhasspy/piper` + `espeak-ng` (GPL-3.0, training/dev-machine only, **never in APK**).
- `openai/whisper` tiny via sherpa builds (MIT, fixture only), `vosk` (Apache-2.0, evaluated),
  `ggerganov/whisper.cpp` (MIT, evaluated) — see `ml/asr/README.md`.
- `pytorch/pytorch` (BSD-3), `onnx/onnx` + `onnxruntime` (MIT), `huggingface/transformers`,
  `tokenizers`, `sentencepiece` (Apache-2.0/MIT) — training/export toolchain.
- Fonts: `Noto Sans Ol Chiki`, `Noto Sans Devanagari`, `Lexend` (OFL), bundled offline.

## Datasets & licenses

- MT prod: `COILD-MT-Corpus 20,603` (CC BY 4.0) + `Education_v2`.
- MT draft stopgap: `datasets/hin_sat` 10,477 pairs — synthetic/MACHINE-TRANSLATED,
  never approved pedagogy without SME sign-off.
- `datasets/hin_mun` (Karya **BY-NC-SA-FS 1.0**, non-commercial) — **quarantined**
  (`datasets/_quarantine/`), never in `.vachakpack`/APK.
- `IN22-Gen/Conv` — eval-only, never train. `material/` FLORES-tagged rows —
  never back score claims (eval: own held-out + `goldverified_dev`).
- Voice: `VOICE_CONSENT.md` — TTS training data **no consent on file, prototype-only**
  until consent filed or retrained on CC BY 4.0 corpora
  (IndicVoices SAT-IV-SP001, Nirantar, Rasa, Common Voice listed as CC BY path).
- Curriculum: `sat_lessons.json` CC BY 4.0 Vachak-authored, AUTHOR-DRAFT.
- Full table: `THIRD_PARTY_NOTICES.md` + `docs/provenance/`. Rule: model license ≠ repo license;
  record repo + model + dataset + voice-consent provenance before merging anything external.
