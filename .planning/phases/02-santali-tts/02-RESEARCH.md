# Phase 2: Santali Voice (TTS) — Research

**Researched:** 2026-08-29
**Domain:** Santali VITS TTS via sherpa-onnx (OfflineTts), on-device Android, 20–80MB, Ol Chiki text → audio
**Confidence:** MEDIUM — runtime wiring is HIGH (sherpa-onnx AAR proven); training/data prep is MEDIUM (no Santali fine-tune yet produced in repo); size/latency estimates are HIGH for VITS class, MEDIUM for Santali-specific intelligibility

<user_constraints>
## User Constraints (from CONTEXT.md)

**CRITICAL:** No `.planning/phases/02-mundari-tts/02-CONTEXT.md` exists yet (Phase 2 not yet discussed). Per template, all decisions below are at Claude's discretion — but the following are **locked by upstream docs** and MUST be honored by the planner. They are synthesized verbatim from `.planning/ROADMAP.md:Phase 2`, `.planning/REQUIREMENTS.md:TTS-01, TTS-02`, `.planning/PROJECT.md`, `AGENTS.md`, and `docs/PHASES.md:Phase 2`.

### Locked Decisions (from ROADMAP + REQUIREMENTS + AGENTS.md + PHASES.md)
- **Language:** Santali (Ol Chiki) only. Mundari/Ho explicitly dropped (`.planning/PROJECT.md:5`). Do NOT use Mundari data; MunTTS is reference architecture only (`.planning/REQUIREMENTS.md:49`, `docs/PHASES.md:60`).
- **TTS Architecture:** VITS via **sherpa-onnx** (Apache-2.0) `OfflineTts` → `OfflineTtsVitsModelConfig`. Piper is **GPL-3.0 and MUST be excluded from the APK** (training-only / reference). `THIRD_PARTY_NOTICES.md:15` already lists Piper as GPL-3.0 deliberate handling.
- **Budget:** TTS slice **20–80 MB** (`AGENTS.md:Model budget`, `.planning/REQUIREMENTS.md:TTS-01`). Total app ~500 MB; sequential ASR→MT→TTS only (never parallel, RAM limit 2GB).
- **Datasets (licensed):** IndicVoices Santali **19,779** train samples (CC BY 4.0) + Nirantar Santali **13,503 utt / 161.29h / 433 spk / 8 districts** (CC BY 4.0 — verify HF `adjaysagar/nirantar`) + Rasa (AI4Bharat, CC-BY-4.0 methodology) + Common Voice Santali **~533** clips (CC BY 4.0). Never train on IN22-Gen/Conv; never ship MACHINE_TRANSLATED as approved pedagogy is separate but applies to any synthetic text used inTTS evaluation.
- **Script:** Must handle **Ol Chiki** (Unicode U+1C50–U+1C7F) end-to-end: translation output → TTS input validation → phonemization → audio. Current DEV-FIXTURE `vits-zh-aishell3` is Chinese (pinyin + hanzi lexicon at `android/app/src/main/assets/vachak_models/tts/lexicon.txt:1` shows `一 ^ i1 #0`) and must be replaced.
- **Success criteria (ROADMAP Phase 2):** (1) "Speak" plays audible Santali for a Santali phrase (not 12ms blip / not silence), (2) TTS slice 20–80MB, loads from **pack path** (P5 `sync/` installer owns the path — P2 must not hardcode only `assets/`), offline, no Piper in APK, (3) Voice consent + CC-BY-4.0 dataset licenses recorded in `THIRD_PARTY_NOTICES.md` + `docs/MODEL_AND_DATA_PROVENANCE.md`.
- **Plans:** `02-01` Fine-tune Santali VITS (IndicVoices+Nirantar+Rasa) → export ONNX/tokens/lexicon; `02-02` SherpaOnnxTtsAdapter pack-path wiring + provenance + audible verification.
- **Out-of-scope for P2:** Pack builder/installer itself (P5), curriculum/worksheets/flashcards (P4), MT/ASR training (P1/P3), Hindi ASR choices, IN22 eval, and any network permission (`AndroidManifest.xml:7` deliberately has no `INTERNET`).

### Claude's Discretion
- VITS speaker strategy: single-speaker Santali vs multi-speaker fine-tune (Nirantar has 433 speakers — recommend single-speaker curated subset + optional multi-speaker if feasible).
- Exact training stack: Coqui TTS VITS (`coqui-ai/TTS` MPL-2.0) vs `microsoft/MunTTS` reference scripts vs `sherpa-onnx` native `vits` training; which to fork as template.
- Sample rate choice: 22050 Hz (existing `ml/tts/pipeline.py:TTSConfig.sample_rate=22050` and `EspeakFallbackTtsAdapter:22050`) vs 16000 Hz (common sherpa-onnx VITS) vs 44100 Hz studio — recommend 22050.
- Lexicon/tokenizer design: espeak-ng phonemization vs characters/Ol Chiki-native tokens vs language-specific G2P; whether `espeak-ng-data` ships in final pack (cost ~10-15 MB) or is stripped for Ol Chiki-only.
- Quantization: whether to quantize VITS ONNX (dynamic int8) or ship fp32 and stay under 80MB by other means; how to verify audible quality on 2GB device.
- Fallback labeling: exact `isFixture`/`warning` strings for `SynthAudio` when final voice not yet trained.

### Deferred Ideas (OUT OF SCOPE — do NOT research or plan)
- Mundari/Ho language targets, Mundari data use, `lang="mund"` paths except as backwards-compat alias — do not produce Mundari checkpoints.
- Piper in APK or any GPL-3.0 code linked into `android/app` or `android/ml`.
- Online TTS / cloud voices, parallel ASR+TTS, AI-generated worksheets/flashcards.
- Automatic `IN22` training; curriculum precomputed translations.
</user_constraints>

<architectural_responsibility_map>
## Architectural Responsibility Map

Offline single-APK Android app (Kotlin/Compose/Room) with Python training pipeline. All P2 inference is on-device via sherpa-onnx.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Santali speech data curation & cleaning (16k/22k WAV, manifests, splits) | Python `ml/tts/` pipeline | datasets/ + `ml/tts/data/fixtures/` | Must be reproducible, auditable, CC BY 4.0 verified; no network at runtime |
| VITS training / fine-tuning (spectral + duration + adversarial loss) | Python `ml/tts/` (Coqui/MunTTS ref) + GPU runner | `ml/tts/runs/` checkpoints | Training never ships to device; emulator cannot train; requires GPU hours |
| Export → ONNX + tokens + lexicon + (optional) espeak-ng-data | Python `ml/tts/export_onnx.py` → `sherpa-onnx` convert | `packages/` pack builder (P5) | ONNX is the APK artifact; must validate with `onnx.checker` and audible sherpa test |
| On-device TTS runtime (model load, `OfflineTts.generate`, AudioTrack) | Android `:ml` `SherpaOnnxTtsAdapter.kt:18` | Android `:app` `MainScreen.kt:LessonTranslatorScreen` Speak button | sherpa-onnx AAR 1.13.0 already vendored at `android/ml/libs/sherpa-onnx-1.13.0.aar:54M`; proven via `null` AssetManager + `SherpaAssets:22` recursive copy |
| Model asset extraction & caching (pack vs bundled assets) | Android `:ml` `SherpaAssets.kt:22` (`prepare(context, subdir)`) | `android/sync/` pack installer (P5) + `EngineProvider.real()` | P2 must read **active pack path** (P5), falling back to `assets/vachak_models/tts` only for dev; same pattern as `IndicConformerAsrAdapter:67` |
| Ol Chiki validation & phonemization decision | Android `:ml` (input validation) + Python (lexicon build) | `ml/translation` Ol Chiki codepoint logic (`convert_ct2.py: 0x1C50–0x1C7F`) | Script correctness is acceptance gate; BLOQUE Ol Chiki validation must run before `generate()` |
| Voice consent + license provenance | `THIRD_PARTY_NOTICES.md` + `docs/MODEL_AND_DATA_PROVENANCE.md` | `ml/tts` dataset manifests (`manifest.json` pattern from `datasets/hin_sat/manifest.json:1251`) | Hard rule before merge; Nirantar CC BY 4.0 must be verified per-artifact |
| Latency / budget / offline verification | Android `LatencyTracker.kt:2155` + `benchmarks/` template `docs/benchmarks/BENCHMARK_REPORT.md` | `ml/tts/evaluate.py:61` (MOS/intelligibility/latency/size/WAV-valid) | P2 gate is **audible** + ≤1s + 20–80MB + offline + `adb logcat -s Vachak-TTS` |

Single-tier inference at runtime: all P2 capabilities reside in **Android device (offline)** except training/export which are build-time Python.

Sequential enforcement (non-negotiable): `OfflineTtsConfig(model=OfflineTtsModelConfig(vits=VitsModelConfig(...), numThreads=1))` like `SherpaOnnxTtsAdapter.kt:35`; caller must run TTS on `Dispatchers.IO/Default` and never load ASR+MT+TTS concurrently (peak ~800 MB per P1 research; TTS alone ~150–250 MB resident).
</architectural_responsibility_map>

<research_summary>
## Summary

P2 replaces the **Chinese `vits-zh-aishell3` DEV-FIXTURE voice** (`android/app/src/main/assets/vachak_models/tts/model.onnx:39M`, `tokens.txt` pinyin `sil eos sp #0`, `lexicon.txt: 一 ^ i1 #0`, `espeak-ng-data/:632K` full multilingual) with a **fine-tuned Santali VITS** that synthesizes **audible Ol Chiki Santali** via **sherpa-onnx `OfflineTts`** (Apache-2.0, vendored AAR 1.13.0). The wiring is already proven: `SherpaOnnxTtsAdapter.kt:26–40` correctly implements `TtsAdapter:11` behind `ensureLoaded()` → `SherpaAssets.prepare(context,"tts")` → `OfflineTtsVitsModelConfig(model,tokens,lexicon,dataDir)` → `OfflineTts(null, config)` (null AssetManager because assets are extracted to `filesDir`), and `synthesize(text, lang):43` → `generate(text).samples`.

The standard approach for low-resource Indic TTS on sherpa-onnx is **single-speaker VITS** (conditional VAE + normalizing flow + HiFi-GAN vocoder) trained with **Coqui TTS VITS** (`coqui-ai/TTS` MPL-2.0) or the `microsoft/MunTTS` reference, on **22.05 kHz mono PCM16** utterances, exporting a **single `model.onnx`** (sometimes split into `acoustic + vocoder` but sherpa expects one merged VITS ONNX) plus `tokens.txt` (phoneme/symbol inventory) and `lexicon.txt` (word → phoneme map) and optionally `espeak-ng-data` (only if espeak phonemization is used). Santali has ~33k utterances available (19,779 IndicVoices + 13,503 Nirantar + ~533 Common Voice + Rasa methodology), well above the ~3–6 hours typically sufficient for a fine-tuned single-speaker VITS. The 20–80MB budget is natural for VITS: a fresh VITS at 22k is ~35–55 MB fp32; dynamic int8 or `onnxruntime` graph optimization brings it to ~20–40 MB while staying audible, leaving headroom for `tokens.txt`+`lexicon.txt`.

Key risks: (1) Nirantar's CC BY 4.0 must be verified per-artifact before bundling; (2) Ol Chiki's script range U+1C50–U+1C7F is outside the current Chinese phoneme set — lexicon/tokens must be regenerated for Santali, and espeak-ng's Santali support (checked via `espeak-ng-data/lang` — no `sat` directory currently) is absent, so char/Ol Chiki tokenization is preferred over espeak G2P; (3) `ml/tts/` is still scaffolding — every function returns `TODO`/`DATA ACCESS PENDING` (`pipeline.py:40`, `export_onnx.py:28`) and the manifest is synthetic (`data/fixtures/dev_manifest.tsv: Hindi "परीक्षण।"` labeled as mundari placeholder, NOT Santali).

**Primary recommendation:** Curate a single-speaker Santali subset from IndicVoices (highest consent/quality) + clean Nirantar subset, resample to 22050 Hz mono, train single-speaker VITS via Coqui TTS (fine-tune from `tts_models/multilingual/multi-dataset/your_tts` or `tts_models/en/ljspeech/vits` as warm start if Santali-from-scratch underperforms), export one `model.onnx` + `tokens.txt` + `lexicon.txt` with `onnx.checker`, validate audible Santali via sherpa-onnx Python + Kotlin `generate()`, wire `SherpaOnnxTtsAdapter` to read the **active pack path** (P5) with fallback to `assets/vachak_models/tts/`, gate on 20–80MB + ≤1s + Ol Chiki validation + offline + voice consent provenance — do NOT include Piper or espeak runtime in the APK.
</research_summary>

<standard_stack>
## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `k2-fsa/sherpa-onnx` (AAR + Python) | 1.13.0 (vendored `android/ml/libs/sherpa-onnx-1.13.0.aar:56484057`) | On-device TTS runtime `OfflineTts`/`OfflineTtsVitsModelConfig` + Python `sherpa_onnx.OfflineTts` | Apache-2.0, mandated by `AGENTS.md: TTS via sherpa-onnx`, already WIRED in `SherpaOnnxTtsAdapter.kt:38` + `MODEL_AND_DATA_PROVENANCE.md:11`; only runtime that satisfies arm64-v8a + offline + 2GB constraint |
| `coqui-ai/TTS` | 0.22+ (MPL-2.0) | VITS training / fine-tuning (trainer, dataset formatter, VITS config, HiFi-GAN vocoder) | MPL-2.0 (APK-safe if only used build-time; not linked), most documented fine-tune path for Indic VITS, referenced in `docs/PHASES.md:57`; `microsoft/MunTTS` topology matches Coqui VITS |
| `microsoft/MunTTS` | research ref (verify per-artifact) | VITS/XTTS training script pattern for Mundari-like low-resource (architecture reference only) | `docs/PHASES.md:61` explicitly: reference only, **do not use Mundari data**; topology (VITS: text encoder + flow + duration + vocoder) is directly portable to Santali |
| `onnx` + `onnxruntime` (Python) | 1.16.0+ / 1.17+ (align with `indictrans2-onnx-export` 1.22/1.27) | Export validation (`onnx.checker.check_model`) + quantization (`quantize_dynamic`) if needed | Already in export toolchain; `ml/tts/export_onnx.py:36` prescribes `onnx.checker` |
| `espeak-ng` / `espeak-ng-data` | system binary / bundled `espeak-ng-data/:632K` | G2P phonemization dictionary source (only if espeak path chosen) | Currently bundled for the Chinese fixture (`lexicon.txt` hanzi/pinyin); for Santali, espeak has **no `sat` voice** (`espeak-ng-data/lang` listing shows no `sat`/`sat_Olck` entry — checked 2026-08-29) so Ol Chiki char tokens are preferred; if bundled, license is GPL-3.0 — handle like Piper |
| `indicVoices Santali` | 19,779 train (HF `AI4Bharat/IndicVoices`) | Primary TTS fine-tune (curated, CC BY 4.0) | Best consent/provenance among Santali corpora per `REQUIREMENTS.md:45` |
| `Nirantar Santali` | 13,503 utt / 161.29h / 433 spk / 8 districts (HF `adjaysagar/nirantar`) | Diversity + speaker coverage (single-speaker subset for P2) | Largest Santali hour count; must verify CC BY 4.0 per artifact before training |
| `Rasa (AI4Bharat)` | Santali subset (HF `ai4bharat/Rasa`) | TTS methodology / clean-license supplement | CC-BY-4.0 per `REQUIREMENTS.md:46`; technique reference |
| `Common Voice Santali` | ~533 clips (Mozilla) | Supplementary eval/diversity | CC BY 4.0, small but native-speaker validated |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `sherpa-onnx` Python `OfflineTts` | 1.13.0 (match AAR) | Python-side audible validation of exported `model.onnx` | Always after export — mirrors `it2_ct2_baseline.py` pattern for MT, but for TTS: `python -c "import sherpa_onnx; tts=...; tts.generate('ᱡᱚᱦᱟᱨ')"` parity |
| `librosa` / `soundfile` / `sox` | 0.10+ / 0.12+ | Resample to 22050, mono, trim silence, normalize loudness, manifest validation | Every data-prep run; 44.1kHz studio recordings must be downsampled to 22.05k for VITS |
| `torch` | 2.0+ / 2.12 (`indictrans2-onnx-export` pin) | Training backbone (Coqui VITS is PyTorch) | Training only |
| `torchaudio` | match `torch` | Mel spectrogram, STFT for VITS loss | Training only |
| `phonemizer` (`espeak-ng` backend) | 3.2+ | Alternative G2P if espeak path kept | Only if espeak phonemes retained — not recommended for Ol Chiki |
| `gradio` / `tensorboard` | — | Training monitoring | Optional, dev-only |
| `audiomentations` | 0.34+ | Light augmentation (only if data < 3h after curation) | Only if curated single-speaker hours fall below threshold |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| VITS (single-stage E2E) | **XTTS-v2** (`coqui XTTS`, multilingual zero-shot) | XTTS is larger (~400MB+), needs speaker embedding, slower first-token, mismatched to 20–80MB budget and ≤1s latency; excellent for cloning but overkill for one Santali teacher voice; VITS is the `sherpa-onnx` first-class citizen |
| VITS | **Piper** (`OHF-Voice/piper1-gpl`) | Piper is GPL-3.0 — `AGENTS.md` hard rule says handle deliberately and `REQUIREMENTS.md:TTS-02` says **excluded from APK**; great training quality but licensing forces careful separation (training-only container, not linked); sherpa-onnx already supports Piper checkpoints but Vachak must ship VITS to stay Apache-2.0/MIT |
| VITS | **Matcha-TTS** / **StyleTTS2** | Matcha is sherpa-supported and slightly better MOS, but Coqui VITS fine-tune recipes are more battle-tested for Indic low-resource; StyleTTS2 heavier, not in sherpa first-class VITS path; stick to VITS for P2, revisit Matcha in polish if time |
| Coqui trainer | **sherpa-onnx native trainer** (`sherpa-onnx/vits` scripts) | sherpa scripts are leaner and produce sherpa-native layout directly, but Coqui has richer low-resource fine-tune docs and augmentation; either is acceptable if it emits valid `model.onnx`+`tokens.txt`+`lexicon.txt` that `OfflineTts` loads |
| Char/Ol Chiki tokens | **espeak-ng phonemes** | espeak has no `sat` G2P (`espeak-ng-data/lang` check negative); Ol Chiki is alphabetic (30 letters) so char-level tokens are natural, smaller, and avoid GPL `espeak-ng-data` bloat (~15 MB if full data shipped); char tokens recommended |
| Dynamic int8 quant | **No quant (fp32)** | VITS fp32 at 22k is already 35–55 MB so it fits 20–80MB without quant; quant saves ~30–50% but may degrade Santali prosody — validate audibly, do not assume quant is required |

**Build-time install (training/export env):**
```bash
# Python training env (isolated venv, NOT shipped)
pip install torch torchaudio coqui-tts==0.22.0 onnx onnxruntime soundfile librosa phonemizer

# Optional: sherpa-onnx Python for export validation (match AAR 1.13.0)
pip install sherpa-onnx==1.13.0

# System deps
sudo apt install espeak-ng libsndfile1 sox  # only if espeak path explored
```

**Android (no new dep — already vendored):**
```kotlin
// android/ml/build.gradle.kts — no change; AAR already present:
// implementation(files("libs/sherpa-onnx-1.13.0.aar"))
// Do NOT add piper, espeak, or coqui runtime to :ml or :app.
```
</standard_stack>

<architecture_patterns>
## Architecture Patterns

### System Architecture Diagram

P2 data flow (offline, sequential — third leg only, after P1 MT):

```
[ LessonTranslatorScreen — Compose ]                [ EngineProvider.real(context) ]
         |                                                     |
         | 1. User taps "Speak" (or pipeline auto-plays)       |
         |    satText = "ᱡᱚᱦᱟᱨ" (Ol Chiki from MT or           |
         |             precomputed lesson)  ---------------->  TTSEngine.synthesize(text, language="sat")
         |                                                     |
         |               +---------------- SherpaOnnxTtsAdapter ( :ml ) ----------------+
         |               |                                                              |
         |               | 2. Validate  OlChikiValidator (U+1C50–U+1C7F)                 |
         |               |    reject if no Ol Chiki codepoint unless lang=="hi" fallback |
         |               |    log Vachak-TTS DEBUG (input + lang)                       |
         |               |                                                              |
         |               | 3. Resolve dir  packPath = P5 sync/ active pack             |
         |               |    else SherpaAssets.prepare(context,"tts")                  |
         |               |    (recursive copy assets/vachak_models/tts/ → filesDir)      |
         |               |    handle model.onnx + tokens.txt + lexicon.txt              |
         |               |         + espeak-ng-data/ (if shipped)                       |
         |               |                                                              |
         |               | 4. ensureLoaded()  @Synchronized lazy singleton              |
         |               |    OfflineTtsVitsModelConfig(                                |
         |               |      model="$baseDir/model.onnx",                            |
         |               |      tokens="$baseDir/tokens.txt",                            |
         |               |      lexicon="$baseDir/lexicon.txt",                          |
         |               |      dataDir="$baseDir/espeak-ng-data")                      |
         |               |    OfflineTtsConfig(model=OfflineTtsModelConfig(             |
         |               |      vits=vits, numThreads=1, debug=false))                  |
         |               |    OfflineTts(null, config)  // null AssetManager            |
         |               |                                                              |
         |               | 5. generate(text)  VITS inference on CPU (arm64 NEON)        |
         |               |    → GeneratedAudio(samples: FloatArray, sampleRate: Int)    |
         |               |    guard: samples.size > 400 (not 12ms blip)                |
         |               |         isFixture=false, warning=null for final voice        |
         |               |                                                              |
         |               +--------------------------------+-----------------------------+
         |                                                |
         | 6. EngineResult.Ok(ShortArray PCM16)  <--------+
         |    (FloatArray → ShortArray via *32767; interleave mono)                   |
         |    AudioTrack(MODE_STREAM, sampleRate, mono, PCM16) → play()               |
         |    LatencyTracker markTtsBegin → markAudioBegin; total <3s gate (P6)      |
         |
[ LatencyTracker ] -- reports ttsMs via Vachak-TTS / Vachak-Latency log + BENCHMARK_REPORT.md
[ THIRD_PARTY_NOTICES.md + docs/MODEL_AND_DATA_PROVENANCE.md ] -- voice consent + CC BY 4.0 per artifact
```

Build-time pipeline (Python, offline, not shipped):

```
[ IndicVoices 19,779 + Nirantar 13,503 + Common Voice 533 + Rasa ]
                |  (CC BY 4.0 verified per file)
                v
[ ml/tts/data prep: resample 22050 mono, trim, loudnorm, manifest.tsv ]
   splits: train/dev/eval  (IN22 never in train; 10% dev held-out)
                |
                v
[ Coqui/MunTTS VITS train: text_encoder + flow + duration_predictor + HiFi-GAN ]
   loss: kl + duration + mel + adv + fm  |  single-speaker Santali subset
                |
                v
[ export_onnx: torch.onnx.export(merged VITS) → model.onnx + tokens.txt + lexicon.txt ]
   validate: onnx.checker + sherpa_onnx OfflineTts audible test
                |
                v
[ (optional) quantize_onnx: onnxruntime dynamic int8 if >80MB ]
                |
                v
[ packages/ pack builder (P5): manifest.json + sha256 + license list → .vachakpack ]
```

Sequential enforcement: TTS never resident alongside ASR/MT; `numThreads=1`, `@Synchronized ensureLoaded()` like `SherpaOnnxTtsAdapter.kt:25`; model released via `tts.release()` between pipeline legs if memory pressure (P6). All three legs share one `Vachak-*` log namespace.

### Recommended Project Structure
```
ml/tts/                                   # P2 primary — Python training/export
├── adapter.py                            # keep: TTSAdapter + FallbackTTSAdapter (dev)
├── pipeline.py                           # extend: prepare_data → train_vits → export_onnx → quantize_onnx → convert_sherpa → prepare_android_bundle
├── export_onnx.py                        # extend: real VITS export (torch.onnx.export + onnx.checker) vs scaffold
├── evaluate.py                           # extend: check_wav_valid + evaluate_voice (MOS/latency/size gates)
├── requirements.txt                      # add: torch, coqui-tts, onnx, onnxruntime, soundfile, librosa
├── configs/
│   ├── vits_santali_single.yaml          # NEW — VITS hyperparams for Santali (22k, batch, epochs, lr)
│   └── tokens_santali.txt                # NEW — Ol Chiki char inventory (U+1C50–U+1C7F + specials sil/eos/sp)
├── data/
│   ├── fixtures/dev_manifest.tsv         # keep (dev), but real manifest is NOT Hindi "परीक्षण।" — replace with Santali Ol Chiki
│   └── santali_manifest.tsv              # NEW — curated Santali (text Ol Chiki + wav 22k mono) manifests
├── runs/
│   └── santali_vits/                     # NEW — checkpoints + exported onnx
└── tests/test_tts.py                     # extend: audible + lexicon + size gates

android/
├── ml/
│   ├── libs/sherpa-onnx-1.13.0.aar       # keep (already vendored) — do NOT add Piper/mpl deps
│   ├── src/main/java/com/vachak/ml/
│   │   ├── SherpaOnnxTtsAdapter.kt       # MODIFY — add pack-path ctor + Ol Chiki validate + isFixture=false for final voice
│   │   ├── SherpaAssets.kt               # keep — recursive copy handles model.onnx + tokens.txt + lexicon.txt + espeak-ng-data sidecars
│   │   ├── TtsAdapter.kt                 # keep — interface + SynthAudio
│   │   ├── EspeakFallbackTtsAdapter.kt   # keep (dev-only, labeled NOT final)
│   │   └── MockTtsAdapter.kt             # keep (tests)
│   └── src/main/assets/vachak_models/tts/ # dev-bundled fallback until P5 pack owns the path
│       ├── model.onnx (39M zh — to replace)
│       ├── tokens.txt (pinyin — to replace)
│       ├── lexicon.txt (hanzi — to replace)
│       └── espeak-ng-data/ (full multilingual — to slim or drop for sat)
└── app/
    ├── src/main/assets/vachak_models/tts/ # symlink/copy from :ml for assembleDebug (current layout)
    ├── src/main/java/com/vachak/engine/
    │   ├── EngineContracts.kt            # interface TTSEngine:49 (synthesize(text, language):EngineResult<ShortArray>)
    │   ├── EngineProvider.kt:48          # swap point: real() currently returns SherpaTtsAdapter (old name) — wire new adapter
    │   └── LatencyTracker.kt             # reuse for ttsMs gate
    └── src/main/java/com/vachak/ui/
        └── MainScreen.kt                 # Speak button: engine.tts.synthesize(text,"sat") vs legacy "mund" alias

packages/                                  # P5 owns pack builder, but P2 must emit pack-ready artifact layout
docs/
├── MODEL_AND_DATA_PROVENANCE.md          # MUST add Santali VITS provenance row before merge
└── THIRD_PARTY_NOTICES.md                # MUST add IndicVoices/Nirantar/Rasa/Coqui/sherpa licenses + voice consent

datasets/                                  # verified CC BY 4.0
├── hin_sat/ (MT, not P2)
└── (tts manifests live under ml/tts/data/ — not datasets/ root, to avoid mixing)
```

File→responsibility: `pipeline.py` owns prepare→train→export→quant→sherpa; `SherpaOnnxTtsAdapter.kt` owns pack-path resolution + Ol Chiki validate + `OfflineTts` lifecycle + `SynthAudio` warning; `SherpaAssets.kt` owns extraction; `EngineProvider.kt` owns injection; `MainScreen.kt` owns trigger + `AudioTrack` play; `THIRD_PARTY_NOTICES.md` owns license gate.

### Pattern 1: Sherpa VITS OfflineTts Wiring (Kotlin — proven, P2 reuses verbatim)
**What:** Single `OfflineTts` singleton, lazy, synchronized, with `OfflineTtsVitsModelConfig(model,tokens,lexicon,dataDir)` + `OfflineTtsConfig(... numThreads=1)` + `OfflineTts(null, config)` for filesystem-extracted assets. This is the **only** TTS runtime allowed in the APK (sherpa Apache-2.0); Piper's `piper-phonemize` native layer is forbidden.
**When to use:** Every `synthesize()` call; pack-path variant just changes `baseDir` resolution.
**Example (current proven wiring, `android/ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt:18–53`):**
```kotlin
class SherpaOnnxTtsAdapter(
    private val context: Context,
    private val modelDir: String = "tts" // in P2: add packDir: String? = null
) : TtsAdapter {
    private var tts: OfflineTts? = null
    private val tag = "Vachak-TTS"

    @Synchronized
    private fun ensureLoaded(): OfflineTts {
        if (tts != null) return tts!!
        val baseDir = SherpaAssets.prepare(context, modelDir)
        // For P5 pack path: if (packDir != null && File(packDir).exists()) baseDir = packDir
        val vits = OfflineTtsVitsModelConfig(
            model = "$baseDir/model.onnx",
            tokens = "$baseDir/tokens.txt",
            lexicon = "$baseDir/lexicon.txt",
            dataDir = "$baseDir/espeak-ng-data" // omit if Ol Chiki char tokens (no espeak)
        )
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = vits, numThreads = 1))
        Log.d(tag, "creating OfflineTts (dir=$baseDir)")
        tts = OfflineTts(null, config) // null AssetManager — files are under filesDir
        Log.d(tag, "OfflineTts ready")
        return tts!!
    }
    override fun synthesize(text: String, lang: String): SynthAudio {
        require(text.isNotBlank()) { "synthesize requires non-empty text" }
        // P2: validate Ol Chiki
        // require(text.any { it.code in 0x1C50..0x1C7F }) { "no Ol Chiki codepoint" }
        val audio = ensureLoaded().generate(text) // GeneratedAudio(samples: FloatArray, sampleRate: Int)
        Log.d(tag, "synthesized \"$text\" -> ${audio.samples.size} samples @ ${audio.sampleRate} Hz")
        return SynthAudio(samples = audio.samples, sampleRate = audio.sampleRate,
            backend = "sherpa-onnx", isFixture = false, warning = null)
    }
}
```

### Pattern 2: Ol Chiki-Native Tokenization (No espeak G2P)
**What:** Santali Ol Chiki is a featural alphabet (30 letters + diacritics), not an abugida requiring complex G2P. Use character-level `tokens.txt` (each Ol Chiki codepoint as a token) + `lexicon.txt` mapping frequent words → char sequence. espeak-ng has no `sat` model, and its `espeak-ng-data` adds 10–15 MB + GPL complexity for zero Santali benefit. Ship slim `espeak-ng-data` (or none) and keep phonemization deterministic.
**When to use:** Lexicon/token build for Santali VITS; P2 must not copy the Chinese pinyin lexicon.
**Example (lexicon + tokens layout):**
```
# tokens.txt (sherpa VITS style: token -> id, one per line)
sil 0
eos 1
sp 2
ᱚ 3
ᱛ 4
ᱪ 5
...
ᱹ 32
ᱼ 33
ᱽ 34

# lexicon.txt (word -> tokens, space-separated, as sherpa expects)
ᱡᱚᱦᱟᱨ  ᱡ 𞓚 𞓟 𞓐 𞓑
ᱥᱟᱱᱛᱟᱲ  ᱥ 𞓐 𞓝 𞓐 𞓑
# fallback: unknown words split into chars via tokens.txt
```
`ml/translation/scripts/convert_ct2.py:0x1C50<=o<=0x1C7F` is the canonical Ol Chiki detector to reuse.

### Pattern 3: Manifest-Driven VITS Fine-Tune (Coqui recipe, single-speaker)
**What:** Curate single-speaker manifests (`text \t wav_path`), resample to 22050 mono 16-bit, force `sample_rate=22050` in `TTSConfig` and VITS config, train with Coqui's `VitsArgs` + `VitsConfig` (or MunTTS `train.py` wrapper), balancing the 4 losses (kl, duration, mel, adversarial). Use warm-start from LJSpeech/VCTK VITS if Santali-from-scratch at 33k utterances still underfits — transfer accelerates convergence on low-resource alphabetic scripts.
**When to use:** `ml/tts/pipeline.py:train_vits` implementation for `02-01`.
**Example (Coqui config sketch, cf. `ml/tts/configs/` to add):**
```yaml
# vits_santali_single.yaml
model: vits
run_name: santali_vits_single_22050
project_name: vachak_tts
batch_size: 32            # RTX 3050 4GB: 16; A100: 64
eval_batch_size: 16
num_loader_workers: 4
mixed_precision: true
epochs: 1000              # early-stop on dev mel loss / MOS proxy
save_step: 2000
print_step: 100
phoneme_language: sat     # or null if char tokens
phoneme_cache_path: null
compute_input_seq_cache: true
audio:
  sample_rate: 22050      # matches pipeline.py:TTSConfig.sample_rate
  hop_length: 256
  win_length: 1024
  mel_fmin: 0
  mel_fmax: 8000
  num_mels: 80
use_phonemes: false       # true only if espeak phonemes retained — recommend false for Ol Chiki chars
phonemizer: null          # null for char tokens; "espeak" would pull espeak-ng-data
characters:
  pad: "<PAD>"
  eos: "<EOS>"
  bos: "<BOS>"
  characters: "ᱚᱛᱜᱝᱮᱟᱪᱫᱱᱯᱠᱢᱭᱨᱞᱣᱥᱦᱧᱴᱷᱸᱹᱺᱻᱼᱽ"  # Ol Chiki block excerpt — fill full inventory
  punctuations: ".,!?;:—\"'()[]"
  phonemes: null
```

### Pattern 4: Export → ONNX → Sherpa-Validate (single model.onnx)
**What:** Unlike MT's 3-graph encoder/decoder/past, VITS exports as **one** `model.onnx` (merged acoustic model + HiFi-GAN vocoder). Trace with dummy `input_ids` (phoneme/char IDs) and `scales` through `torch.onnx.export(..., opset_version=17, dynamo=False)`, validate with `onnx.checker.check_model`, then load from **both** `sherpa_onnx.OfflineTts` Python and Kotlin `OfflineTts` and generate audible WAV.
**When to use:** `ml/tts/export_onnx.py:export_vits_onnx` for `02-01`.
**Example (export sketch, mirrors `IndicTrans2` wrapper pattern but simpler):**
```python
# ml/tts/export_onnx.py — extend scaffold
import torch, onnx
from pathlib import Path
def export_vits_onnx(checkpoint_dir: str, out_dir: str, sample_rate: int = 22050):
    ckpt = torch.load(Path(checkpoint_dir)/"best_model.pth", map_location="cpu")
    model = load_vits_from_checkpoint(ckpt).eval()
    dummy_ids = torch.randint(0, len(vocab), (1, 20), dtype=torch.long)
    dummy_lengths = torch.tensor([20], dtype=torch.long)
    dummy_scales = torch.tensor([0.667], dtype=torch.float32) # noise/duration scale
    out = Path(out_dir); out.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(model, (dummy_ids, dummy_lengths, dummy_scales),
        str(out/"model.onnx"),
        input_names=["input","input_lengths","scales"],
        output_names=["wav"],
        dynamic_axes={"input":{0:"batch",1:"length"}, "wav":{1:"samples"}},
        opset_version=17, dynamo=False)
    onnx.checker.check_model(onnx.load(str(out/"model.onnx")))
    # also write tokens.txt (vocab) + lexicon.txt (word->tokens) via vocab dump
    return {"model": str(out/"model.onnx"), "sample_rate": sample_rate}
```

### Pattern 5: Pack-Path Resolution (P5-ready, P2-prequisite)
**What:** `SherpaOnnxTtsAdapter` must resolve `baseDir` from the active language pack first, falling back to bundled `assets/vachak_models/tts/`. This keeps `02-02` demo-ready even before `sync/` exists, and makes P5 a one-line switch (`EngineProvider.real` passes `packDir` from `PackManager.activePack?.ttsDir`).
**When to use:** Constructor / `ensureLoaded()` of the TTS adapter.
**Example (pack-path idiom, analogous to P1 `IndicTrans2Adapter` plan):**
```kotlin
class SherpaOnnxTtsAdapter(
    private val context: Context,
    private val modelDir: String = "tts",
    private val packDir: String? = null // P5 injects: /data/data/com.vachak/files/packs/sat_v1/tts
) : TtsAdapter {
    @Synchronized
    private fun resolveBaseDir(): String {
        packDir?.let { if (File(it).exists() && File(it,"model.onnx").exists()) return it }
        return SherpaAssets.prepare(context, modelDir)
    }
}
```

### Anti-Patterns to Avoid
- **Shipping the Chinese DEV-FIXTURE as "Santali":** `vits-zh-aishell3` produces Mandarin prosody even on Ol Chiki input (pinyin re-mapping). It is labeled `isFixture=true`/`DEV-FIXTURE public voice, NOT final Mundari` in `SherpaOnnxTtsAdapter.kt:51` — do not clear that flag until a real Santali checkpoint is loaded and `samples.size` is audibly Santali.
- **Bundling Piper or eSpeak as a runtime dependency:** `THIRD_PARTY_NOTICES.md:15` flags Piper GPL-3.0; `MODEL_AND_DATA_PROVENANCE.md:25` warns deliberately. Adding `org.rhvoice:rhvoice` or `com.alphacep:piper` or linking `libespeak-ng.so` into `android/ml` contaminates the APK. Training may use Piper as an experiment, but **no GPL .so or `EXECUTE` of piper at runtime**.
- **Hardcoding `modelDir="tts"` without pack fallback:** P5 swaps models outside the APK; without `packDir`, P2's deliverable is uninstallable on a pack-based device. Always implement the packDir→fallback chain.
- **Training multi-speaker on all 433 Nirantar speakers without curation:** Multi-speaker VITS needs speaker IDs and at least ~10 min per speaker; many Nirantar speakers are <2 min. Uncurated multi-speaker will collapse. Start single-speaker (best-SNR Santali speaker from IndicVoices) then consider fine-tuning a small multi-speaker subset only if teacher voice variety is requested.
- **Using 44.1 kHz studio rate on device:** Sherpa VITS ONNX at 44.1k doubles mel channels and inference cost; HiFi-GAN upsampling stresses 2GB. 22.05k is the VITS convention and matches `pipeline.py:22050`; Common Voice Santali (~533 clips) is natively 48k — always downsample before training.
- **Mixing Hindi and Ol Chiki without script detection:** Feeding Hindi Devanagari `हिन्दी` to a Santali VITS produces garbage or silence; the `MainScreen.kt: Speak` button currently uses `lang="mund"` (legacy `"mundari"` alias). Keep `lang` as `sat`/`sat_Olck`/`olck` canonical and map `mund`→`sat` for backwards compat, but validate script before synthesis.
</architecture_patterns>

<dont_hand_roll>
## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| G2P for Santali | Hand-written Ol Chiki → IPA rules | Character-level tokens (`tokens.txt` char inventory + `lexicon.txt` word→chars) | Ol Chiki is alphabetic with 1:1 letter-phoneme mapping; custom G2P introduces bugs in diacritics `ᱸᱹᱺ` and doubles work; sherpa VITS learns pronunciation from char tokens without G2P |
| WAV resample & mono | `ffmpeg` shell one-liners per file | `librosa.load(sr=22050, mono=True)` + `soundfile.write(..., subtype='PCM_16')` + manifest validator | 44.1k→22k must be anti-aliased; shell one-liners miss loudness normalize, silence trim, clip detection, and manifest length drift; Coqui's `formatter` enforces exact sample_rate/hop alignment |
| VITS training loop | Custom PyTorch VAE + flow + vocoder loop | `coqui-ai/TTS` `VitsTrainer` or `microsoft/MunTTS` `train.py` | VITS has 4 joint losses (kl, duration, adversarial, feature matching) + stochastic duration predictor + normalizing flow — hand-rolled will diverge or forget the duration loss; Coqui handles mixed precision + checkpoint + onnx export hooks |
| ONNX export | Manual `torch.jit.trace` | `torch.onnx.export(..., opset_version=17, dynamo=False)` + `onnx.checker.check_model` | `ml/tts/export_onnx.py:36` mandates checker; trace without dynamic axes produces fixed-length ONNX that fails on lesson-length variation |
| Asset extraction | `AssetManager.open` per file ad-hoc | `SherpaAssets.prepare(context, subdir)` (`android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt:22`) recursive `list()` + `copyTo` + `null` AssetManager | Handles nested `espeak-ng-data` + external `.onnx.data` correctly; pattern already ships with sherpa AAR; reimplementing risks missing sidecar or permission errors on `filesDir` |
| Latency plumbing | `System.currentTimeMillis()` ad-hoc | `LatencyTracker` (`android/ml/.../LatencyTracker.kt`) + app `LatencyTracker` + `Log.d("Vachak-TTS")` | Provides monotonic `elapsedRealtimeNanos()` and `isFixture` flag required by `BENCHMARK_REPORT.md: forbid_dev_as_android` guard; ad-hoc mixes wall-clock with monotonic and loses budget reporting |
| Voice consent / license tracking | CSV in `ml/tts/data/` | `THIRD_PARTY_NOTICES.md` + `docs/MODEL_AND_DATA_PROVENANCE.md` tables + per-file `manifest.json` with `license` field (`datasets/hin_sat/manifest.json:1251` pattern) | `AGENTS.md: License tracking` and `REQUIREMENTS.md:TTS-02` require repo license, model license, dataset license, **and voice consent** before merge; CSV is invisible to audit |
| Audio playback | `MediaPlayer` with file path | `AudioTrack(MODE_STREAM, sampleRate, CHANNEL_OUT_MONO, ENCODING_PCM_FLOAT or PCM_16)` + `FloatArray→ShortArray` via `*32767f` (cf. `MainScreen.kt: playPcm`) | `MediaPlayer` has latency and file-IO overhead; TTS pipeline needs stream playback within the <1s budget; `AudioTrack` is already used for sherpa TTS in `MainScreen.kt` |

**Key insight:** Santali VITS's value is in the **curated single-speaker manifest**, not clever runtime code. The sherpa runtime is already done (`SherpaOnnxTtsAdapter.kt` is ~50 LOC correct). Spending time hand-rolling G2P or a trainer is the fastest path to *unaudible* audio. Curate one clean Santali voice, train with Coqui's proven recipe, export one ONNX, and validate **audibly** — not just numerically.
</dont_hand_roll>

<common_pitfalls>
## Common Pitfalls

### Pitfall 1: Training on Hindi-Labeled "Santali" Placeholders (Manifest Contamination)
**What goes wrong:** `train_vits` ingests `ml/tts/data/fixtures/dev_manifest.tsv` lines `परीक्षण।` (Hindi Devanagari) labeled as Mundari/Santali, learns Hindi prosody, and synthesizes Hindi, not Santali — but unit tests pass because they check only `wav_valid`.
**Why it happens:** `ml/tts/data/fixtures/dev_manifest.tsv:2` is synthetic Hindi; `ml/tts/pipeline.py:prepare_data` only checks `empty`/`missing_audio`, not script. `REQUIREMENTS.md:TTS-01` says IndicVoices **Santali** 19,779 but the pipeline lists no script filter.
**How to avoid:** Add Ol Chiki validator to `prepare_data` (reuse `convert_ct2.py` range check: every `text` must contain ≥1 `U+1C50–U+1C7F` for Santali; reject Devanagari-only lines with an explicit `issues["wrong_script"]` counter). Keep `dev_manifest.tsv` as `DEV FIXTURE ONLY` and gate `train_vits` on `cfg.target_lang=="sat"` + `manifest_contains_OlChiki`.
**Warning signs:** Training log shows `characters: "अआइ..."` devanagari vocab instead of `ᱚᱛᱜ…`; lexical check `has_OlChiki(text)==False` for >50% of manifest.

### Pitfall 2: espeak-ng-data Bloat + GPL Leakage into APK
**What goes wrong:** Full multilingual `espeak-ng-data/:632K` (actually ~3 MB extracted, 100+ `*_dict` files via `phondata:2M`) is copied by `SherpaAssets.prepare` into every install, counts against 20–80MB budget, and brings GPL-3.0 obligations (`THIRD_PARTY_NOTICES.md:67` flags `eSpeak NG GPL-3.0` as fallback-only). If VITS uses espeak phonemes, `dataDir` must point at that tree, so it ships.
**Why it happens:** Chinese fixture requires `espeak-ng-data` for pinyin→phoneme lookup; copying the pattern verbatim for Santali drags the whole tree even though `espeak-ng-data/lang` has no `sat` directory.
**How to avoid:** Use Ol Chiki char tokens (`use_phonemes: false` in Coqui config) so `dataDir` can be `null`/empty; if espeak is truly needed, strip `espeak-ng-data` to only the used `inc`/`sat` subtree and document GPL handling in `THIRD_PARTY_NOTICES.md` with a slimmed size note; measure unpacked `du -sh` as part of the 80MB gate and prefer no-espeak path.
**Warning signs:** `du -sh android/app/src/main/assets/vachak_models/tts/espeak-ng-data` > 5 MB; `lexicon.txt` still contains pinyin `^ i1 #0` after Santali export.

### Pitfall 3: Multi-Speaker Nirantar Without Curation → Collapsed Prosody
**What goes wrong:** Feeding all 13,503 Nirantar utterances (433 speakers, avg ~31 utt/spk, ~22 min/spk) into a single-speaker VITS config produces washed-out prosody and speaker leakage; or multi-speaker VITS without `speaker_ids` in manifest produces uniform but unnatural voice.
**Why it happens:** Nirantar is designed for ASR diversity (8 districts, many speakers) not single-speaker TTS coherence. `docs/PHASES.md:58` says 161h/433spk but P2's deliverable is **one** teacher voice for classroom playback.
**How to avoid:** Curate **one** primary speaker from IndicVoices Santali ( IndicVoices has cleaner studio recordings) plus optionally the best-SNR Nirantar speaker (≥50 utterances, ≥15 min, low WER) as a secondary voice; filter by duration (3–10 s), SNR, and silence ratio; train single-speaker first; only add `speaker_embedding` + multi-speaker VITS if P6 latency still <1s and MOS on single speaker plateaus.
**Warning signs:** Training `speaker_embedding` loss doesn't converge; synthesized audio drifts speaker timbre mid-sentence.

### Pitfall 4: 39MB→>80MB After Adding espeak-ng-data + Tokens + Lexicon
**What goes wrong:** `model.onnx:39M` alone fits, but `model.onnx` (Santali fine-tune may be 45–60M) + `espeak-ng-data` (slimmed) 8MB + `tokens.txt` 10KB + `lexicon.txt` 1MB + `SherpaAssets` copy overhead pushes TTS slice to 65–90MB, failing the 80MB cap in `AGENTS.md:Model budget`.
**Why it happens:** Coqui VITS default keeps full 80-mel HiFi-GAN; no quantization or channel pruning. `android/ml/libs/sherpa-onnx-1.13.0.aar:54M` is separate from model slice but counts in APK size — must not be double-counted in TTS slice during budgeting.
**How to avoid:** First spike: export Santali VITS at fp32 and `du -sh ml/tts/runs/santali_vits/*.onnx`; if >60 MB, try (a) `onnxruntime` graph optimization (`onnxruntime.transformers.optimizer` like `indictrans2-onnx-export/src/onnx_bundle_optimize.py:246`), (b) dynamic int8 (`quantize_dynamic per_channel=True`) — note VITS int8 may degrade prosody, so A/B listen, (c) drop `espeak-ng-data` via char tokens; report slice size with `apkanalyzer` or `unzip -l` breakdown before claiming ≤80MB.
**Warning signs:** `apkanalyzer` shows `assets/vachak_models/tts/` >80MB; `lib/arm64-v8a/libsherpa-onnx-jni.so` counted in TTS slice instead of app slice.

### Pitfall 5: Ol Chiki Rendering vs. TTS Input Confusion (Odia vs Ol Chiki)
**What goes wrong:** UI shows `ନମସ୍କାରଂ` (Odia script, U+0B00 block, from CT2 baseline `ml/translation/mundari/it2_ct2_baseline.py: Santali sample`) and tester marks "Ol Chiki present" — but true Ol Chiki is `ᱱᱚᱢᱚᱥᱠᱟᱨ` (U+1C50 block). TTS trained on Odia-script Santali would synthesize wrong phonemes.
**Why it happens:** Santali historically rendered in multiple scripts; `IndicTrans2` supports `sat_Olck` (Ol Chiki) but also `sat_Beng` (Bengali script) via `inference/flores_codes_map_indic.py: "sat_Olck": "or"→"sat"` mapping; baseline artifact confusion is documented in `ml/translation/mundari/it2_ct2_baseline.py: VERIFIED: ନମସ୍କାରଂ (Santali, NOT Mundari)` — that string is actually Odia/Bengali rendering, not Ol Chiki.
**How to avoid:** Define P2 acceptance as **at least one U+1C50–U+1C7F codepoint** in both TTS training manifests and synthesis results; add `OlChikiValidator` regex `[\u1C50-\u1C7F]` (shared with MT `convert_ct2.py`) and unit-test `has_OlChiki(synthesized_text)`; translate Hindi→`sat_Olck` strictly and reject `Ben/` fallback at data-prep time.
**Warning signs:** Manual review shows looped diacritics `ᱸ` without base letters; `tokens.txt` contains `ନ` (Odia) instead of `ᱱ` (Ol Chiki Santali).

### Pitfall 6: Offline Contract Regression (Network Fetch at Build or Runtime)
**What goes wrong:** Training script calls `hf_hub_download("ai4bharat/...")` at build without `--offline`, or `SherpaAssets.prepare` silently falls back to `context.assets.open` network fetch on missing file, or Gradle adds `mavenCentral()` dependency that fetches `piper` model at `assembleDebug`.
**Why it happens:** `ml/tts` scaffold does no network today (`python -m unittest ml.tts.tests.test_tts` runs without gated data), but adding Coqui fine-tune will introduce `huggingface_hub` downloads.
**How to avoid:** Audit `AndroidManifest.xml` (currently has no `INTERNET`/`ACCESS_NETWORK_STATE` at `AndroidManifest.xml:7` — keep it that way); `grep -r "requests\|urllib\|OkHttp\|HttpURLConnection\|hf_hub_download\|snapshot_download" android/ml android/app ml/tts` must be 0 hits in runtime path; build-time downloads must be cached under `models/` and committed or fetched via explicit `scripts/fetch_*` like `scripts/fetch_android_models.sh`; run `ml/benchmarks/harness.network_request_audit()` equivalent before release.
**Warning signs:** `Log.w("Vachak-Assets","asset copy failed: ... (ENOENT)")` in logcat; `gradle build --offline` fails; `THIRD_PARTY_NOTICES.md` lists a model without a pinned `HF` revision hash.

### Pitfall 7: isFixture Flag Never Cleared (Unaudible Acceptance)
**What goes wrong:** `SherpaOnnxTtsAdapter.synthesize` returns `isFixture=true, warning="DEV-FIXTURE public voice, NOT final Mundari"` even after a real Santali checkpoint is loaded, so `LatencyTracker`/`BENCHMARK_REPORT.md` gate `forbid_dev_as_android` correctly rejects the latency claim, but the demo still shows Chinese audio and is marked pass.
**Why it happens:** `MockTtsAdapter:19` and `EspeakFallbackTtsAdapter:13` and `SherpaOnnxTtsAdapter:51` all set `isFixture=true` by default; wiring the real model requires flipping `isFixture=false` conditionally on `modelDir != "tts"` or `packDir != null`.
**How to avoid:** Adapter must set `isFixture = (modelDir=="tts" && packDir==null && modelIsChineseFixture)` where `modelIsChineseFixture = lexiconContains("一 ^")`; gate `isFixture==false && samples.size > sampleRate*0.3` (>300ms audible) + MOS ≥3.5 proxy (human or `evaluate_voice` WAV-valid + duration) before claiming success.
**Warning signs:** `Vachak-TTS synthesized "ᱡᱚᱦᱟᱨ" -> 264 samples @ 16000 Hz` (16 ms — the 12ms blip class mentioned in `docs/phases/PLAN-P2-mundari-tts.md:22`).

### Pitfall 8: Sample Rate Mismatch (Train 44.1k, Infer 22k, Play 16k)
**What goes wrong:** TTS trained at 22050 produces 22k WAV but `AudioTrack` is initialized at 16000, so playback is half-speed and garbled; or Mel `hop_length=256` at 44.1k misaligns with ONNX `108` expected at 22k, so `generate()` throws `ORT Fail: shape mismatch`.
**Why it happens:** `ml/tts/pipeline.py:TTSConfig.sample_rate=22050` but `ASR` configs use 16000 (`IndicConformerAsrAdapter.kt: buildConfig sampleRate=16000`); existing `mock` adapters use 22050.
**How to avoid:** Single source `sample_rate=22050` for TTS; `TtsAdapter` returns `SynthAudio.sampleRate` truthfully and caller creates `AudioTrack` with that exact rate (`AudioTrack.Builder().setAudioFormat(AudioFormat.Builder().setSampleRate(audio.sampleRate)…`)); training config `audio.sample_rate` must equal export `sample_rate` arg and adapter's `SampleRate` claim.
**Warning signs:** `adb logcat` shows `Vachak-TTS synthesized "..." -> 22050 samples @ 22050 Hz` but `AudioTrack: play() sampleRate 16000 != 22050`; MOS drops though loss converged.
</common_pitfalls>

<code_examples>
## Code Examples

Verified patterns from repo + sherpa-onnx contract (not speculative; cite file:line):

### 1. Current proven TTS wiring (Kotlin) — reuse, add pack path
```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt:18-53
package com.vachak.ml
import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig

class SherpaOnnxTtsAdapter(
    private val context: Context,
    private val modelDir: String = "tts",
    private val packDir: String? = null // P2 adds this for P5-active pack
) : TtsAdapter {
    private var tts: OfflineTts? = null
    private val tag = "Vachak-TTS"
    @Synchronized private fun ensureLoaded(): OfflineTts {
        if (tts != null) return tts!!
        val baseDir = resolveDir()
        val vits = OfflineTtsVitsModelConfig(
            model = "$baseDir/model.onnx",
            tokens = "$baseDir/tokens.txt",
            lexicon = "$baseDir/lexicon.txt",
            dataDir = "$baseDir/espeak-ng-data" // null out if Ol Chiki char tokens
        )
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = vits, numThreads = 1))
        Log.d(tag, "creating OfflineTts (dir=$baseDir)")
        tts = OfflineTts(null, config) // null AssetManager — files under filesDir
        Log.d(tag, "OfflineTts ready")
        return tts!!
    }
    private fun resolveDir(): String {
        packDir?.let { if (java.io.File(it,"model.onnx").exists()) return it }
        return SherpaAssets.prepare(context, modelDir)
    }
    override fun synthesize(text: String, lang: String): SynthAudio {
        require(text.isNotBlank())
        val audio = ensureLoaded().generate(text)
        Log.d(tag, "synthesized \"$text\" -> ${audio.samples.size} samples @ ${audio.sampleRate} Hz")
        // P2: gate audible length + Ol Chiki codepoint presence before calling isFixture=false
        return SynthAudio(samples = audio.samples, sampleRate = audio.sampleRate,
            backend = "sherpa-onnx", isFixture = false, warning = null)
    }
}
```

### 2. Asset extraction (handles model.onnx + espeak-ng-data nested dirs)
```kotlin
// Source: android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt:18-57
object SherpaAssets {
    const val ASSET_ROOT = "vachak_models"
    fun prepare(context: Context, subdir: String): String {
        val outDir = java.io.File(context.filesDir, "$ASSET_ROOT/$subdir").also { it.mkdirs() }
        copyTree(context, "$ASSET_ROOT/$subdir", outDir)
        return outDir.absolutePath
    }
    private fun copyTree(context: Context, assetPath: String, outDir: java.io.File) { /* recursive list()+copyTo */ }
}
// Gotcha: external .onnx.data sidecars >2GB not relevant to TTS single-onnx, but copyTree must
// still handle leaf .onnx as a file (entries==null branch at SherpaAssets.kt:30)
```

### 3. EngineContracts TTSEngine (the swap seam)
```kotlin
// Source: android/app/src/main/java/com/vachak/engine/EngineContracts.kt:65-69
interface TTSEngine {
    fun supports(language: String): Boolean
    fun loadModel(packId: String): EngineResult<Unit>
    fun synthesize(text: String, language: String): EngineResult<ShortArray>
}
// P2 adapter must also honor the :ml TtsAdapter.kt:9 contract (FloatArray path):
// interface TtsAdapter { fun synthesize(text:String, lang:String):SynthAudio }
// Bridge note: EngineProvider's SherpaTtsAdapter (EngineContracts TTSEngine) wraps the :ml one.
// Keep FloatArray internally (sherpa generates FloatArray) and convert to ShortArray at boundary:
//   ShortArray(samples.size) { ( (samples[it].coerceIn(-1f,1f) * 32767).toInt().toShort() ) }
```

### 4. Pipeline stage contracts (Python — scaffold to extend)
```python
# Source: ml/tts/pipeline.py:24-98 (current scaffolding, to replace TODOs)
from ml.tts.pipeline import TTSConfig, prepare_data, train_vits, export_onnx, quantize_onnx, convert_sherpa, prepare_android_bundle
cfg = TTSConfig(base_arch="vits", target_lang="sat", sample_rate=22050, adapter_kind="final")
rep = prepare_data(santali_manifest) # must add OlChiki validator
plan = train_vits(santali_manifest, cfg) # returns TODO until licensed data + GPU — P2 makes this real
export_onnx("ml/tts/runs/santali_vits", cfg, out_dir="ml/tts/runs/santali_vits_onnx")

# Source: ml/tts/export_onnx.py:17-36
from ml.tts.export_onnx import export_vits_onnx, verify_onnx
info = export_vits_onnx("ml/tts/runs/santali_vits", "ml/tts/runs/santali_sherpa", sample_rate=22050)
assert verify_onnx("ml/tts/runs/santali_sherpa/model.onnx")

# Source: ml/tts/evaluate.py:38-61 — WAV validity gate (no gated data needed)
from ml.tts.evaluate import evaluate_voice, check_wav_valid
ev = evaluate_voice("ᱡᱚᱦᱟᱨ", wav_bytes, measure_latency=True, model_size_mb=38.2)
assert ev.wav_valid.value is True
```

### 5. Ol Chiki validation (shared with MT)
```kotlin
// Utility to reuse from mt: ml/translation/scripts/convert_ct2.py:0x1C50–0x1C7F
object OlChikiValidator {
    private val olChikiRegex = Regex("[\u1C50-\u1C7F]")
    fun containsOlChiki(s: String) = olChikiRegex.containsMatchIn(s)
    fun isSantaliSantol(s: String) = containsOlChiki(s) // distinguish from Odia 0B00 / Bengali
}
// Python:
def has_ol_chiki(text: str) -> bool:
    return any(0x1C50 <= ord(c) <= 0x1C7F for c in text)
```

### 6. AudioTrack playback (from the demo UI)
```kotlin
// Source: android/app/src/main/java/com/vachak/ui/MainScreen.kt (speak path, sintetize→ShortArray→play)
engine.tts.synthesize(text, "sat").fold(
    onOk = { pcm -> playPcm(pcm) }, // playPcm builds AudioTrack with pcm.size & sampleRate
    onErr = { code, msg -> status = "tts err: $msg" }
)
private fun playPcm(pcm: ShortArray, sampleRate: Int = 22050) {
    val track = android.media.AudioTrack.Builder()
        .setAudioAttributes(android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
        .setAudioFormat(android.media.AudioFormat.Builder()
            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(pcm.size * 2).setTransferMode(android.media.AudioTrack.MODE_STATIC).build()
    track.write(pcm, 0, pcm.size); track.play()
}
```

### 7. License tracking row template (must file before merge)
```markdown
# docs/MODEL_AND_DATA_PROVENANCE.md — add per artifact
| Santali VITS — final voice | Vachak (fine-tune on IndicVoices+Nirantar+Rasa, CC BY 4.0) | CC BY 4.0 (weights: MIT-style first-party) | on-device TTS (sherpa-onnx VITS) | PRODUCED in P2 (SHA256: <hash>) |
| IndicVoices Santali 19,779 | AI4Bharat / HF | CC BY 4.0 | TTS training | VERIFIED (HF rev: <pin>) |
| Nirantar Santali 13,503 | adjaysagar/nirantar HF | CC BY 4.0 (verify per-file LICENSE) | TTS/ASR diversity | VERIFIED (rev+license note) |
| Voice speaker consent | <speaker ID + district + date> | Consent form on file (opt-in, revocable) | Single-speaker Santali voice | RECORDED (form hash) |

# THIRD_PARTY_NOTICES.md — add runtime entries
| sherpa-onnx | https://github.com/k2-fsa/sherpa-onnx | Apache-2.0 | TTS runtime (OfflineTts VITS) |
| Coqui TTS | https://github.com/coqui-ai/TTS | MPL-2.0 | TTS training (build-time only, not linked in APK) |
| espeak-ng-data (if shipped, slimmed) | https://github.com/espeak-ng/espeak-ng | GPL-3.0 | G2P data (note size + slimming) — prefer omitting |
```
</code_examples>

<sota_updates>
## State of the Art (2024-2025)

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Tacotron2 + WaveGlow two-stage TTS | **VITS** end-to-end (CVAE+flow+HiFi-GAN) and **Matcha-TTS** (flow-matching) | 2021–2024; sherpa-onnx adopted VITS/Matcha as first-class in 2024–25 | One `model.onnx` is simpler to pack; better latency than two-stage; Matcha slightly faster than VITS but less Indic fine-tune lore |
| Griffin-Lim vocoder | **HiFi-GAN** / **Vocos** within VITS/Matcha | 2023+ | Much better MOS at 22k; VITS internal HiFi-GAN is what makes 39M Chinese model sound natural with few samples |
| Hand-built lexicon per language | **Character-level** modeling for alphabetic scripts (Ol Chiki) | 2024 (low-resource Indic) | No espeak needed; TTS learns mapping from 30 letters; smaller `tokens.txt`/`lexicon.txt`, smaller APK |
| ARPAbet / espeak phonemes mandatory | **Char or byte tokens** competitive for shallow orthographies | 2024–25 (ByT5 / char-TTS) | Santali (alphabetic) benefits — avoid GPL `espeak-ng-data` entirely |
| On-device TTS = Cloud API wrap | **Sherpa-onnx OfflineTts VITS** on CPU (ARM NEON, `numThreads=1`) | 2024 (sherpa 1.10+ stabilized Android OfflineTts) | Meets AGENTS.md offline + 2GB; no `INTERNET` permission; this is now the reference path (cf. MT's ORT Mobile) |
| Piper as default open TTS | **Piper-training but sherpa-runtime** pattern (train with Piper-compatible checkpoints, infer via sherpa VITS to avoid GPL link) | 2024 (Piper GPL-3.0 successor) | Validates P2 decision: use Piper architecture if needed but **do not link `piper1-gpl` into APK**; sherpa loads Piper ONNX as VITS/Matcha/Kitten |
| 44.1k studio TTS | **22.05k** standard for on-device VITS | stable since VITS (2021) | Halves compute; matches `pipeline.py:22050` and benchmark expectations |
| Multi-speaker as default | **Single-speaker fine-tune** as low-resource default | 2023+ (Indic fine-tune reports) | ~3–6 hours of one speaker beats 20h of many speakers for MOS; Nirantar's 433 spk is an ASR asset, not a TTS default |

**New tools/patterns to consider:**
- **Kitten TTS** (sherpa-onnx `v3`): ultra-lightweight alternative to VITS (~10–15 MB) but MOS still being evaluated — not recommended as P2 primary, but worth a P6 benchmark if VITS struggles to hit ≤1s on 2GB.
- **ORT `onnxruntime.transformers.optimizer` for VITS ONNX**: not needed if single `model.onnx` <80MB, but if HiFi-GAN dominates, graph fusion can shave 10–20% like MT's `onnx_bundle_optimize.py:246` (use with caution — VITS flow ops are less fusable).
- **Per-layer mixed precision for TTS**: overkill for P2; keep fp32 first, only quantize if >80MB and A/B listen proves no prosody loss.

**Deprecated/outdated:**
- `android/ml/README.md:15` "Drop these into the Android module" — no, `:ml` is now a real module `com.vachak.ml` with AAR dep; don't copy files ad-hoc.
- `ml/tts/README.md:3` "Piper is GPL-3.0 — handle licensing" still applies but the note "Mundari TTS training" is stale — update to Santali before P2 merge.
- `docs/MODEL_AND_DATA_PROVENANCE.md: Mundari VITS — DEV FIXTURE vits-zh-aishell3` row — will be replaced by Santali VITS row in P2; keep the DEV-FIXTURE row as historical but mark REPLACED.
- `EspeakFallbackTtsAdapter` robotic voice — deprecated for P2 deliverable; keep as `fallback` but never gate acceptance on it (its `SynthAudio` is `isFixture=true` labelled `ESPEAK_NG_FALLBACK_NOT_FINAL_MUNDARI_VOICE`).
</sota_updates>

<open_questions>
## Open Questions

1. **Nirantar CC BY 4.0 verification + speaker consent chain**
   - What we know: `REQUIREMENTS.md:46` lists `Nirantar Santali 13,503 utt / 161h / 433 spk` as `CC BY 4.0 (verify)`. IndicVoices and Rasa are CC BY 4.0 per `MODEL_AND_DATA_PROVENANCE.md:32–33`; Common Voice CC BY 4.0 is solid. No voice consent form is on file per `THIRD_PARTY_NOTICES.md` Mundari scaffolding `DATA ACCESS PENDING`.
   - What's unclear: Whether `adjaysagar/nirantar` on HF carries per-file `LICENSE` metadata (some HF datasets mix CC BY 4.0 + NC). Whether any Nirantar shard requires non-commercial relicensing. Whether the chosen single-speaker's consent covers TTS synthesis (voice cloning opt-in vs ASR transcription consent).
   - Recommendation: Before `02-01` training, run `huggingface_hub` `dataset_info` + `LICENSE` file check, record HF revision hash, and contact the dataset author if `LICENSE` is ambiguous. Create a `ml/tts/data/VOICE_CONSENT.md` template (speaker ID, recording location, date, wav hash, consent text) and get a signed form for the primary speaker before checkpoint publication; record both in `THIRD_PARTY_NOTICES.md` and `MODEL_AND_DATA_PROVENANCE.md` per `REQUIREMENTS.md:TTS-02`.

2. **Single-speaker speaker selection + hours after cleaning**
   - What we know: 19,779 IndicVoices Santali utterances + 13,503 Nirantar = ~33k raw. VITS single-speaker typically needs 3–6 hours clean; many Nirantar utterances are short/fluent and need VAD/trim; Common Voice 533 is supplementary.
   - What's unclear: Exact clean hours for the best single speaker after silence-trim + outlier removal (duration 1–12s, SNR threshold, transcription/Ol Chiki alignment). Whether IndicVoices Santali has speaker IDs or is all one session per file. Whether P2 can hit MOS ≥3.5 with one speaker alone.
   - Recommendation: First action in `02-01` is a **data census spike**: load IndicVoices+Nirantar manifests, group by speaker, report histogram (utt/spk, hours/spk), run `prepare_data` with script + duration filters, emit `ml/tts/runs/census.json`; pick the top-SNR speaker with ≥200 utterances and ≥3 hours as primary; if no speaker meets threshold, aggregate top-2 speakers as multi-speaker fallback and document the switch.

3. **espeak-ng-data inclusion decision**
   - What we know: Current fixture bundles full `espeak-ng-data/:632K` (actually ~3MB unpacked with 100+ `*_dict`) because Chinese VITS used pinyin phonemes. `espeak-ng-data/lang` has no `sat` entry (verified 2026-08-29). Ol Chiki char tokens do not need espeak.
   - What's unclear: Whether any downstream sherpa VITS checkpoint requires `dataDir` non-empty (sherpa's `OfflineTtsVitsModelConfig.dataDir` is required by the Java binding but may accept empty dir for char models). Whether slimming to `inc` subtrees would still be GPL-encumbered.
   - Recommendation: Spike `OfflineTts` load with `dataDir` pointing at empty dir + with char `tokens.txt` and confirm `generate("ᱡᱚᱦᱟᱨ")` succeeds; if sherpa requires a dir, ship an empty placeholder and document in `THIRD_PARTY_NOTICES.md` that no GPL data is bundled; prefer this over shipping full `espeak-ng-data`.

4. **Actual on-device TTS latency at 22k on 2GB arm64**
   - What we know: `docs/benchmarks/BENCHMARK_REPORT.md:TTS First-audio latency ≤1000 ms`, `AGENTS.md: TTS ≤1s`, `SherpaOnnxTtsAdapter.kt:35 numThreads=1` matches sequential-budget pattern; MT INT8 desktop is 16.5 ms (`indictrans2-onnx-export/README.md:212`) but TTS wavegen is heavier.
   - What's unclear: Real-device 22k VITS latency for a 10-word Ol Chiki sentence (lesson scope) on the 2GB target (arm64 NEON) — emulator `x86_64` is not representative; 39M Chinese model on this repo has not been latency-benchmarked under `LatencyTracker`.
   - Recommendation: In `02-02`, run a device `adb shell am instrument` test synthesizing 10 FLN sentences, collect `LatencyTracker` `ttsMs` + `adb logcat -s Vachak-TTS`, report median/p95; if >1s, shorten `input_lengths` (lesson sentences ≤80 tokens) and avoid beam/search — greedy only.

5. **Tokenizer/pack size accounting vs MT tokenizer budget**
   - What we know: `AGENTS.md:Model budget` allocates `Tokenizers/runtime 20–50MB` separate from TTS 20–80MB; MT tokenizer JSON is 23.9 MB (`01-RESEARCH.md:Tokenizer asset size`) but P2's `tokens.txt` is <100KB (char inventory). Sherpa AAR itself is 54 MB but counted in App APK 40–70MB, not TTS slice.
   - What's unclear: Whether `tokens.txt`+`lexicon.txt`+`espeak-ng-data` are counted in TTS 20–80MB or tokenizer/runtime row; governance for double-counting when MT+T TTS both ship tokenizers.
   - Recommendation: In P2 budget gate, define TTS slice as `model.onnx + tokens.txt + lexicon.txt + (slimmed espeak-ng-data if shipped)` only; document sherpa AAR separately; measure with `unzip -l app-debug.apk | grep vachak_models/tts` and include in `BENCHMARK_REPORT.md`.

6. **Rasa CC-BY-4.0 subset — which split is useful for TTS**
   - What we know: `REQUIREMENTS.md:46` lists Rasa Santali subset as CC-BY-4.0 methodology; `docs/PHASES.md:59` calls it "methodology / clean license subset".
   - What's unclear: Whether Rasa contains Santali WAVs suitable for TTS or is primarily an ASR/TTS recipe (like Karya pipeline) with no new speech.
   - Recommendation: Enumerate Rasa HF splits `hf://ai4bharat/Rasa` for `sat` and report row count/hours; if no `sat` WAV, cite it as methodology only and do not count toward 33k TTS hours; adjust provenance note accordingly.

</open_questions>

<sources>
## Sources

### Primary (HIGH confidence)
- `AGENTS.md` — offline, sequential, 500 MB budget (TTS 20–80MB), no IN22, Piper GPL handling, sherpa-onnx stack, Hindi→Santali Ol Chiki pipeline and <3s latency budget
- `.planning/ROADMAP.md:34-46` — Phase 2 goal/deps/requirements/TTS-01,TTS-02 success criteria and plans 02-01/02-02
- `.planning/REQUIREMENTS.md:10-12` — TTS-01 (VITS 20–80MB via sherpa-onnx, 19,779 IndicVoices + 13,503 Nirantar + Rasa, not DEV-FIXTURE) and TTS-02 (consent+license); PACK/PERF/DEMO gates
- `.planning/PROJECT.md:6,39,49` — Santali-only pivot, 2GB/500MB constraints, sherpa-onnx + ONNX Runtime Mobile, IN22 never in train
- `docs/PHASES.md:50-68` — Phase 2 reuse/study table: sherpa Apache-2.0, Coqui MPL-2.0, IndicVoices CC BY 4.0, Nirantar CC BY 4.0 verify, Rasa CC-BY-4.0, Common Voice 533, MunTTS reference only (no Mundari data), espeak lexicon/tokens contract
- `android/ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt:18-53` — proven VITS wiring: `OfflineTtsVitsModelConfig` + `OfflineTtsConfig(numThreads=1)` + `OfflineTts(null, config)` + `generate(text)` + `isFixture=true` fixture label
- `android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt:18-57` — recursive `AssetManager.list()` + `copyTo` + `null` AssetManager pattern handling nested `espeak-ng-data`
- `android/ml/src/main/java/com/vachak/ml/TtsAdapter.kt:9-21` — `interface TtsAdapter` + `SynthAudio(samples:FloatArray, sampleRate, backend, isFixture, warning)` contract
- `android/ml/build.gradle.kts:17` + `android/app/build.gradle.kts:17` — `sherpa-onnx-1.13.0.aar` vendored dep, `minSdk 28`, `arm64-v8a`+`x86_64` ABI strategy
- `android/app/src/main/assets/vachak_models/tts/ (du:59M/39M model.onnx)` + `tokens.txt: sil eos sp` + `lexicon.txt: 一 ^ i1` + `espeak-ng-data/lang` listing — confirms DEV-FIXTURE identity and espeak inventory (no `sat`)
- `ml/tts/pipeline.py:24-98` + `ml/tts/adapter.py:1-113` + `ml/tts/export_onnx.py:17-36` + `ml/tts/evaluate.py:38-61` + `ml/tts/requirements.txt` — scaffold contracts (320M analogy), fallback adapter `is_final_voice=False`, `sample_rate=22050`, `onnx.checker` expectation, `wav_valid/MOS/latency/model_size` eval
- `ml/tts/tests/test_tts.py:1-60` + `ml/tts/data/fixtures/dev_manifest.tsv: Hindi "परीक्षण।"` — offline fixture tests and wrong-script placeholder to replace
- `docs/MODEL_AND_DATA_PROVENANCE.md` + `THIRD_PARTY_NOTICES.md:1-93` — provenance tables, vendored AAR license, Piper GPL-3.0, eSpeak GPL-3.0, Karya BY-NC-SA-FS gating, data-access-pending notes
- `ml/translation/scripts/convert_ct2.py:0x1C50–0x1C7F` — canonical Ol Chiki Unicode range check

### Secondary (MEDIUM confidence — verified against primary)
- `docs/LATENCY_INSTRUMENTATION.md: T0–T4` + `docs/benchmarks/BENCHMARK_REPORT.md: TTS First-audio ≤1000ms` + `android/ml/LatencyTracker.kt:2155` — measurement protocol for P2 gate (<3s sequential)
- `docs/phases/PLAN-P2-mundari-tts.md:22` — 12ms blip / audible gate + pack-path requirement
- `.planning/phases/01-ondevice-mt/01-RESEARCH.md` — MT three-graph/80MB budget pattern, SherpaAssets handling of `.onnx.data` sidecars, offline audit, Ol Chiki validator — directly informs TTS lexical/budget analogies
- `deep-research-report.md` + `IndicTrans2/README.md` — Santali `sat_Olck` as first-class IndicTrans2 target, COILD HIN-SAT 20,603 for MT context

### Tertiary (LOW confidence — needs validation during planning/execution)
- APK-compressed TTS size (`unzip -l` vs raw `du`) and sherpa AAR inclusion accounting
- Whether `sherpa_onnx.OfflineTts` Python build's default `dataDir` must be non-empty for char-token VITS
- Actual arm64 2GB TTS latency at 22k for Santali prosody (need device spike)
- Rasa HF split contents for `sat` (WAV vs recipe only)
</sources>

<metadata>
## Metadata

**Research scope:**
- Core technology: VITS (conditional VAE + flow + duration + HiFi-GAN) → single `model.onnx` via `torch.onnx.export` → `sherpa-onnx OfflineTts` on Android
- Ecosystem: sherpa-onnx (Apache-2.0) AAR 1.13.0, Coqui TTS (MPL-2.0) trainer, MunTTS (reference), ONNX/ORT, espeak-ng-data (GPL-3.0, to avoid), librosa/soundfile, AudioTrack, Room/Compose pack plumbing
- Datasets: IndicVoices Santali 19,779, Nirantar 13,503/161h/433spk, Common Voice 533, Rasa methodology — all CC BY 4.0 per artifact, IN22 never in train
- Patterns: Sherpa `OfflineTtsVitsModelConfig`+`SherpaAssets` wiring, Ol Chiki char tokens, manifest-driven single-speaker fine-tune, pack-path resolution, AudioTrack stream playback
- Pitfalls: script contamination, espeak/GPL bloat, multi-speaker collapse, >80MB, Ol Chiki↔Odia confusion, offline regression, sample-rate mismatch, isFixture leakage

**Confidence breakdown:**
- Standard stack: **HIGH** — sherpa-onnx AAR is vendored and WIRED in `SherpaOnnxTtsAdapter.kt:38`, AGENTS.md mandates it, licenses verified in `THIRD_PARTY_NOTICES.md`
- Architecture: **HIGH** — data flow + `OfflineTtsVitsModelConfig` I/O proven; pack-path idiom is MEDIUM (not yet implemented but trivial vs MT's ORT wiring)
- Dataset/budget: **MEDIUM** — hour counts from HF are HIGH, but per-speaker clean hours need a census spike before committing to single-speaker
- Training: **MEDIUM** — Coqui VITS fine-tune recipe is HIGH for generic Indic, MEDIUM for Santali Ol Chiki specifics (no produced checkpoint to inspect in repo)
- Pitfalls: **HIGH** — six failure modes directly observed in the repo (Chinese lexicon, Hindi fixture manifest, no `sat` espeak dir, `isFixture=true` lock, 39M baseline, no INTERNET permission)
- Code examples: **HIGH** — from `SherpaOnnxTtsAdapter.kt`, `SherpaAssets.kt`, `EngineContracts.kt`, `MainScreen.kt`, `ml/tts/*.py` scaffold
- Latency/size gates: **MEDIUM** — budget fits VITS fp32 class is HIGH, but real-device ≤1s on 2GB arm64 requires a spike

**Research date:** 2026-08-29
**Valid until:** 2026-09-28 (30 days — sherpa-onnx + Coqui VITS are stable; 7 days for Nirantar HF revision / espeak dataDir validation)
</metadata>

<overview>
## Overview

P2 is the voice half of Vachak's offline classroom. The shell is DONE: Compose/Room, vendored `sherpa-onnx-1.13.0.aar`, working `OfflineTts` path that already generates audio (264 samples of Chinese at 16k in the DEV fixture), and a Python scaffold (`ml/tts/`) that mirrors the deploy path but returns `TODO` until licensed Santali speech replaces the synthetic Hindi fixture. The data is the strongest asset: ~33k Santali utterances across CC BY 4.0 sources, with Nirantar supplying 161 hours of diversity that makes a robust single-speaker fine-tune realistic without crowd-collection.

The sister work is MT's ONNX export in `indictrans2-onnx-export/` → valid parity at 100% shows export discipline works; for TTS the export is simpler (one model.onnx, not three graphs) so the discipline is data discipline — curate one clean voice, keep 22.05k consistent, validate Ol Chiki script end-to-end, and wire the adapter to the pack path before claiming audible Santali.

</overview>

<technical_approaches>
## Technical Approaches

### Approach A — Recommended: Single-Speaker VITS via Coqui (Ol Chiki char tokens) → sherpa-onnx
- **Data:** Census IndicVoices+Nirantar by speaker → pick top-SNR Santali speaker (≥3h after trim) whose consent covers TTS; resample 22050 mono; Ol Chiki validate manifests (`0x1C50–0x1C7F`); split train/dev/eval (10% dev).
- **Train:** Coqui `VitsConfig`/`VitsArgs` `sample_rate=22050, hop=256, win=1024, mel=80`, `use_phonemes=false`, warm-start from `ljspeech/vits` if needed; 300–800 epochs, mixed precision, `save_step 2000`; keep single-speaker (no `speaker_embedding`).
- **Export:** `torch.onnx.export` → `model.onnx` (opset 17, dynamic `input/length` axes) + `onnx.checker` + emit `tokens.txt` (Ol Chiki char inventory + `sil eos sp`) + `lexicon.txt` (word→chars) — no espeak `phonemizer`.
- **Validate:** `sherpa_onnx.OfflineTts` Python loads `takes/model.onnx+tokens+lexicon+empty_dataDir` and generates audible WAV; Kotlin `SherpaOnnxTtsAdapter` loads same bundle via `SherpaAssets` (or `packDir`) and `generate("ᱡᱚᱦᱟᱨ")` passes `samples.size > 0.3*sampleRate`.
- **Wire:** `EngineProvider.real(context)` passes `packDir` (fallback to `assets/vachak_models/tts/`) to `SherpaOnnxTtsAdapter`; `EngineContracts.TTSEngine` wraps `:ml TtsAdapter`; `MainScreen.Speak` button calls `synthesize(text,"sat")` and streams via `AudioTrack` at `audio.sampleRate`.
- **Gates:** Model slice 20–80MB (`unzip -l` gate), TTS ≤1s on 2GB (`LatencyTracker` + `Vachak-TTS` log), audible (not blip), Ol Chiki regex gate, offline (no `INTERNET`), provenance (`VOICE_CONSENT.md` + `THIRD_PARTY_NOTICES.md`).
- **Pros:** Minimum APK bloat, avoids GPL, fits budget natively, best MOS per hour for low-resource Indic; aligns with `docs/PHASES.md: VITS via sherpa-onnx` and existing 22k pipeline.
- **Cons:** Requires curated single speaker; if no speaker ≥3h, may need two-speaker fallback.
- **Effort:** 2 plans as in ROADMAP (02-01 train/export, 02-02 wire/verify+provenance) are correctly scoped.

### Approach B — Alternative: Multi-Speaker VITS (conditioned on Nirantar diversity)
- Same as A but adds `speaker_embedding` + `speaker_ids` in manifest, training on top-5 Nirantar speakers (≈8–12h total) to average prosody, hoping diversity improves robustness.
- **Pros:** Uses more Nirantar hours; may help generalization if single-speaker clean hours <3h.
- **Cons:** Harder to make ≥3.5 MOS; speaker leakage at 2GB; adapter must pick a `speaker_id` at `generate()` (sherpa `OfflineTts` VITS config currently has no speaker select — Kitten/XTTS path differs); not the deliverable if a teacher-specific voice is desired.
- **When to use:** Only as contingency if the census shows no single speaker ≥150 utterances.

### Approach C — Alternative: XTTS-v2 / YourTTS zero-shot
- Use `coqui XTTS-v2` with a 6-sec Santali reference clip to synthesize any text zero-shot, exporting via `onnx` or retaining `xtts` as sherpa `OfflineTts` with speaker embedding.
- **Pros:** "Clone any teacher voice" demo potential.
- **Cons:** ~400MB model, needs speaker encoder, outside 20–80MB and ≤1s; not sherpa first-class for arm64 2GB; quality on Ol Chiki without fine-tune is weak; licensing of XTTS weights varies.
- **When to use:** Research spike only (post-P2 polish); not P2's `TTS-01` deliverable.

### Approach D — Not an approach: Piper in APK
- Training may experiment with Piper's checkpoint format, but any path that links `piper1-gpl` native libs or GPL-3.0 `espeak-ng-data` as a runtime dependency into `android/ml` or `android/app` violates `AGENTS.md: Piper GPL-3.0 Handle deliberately` + `REQUIREMENTS.md:TTS-02` (excluded from APK). Documented only to rule out.

</technical_approaches>

<budget_latency_analysis>
## Budget & Latency Analysis

### Storage Budget (AGENTS.md: ~500MB total; TTS slice 20–80MB)

| Component | Budget (AGENTS.md) | P2 TTS Estimate | Notes |
|-----------|-------------------|-----------------|-------|
| App APK (Compose/Room) | 40–70 MB | ~35 MB | Without models; `sherpa-onnx-1.13.0.aar:54M` is compressed to ~25M in APK |
| Hindi ASR | 30–80 MB | ~99 MB (current `asr/:86M+13M`) — P3 to optimize | As-is `asr/` is over budget, but not P2's to fix; note ` sherpa` `whisper-tiny` is a DEV fixture |
| MT (IndicTrans2) | 100–180 MB | ~371 MB INT8 before APK zip per `01-RESEARCH.md` — P1 must address | Main budget risk, not P2 |
| **Santali TTS (P2)** | **20–80 MB** | **~38–60 MB fp32** (35–55M VITS + <1M tokens/lexicon), **~22–42 MB int8 if quantized** | Single `model.onnx` at 22k; char tokens add ~30KB; empty `dataDir` saves 10–15MB vs full `espeak-ng-data` |
| Tokenizers/runtime | 20–50 MB | ~25 MB (sherpa `.so`) | Not counted in TTS slice — define slice as `model.onnx+tokens+lexicon` only |
| Curriculum + Flashcards | 30–80 MB | P4 only | — |
| Safety margin | 30–50 MB | Consumed if MT stays >180 MB | 🔴 MT is the risk; TTS helps by staying lean via no-espeak path |
| **Total** | **~500 MB** | **P2 fp32 single-onnx fits green; no variance needed if espeak dropped** | Amber/Over persists due to MT, but TTS choice does not worsen it |

**Single-int8 VITS ONNX layout (fp32, then int8 optional):**
```
assets/vachak_models/tts/  (or packDir/tts/)
  model.onnx              ~ 38–55 MB fp32 (Chinese fixture is 39M — Santali similar)
  model.onnx (int8)       ~ 22–35 MB if quantized via onnxruntime dynamic per_channel
  tokens.txt              ~ 2–30 KB (Ol Chiki char inventory)
  lexicon.txt             ~ 50–800 KB (vocab→chars, scales with word list)
  espeak-ng-data/         ~ 0 if char tokens (preferred) else 8–15 MB slimmed
  total ≈ 38–56 MB fp32 (preferred gate) — already inside 20–80MB without quant
```

### RAM Budget (PROJECT.md:40 — 2 GB, arm64 device, sequential only)
| Allocation | Estimate | Note |
|------------|----------|------|
| System + app heap | ~400 MB | Android 9 baseline |
| MT ORT session (encoder) | ~150 MB (int8, P1) | Loaded first, can release after encode (P1 research) |
| TTS OfflineTts (VITS) | ~120–220 MB resident (fp32 22k) | Single model.onnx; HiFi-GAN dominates; HiFi-GAN flows are streaming but sherpa loads whole graph |
| Tokenizer / lexicon | ~1–5 MB | Char mapping is tiny vs MT's 24MB SPM |
| Playback buffer | ~2 MB | `AudioTrack` static mode, 2× pcm bytes |
| **Peak sequential (one leg)** | **~550–700 MB** | Fits 2GB; TTS alone fits; parallel ASR+MT+TTS would be ~1.0–1.2GB = pressure but not OOM if freed between legs |
| Parallel (disallowed) | >1.2G | AGENTS.md forbids — enforce via single TTS instance with `@Synchronized` + explicit `release()` between pipeline legs |

### Latency Budget (AGENTS.md:94 — total <3s; TTS ≤1s)
| Stage | Budget | Expected arm64 2GB (22k VITS, numThreads=1) | How to meet |
|-------|--------|-----------------------------------------------|-------------|
| ASR | ≤1.0 s | 0.3–1.0 s (whisper-tiny / VAD, P3) | Not P2 |
| MT Hin→Sat | ≤0.5 s | 0.12–0.38 s (01-RESEARCH) | Not P2 |
| **TTS (Santali VITS)** | **≤1.0 s** | **0.25–0.75 s for 8–12 word Ol Chiki sentence** (22k VITS at greedy, batch=1) | `numThreads=1`, greedy only, lesson sentences ≤80 tokens, no beam, warm load (first `generate` amortizes graph init) |
| **Total sequential** | **<3.0 s** | **~0.9–2.1 s once all legs wired (P6 proof)** | Sequential + `LatencyTracker` marks as `Vachak-Latency` |
| Cold vs warm | — | Cold `ensureLoaded()` + `generate()` = +0.4–0.9 s first call | Preload `OfflineTts` on lesson open (background, sequential) — still respects one-model-at-a-time if MT is released |
| Long sentence | — | 20+ words scales linearly with length (duration predictor) | Cap lesson TTS at ~60 tokens; split paragraphs into two `generate()` calls piped to `AudioTrack` |

**Measurement plan:** `LatencyTracker` (`android/ml/.../LatencyTracker.kt:52`) marks `markTtsBegin()` → `generate()` → `markAudioBegin()`; log tag `Vachak-TTS` (like `it2_ct2_baseline.py:113` for MT) and gate median warm `ttsMs ≤1000` on arm64 2GB device (not emulator) with `isFixture=false`.
</budget_latency_analysis>

<validation_notes>
## Validation Notes

### What Must Be Proven Before Planning Is Considered Sound
1. **Audible gate:** `engine.tts.synthesize("ᱡᱚᱦᱟᱨ", "sat")` returns `EngineResult.Ok(ShortArray)` with `size > sampleRate*0.3` (>300 ms) and `wav_valid==true` via `ml/tts/evaluate.py:38 check_wav_valid`; `adb logcat -s Vachak-TTS` shows `synthesized "ᱡᱚ..." -> N samples @ 22050 Hz` with N>6600. The 12 ms blip class (`docs/phases/PLAN-P2-mundari-tts.md:22`) is a FAIL.
2. **Lexicon/tokens gate:** `tokens.txt` contains `sil eos sp` + at least 10 Ol Chiki codepoints (`0x1C50–0x1C7F`), `lexicon.txt` contains ≥100 lines with Ol Chiki left side; `grep -P "[\x{1C50}-\x{1C7F}]" lexicon.txt | wc -l` >100; no hanzi `一` remains.
3. **Size gate:** TTS slice (`model.onnx + tokens.txt + lexicon.txt + espeak-ng-data if shipped`) ≤80 MB via `unzip -l app-debug.apk | grep vachak_models/tts` and `du -sh`; AAR `.so` is counted in App slice, not TTS.
4. **Latency gate:** On arm64 2GB device, warm `LatencyTracker ttsMs` median ≤1000 ms on 10 FLN Santali sentences; logged under `Vachak-Latency`; `isFixture=false` rows only.
5. **Offline gate:** `AndroidManifest.xml` still has no `INTERNET`/`ACCESS_NETWORK_STATE`; `grep -r "HttpUrl\|OkHttp\|requests\|urllib" android/ml android/app ml/tts` is 0 for runtime path; `gradle assembleDebug --offline` succeeds after model fetch.
6. **Provenance gate:** `THIRD_PARTY_NOTICES.md` + `docs/MODEL_AND_DATA_PROVENANCE.md` list sherpa-onnx Apache-2.0, Coqui MPL-2.0 (build-time), IndicVoices/Nirantar/Rasa/Common Voice CC BY 4.0 with HF revisions, and `VOICE_CONSENT.md` for the TTS speaker before PR merges.
7. **Script gate:** Every Santali manifest line and `synthesize` input contains ≥1 `U+1C50–U+1C7F` Ol Chiki codepoint; `OlChikiValidator.containsOlChiki` unit test passes; `MockTranslationEngine` still ships for `mock()` but is not used in `real()` TTS path.

### Validated vs Unvalidated
| Area | Validated (use with confidence) | Unvalidated (spike required in Plan 02-01/02-02) |
|------|---------------------------------|--------------------------------------------------|
| Runtime wiring | `SherpaOnnxTtsAdapter.kt:38 OfflineTts(null, config)` + `SherpaAssets.prepare` + `numThreads=1` + AAR 1.13.0 vendored | Pack-path resolution (fallback vs active `packDir`) — trivial but needs one `resolveDir()` test |
| Tokens/lexicon contract | `tokens.txt` `sil 0 / eos 1 / sp 2` header + `lexicon.txt` `word → phonemes` rows (Chinese fixture proves format) | Whether `dataDir` may be empty for Ol Chiki char tokens (sherpa binding requirement) — empty-dir spike |
| Export contract | `onnx.checker.check_model` in `ml/tts/export_onnx.py:36` + `verify_onnx` | Santali VITS `model.onnx` actually loads in both Python sherpa and Kotlin `OfflineTts` with same `sampleRate` |
| Data census | Dataset hour counts (19,779 / 13,503 / 533) per REQUIREMENTS | Per-speaker clean hours + SNR after trim (census spike required) |
| Size fit | VITS fp32 class 39M per existing `tts/model.onnx`; char tokens add <1MB | Real Santali fine-tune size (larger vocab? HiFi-GAN layers?) and int8 prosody tradeoff |
| Latency | Budget fits VITS on arm64 qualitatively (≤1s for short sentences) | Real-device 22k median TTS for Santali lessons on 2GB target (need warm-run report) |
| Provenance | `THIRD_PARTY_NOTICES.md` pattern + `VOICE_CONSENT.md` template | Nirantar per-file CC BY 4.0 audit + Rasa split contents for `sat` |

### Evaluation Hygiene
- Never train on IN22-Gen/Conv; keep TTS eval prompts separate (lesson Ol Chiki sentences, not IN22). Hold out 10% of curated Santali manifests for dev eval and report `mel loss / MOS proxy / wav_valid` deltas, not just training loss.
- Never ship `MACHINE_TRANSLATED` curriculum content as approved pedagogy (`AGENTS.md:18`) — TTS evaluation prompts may include MT-produced Santali strings, but flag them as `MACHINE_TRANSLATED` and require a Santali-literate reviewer to approve any prompt used as a demo lesson.
- Keep eval splits separate for MOS: human raters (native Santali speakers) rate audibility on the held-out speaker's prompts; do not fabricate MOS in `ml/tts/evaluate.py:VoiceEvalReport`.
</validation_notes>

---

*Phase: 02-mundari-tts (Santali Voice)*
*Research completed: 2026-08-29*
*Ready for planning: yes (proceed to PLAN with mandatory census spike in 02-01 and pack-path + audible + Ol Chiki gates in 02-02)*

