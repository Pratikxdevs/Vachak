# SIH26042 --- Backend, Content Pipeline & System Architecture

## 1. Backend philosophy

The deployed classroom application does **not require a live backend**.

The "backend" exists as a development/control plane for:

-   preparing curriculum
-   validating translations
-   training models
-   building language packs
-   packaging content
-   signing/versioning packages
-   optionally serving packages during initial synchronization

Runtime classroom inference is local.

``` text
                DEVELOPMENT / CONTENT PLANE
                         │
       ┌─────────────────┼──────────────────┐
       │                 │                  │
   Curriculum        Model training     Asset pipeline
       │                 │                  │
       └─────────────────┼──────────────────┘
                         ↓
                 Language Pack Builder
                         ↓
                 Signed Content Pack
                         ↓
             USB / local LAN / initial sync
                         ↓
                    ANDROID TABLET
                         │
                  NO NETWORK NEEDED
```

------------------------------------------------------------------------

# 2. Backend components

## A. Content Authoring Service

Purpose:

-   manage lessons
-   manage translations
-   map NIPUN outcomes
-   review Santali text
-   attach flashcards
-   attach worksheet templates
-   publish language packs

Suggested stack:

-   Python
-   FastAPI
-   PostgreSQL for team/server deployment
-   object storage/local filesystem for assets
-   Pydantic schemas

This service is not required on the tablet.

## B. Model Build Pipeline

Purpose:

-   download base models
-   fine-tune MT
-   train TTS
-   quantize
-   export ONNX/CT2
-   run benchmarks
-   generate checksums
-   produce language pack

Suggested stack:

-   Python
-   PyTorch
-   Hugging Face Transformers where applicable
-   IndicTrans2 training pipeline
-   CTranslate2
-   ONNX
-   sherpa-onnx
-   Docker

## C. Package Builder

Input:

``` text
model artifacts
tokenizers
curriculum
flashcards
audio
metadata
```

Output:

``` text
santali-v1.0.0.pack
manifest.json
sha256.json
```

## D. Optional Distribution Server

Only for initial sync.

``` text
GET /packs
GET /packs/{pack_id}
GET /packs/{pack_id}/manifest
GET /packs/{pack_id}/artifact/{artifact_id}
```

The tablet can download a pack when internet exists.

The classroom application must never depend on this service.

------------------------------------------------------------------------

# 3. Production architecture

``` text
                         ┌────────────────────┐
                         │ Content Authoring   │
                         │ FastAPI             │
                         └─────────┬──────────┘
                                   │
                         ┌─────────▼──────────┐
                         │ PostgreSQL          │
                         │ lessons/outcomes    │
                         └─────────┬──────────┘
                                   │
                         ┌─────────▼──────────┐
                         │ Asset/Object Store  │
                         │ images/audio/models │
                         └─────────┬──────────┘
                                   │
                         ┌─────────▼──────────┐
                         │ Pack Builder        │
                         │ manifest + hashes   │
                         └─────────┬──────────┘
                                   │
                       HTTPS / USB / Local LAN
                                   │
                         ┌─────────▼──────────┐
                         │ Android Tablet      │
                         │ Room + Assets       │
                         └─────────────────────┘
```

------------------------------------------------------------------------

# 4. Tablet architecture

``` text
com.sih26042.app

presentation/
  home/
  lessons/
  live/
  worksheets/
  flashcards/
  settings/

domain/
  lesson/
  translation/
  speech/
  worksheet/
  flashcard/
  languagepack/

data/
  room/
  assets/
  packages/
  repository/

ml/
  asr/
  translation/
  tts/
  runtime/

sync/
  manifest/
  downloader/
  verifier/
  installer/

diagnostics/
  latency/
  memory/
  modelstatus/
  offline/
```

------------------------------------------------------------------------

# 5. Runtime data flow

## Text translation

``` text
Teacher selects lesson
        ↓
Room
        ↓
Hindi lesson text
        ↓
TranslationService
        ↓
Local MT runtime
        ↓
Santali text
        ↓
Room/cache
        ↓
UI
```

For packaged curriculum, translation should normally be precomputed.

Do not waste tablet compute translating static content repeatedly.

------------------------------------------------------------------------

# 6. Live voice flow

``` text
Microphone
    ↓
AudioRecorder
    ↓
Hindi ASR
    ↓
recognized Hindi
    ↓
Normalizer
    ↓
IndicTrans2
    ↓
Santali Ol Chiki
    ↓
Santali TTS
    ↓
AudioTrack
```

Only one major model should be active at a time if required by the
device's RAM profile.

Example:

``` text
ASR loaded
→ recognize
→ release ASR resources

MT loaded
→ translate
→ release MT resources

TTS loaded
→ synthesize
→ release TTS resources
```

A warm-cache mode can be added only if memory profiling proves it safe.

------------------------------------------------------------------------

# 7. Content database

SQLite/Room schema.

## `lessons`

``` text
id
grade
subject
unit
title_hi
title_sat
description_hi
description_sat
outcome_ids
version
status
```

## `lesson_items`

``` text
id
lesson_id
type
order_index
text_hi
text_sat
audio_asset
worksheet_template
```

Types:

-   SCRIPT
-   ACTIVITY
-   ASSESSMENT
-   VOCABULARY
-   STORY
-   INSTRUCTION

## `learning_outcomes`

``` text
id
framework
grade
domain
competency
description
source_document
```

## `flashcards`

``` text
id
lesson_id
image_asset
text_hi
text_sat
audio_asset
outcome_id
```

## `packages`

``` text
id
language
version
created_at
sha256
size
status
```

------------------------------------------------------------------------

# 8. Content package format

Recommended structure:

``` text
santali-v1.0.0/
│
├── manifest.json
├── lessons.db
│
├── models/
│   ├── asr/
│   ├── mt/
│   └── tts/
│
├── tokenizers/
│
├── assets/
│   ├── flashcards/
│   ├── worksheet/
│   └── icons/
│
└── audio/
    └── lessons/
```

Archive:

`.zip` or a custom package container.

The Android installer:

1.  verifies manifest
2.  verifies SHA-256
3.  verifies compatibility
4.  stages files
5.  atomically activates package
6.  records version in Room

------------------------------------------------------------------------

# 9. Manifest

Example:

``` json
{
  "package_id": "sat-fln",
  "version": "1.0.0",
  "min_android": 28,
  "language": "sat",
  "script": "Ol_Chiki",
  "size_bytes": 398000000,
  "artifacts": [
    {
      "id": "asr-hi",
      "type": "asr",
      "runtime": "onnx",
      "sha256": "..."
    },
    {
      "id": "mt-hi-sat",
      "type": "translation",
      "runtime": "onnx",
      "sha256": "..."
    },
    {
      "id": "tts-sat",
      "type": "tts",
      "runtime": "sherpa-onnx",
      "sha256": "..."
    }
  ]
}
```

------------------------------------------------------------------------

# 10. Security model

No user authentication is required.

Security is package integrity, not identity.

Use:

-   HTTPS for initial distribution.
-   SHA-256 for corruption detection.
-   signed manifests for production.
-   atomic package activation.
-   immutable model version IDs.

Never execute downloaded code.

Only accept known artifact types.

------------------------------------------------------------------------

# 11. Offline guarantees

At runtime:

``` text
Network permission:
  not needed for inference

DNS:
  not used

HTTP:
  not used

Cloud APIs:
  not used

Analytics:
  disabled by default

Remote configuration:
  not used
```

The application should remain functional with:

-   Wi-Fi disabled
-   mobile data disabled
-   airplane mode enabled

------------------------------------------------------------------------

# 12. Sync modes

## Mode A --- bundled

Best for SIH demo.

The APK or accompanying package contains everything.

Advantages:

-   deterministic demo
-   no server dependency
-   easy offline proof

## Mode B --- initial download

Teacher/tablet connects once.

``` text
server
 ↓
manifest
 ↓
language pack
 ↓
verify
 ↓
install
```

Then the app becomes offline.

## Mode C --- USB

Recommended for government deployment.

``` text
Laptop/USB
 ↓
content package
 ↓
tablet installer
 ↓
verification
 ↓
activation
```

This is particularly suitable for low-connectivity schools.

------------------------------------------------------------------------

# 13. Content publishing pipeline

``` text
Source material
      ↓
OCR/text extraction
      ↓
Hindi normalization
      ↓
Sentence segmentation
      ↓
Machine translation
      ↓
Native reviewer
      ↓
Educational reviewer
      ↓
NIPUN mapping
      ↓
Lesson assembly
      ↓
Worksheet/flashcard assets
      ↓
QA
      ↓
Pack builder
      ↓
Release
```

------------------------------------------------------------------------

# 14. Translation QA workflow

Every generated translation has:

``` text
machine_translation
review_status
reviewer_id
reviewed_text
review_notes
```

Statuses:

``` text
DRAFT
MACHINE_TRANSLATED
LINGUIST_REVIEW
PEDAGOGY_REVIEW
APPROVED
REJECTED
```

Never ship `MACHINE_TRANSLATED` curriculum content as approved pedagogy.

Live conversational translation is different: it is model output and
should be visibly presented as generated translation.

------------------------------------------------------------------------

# 15. Training pipeline

## Translation

``` text
COILD HIN-SAT
        +
Education_v2 HIN-SAT
        +
curated FLN corpus
        ↓
clean
        ↓
deduplicate
        ↓
normalize
        ↓
split
        ↓
fine-tune IndicTrans2
        ↓
evaluate
        ↓
export
        ↓
quantize
        ↓
mobile benchmark
```

Training data sources:

-   COILD-MT-Corpus HIN-SAT: 20,603 sentence pairs.
-   Education_v2 HIN-SAT.
-   curated Jharkhand/FLN translations.
-   optionally other licensed Santali data for vocabulary/domain
    adaptation.

------------------------------------------------------------------------

# 16. TTS training pipeline

``` text
IndicVoices Santali
       +
Common Voice Santali
       ↓
speaker filtering
       ↓
audio quality filtering
       ↓
text normalization
       ↓
Ol Chiki normalization
       ↓
train/validation split
       ↓
compact VITS/Piper-compatible training
       ↓
export
       ↓
ONNX
       ↓
sherpa-onnx
       ↓
Android benchmark
```

The current Quipus Santali model can be used as a quality reference, but
its \~1.24 GB artifact is not suitable for the tablet.

------------------------------------------------------------------------

# 17. ASR training/selection pipeline

Start with pretrained models.

``` text
IndicConformer Hindi
        VS
Whisper tiny
        VS
Vosk Hindi
        ↓
same classroom test set
        ↓
WER + latency + RAM
        ↓
winner
```

Do not train Hindi ASR unless the existing models fail the classroom
test.

For Santali reverse ASR, use IndicConformer Santali as a later
extension.

------------------------------------------------------------------------

# 18. Model conversion pipeline

``` text
HF/Fairseq checkpoint
        ↓
fine-tuned model
        ↓
export
        ↓
ONNX / CT2
        ↓
quantization
        ↓
correctness test
        ↓
latency test
        ↓
RAM test
        ↓
package
```

Every quantized model must be compared against its FP32/FP16 reference.

Acceptance test:

-   no catastrophic translation regressions
-   no major TTS intelligibility regression
-   ASR WER within predefined tolerance
-   package within budget

------------------------------------------------------------------------

# 19. Benchmark harness

Create:

`benchmarks/`

with:

``` text
translation_benchmark.py
asr_benchmark.py
tts_benchmark.py
android_benchmark/
```

Record:

``` text
model
version
device
android_version
ram
input_length
latency_ms
peak_ram_mb
output_quality
package_size_mb
```

The SIH repository should include benchmark CSV/JSON results.

------------------------------------------------------------------------

# 20. Test device requirements

At least one actual device:

-   Android 9+
-   2 GB RAM
-   ARM64
-   low-cost tablet CPU

Testing on a laptop emulator is insufficient.

The final demo device must be the same class of hardware used for
performance claims.

------------------------------------------------------------------------

# 21. API surface

The Android app should expose internal interfaces, not network APIs.

``` kotlin
interface TranslationEngine {
    suspend fun translate(
        text: String,
        source: String,
        target: String
    ): TranslationResult
}

interface SpeechRecognizer {
    suspend fun recognize(audio: AudioInput): SpeechResult
}

interface SpeechSynthesizer {
    suspend fun synthesize(
        text: String,
        language: String
    ): AudioResult
}

interface LanguagePack {
    val languageCode: String
    val script: String
    val asr: ModelRef?
    val mt: ModelRef
    val tts: ModelRef
}
```

This is what enables Ho/Mundari later without changing the application
architecture.

------------------------------------------------------------------------

# 22. No-backend runtime principle

The tablet should never have code like:

``` text
POST /translate
POST /speech-to-text
POST /tts
```

Instead:

``` text
TranslationEngine
    → local model

SpeechRecognizer
    → local model

SpeechSynthesizer
    → local model
```

Network access exists only inside the optional package downloader.

------------------------------------------------------------------------

# 23. Repository layout

``` text
sih26042/
│
├── android/
│   ├── app/
│   ├── core/
│   ├── ml/
│   ├── content/
│   └── sync/
│
├── ml/
│   ├── translation/
│   ├── asr/
│   └── tts/
│
├── datasets/
│   ├── manifests/
│   ├── preprocessing/
│   ├── splits/
│   └── LICENSES.md
│
├── curriculum/
│   ├── raw/
│   ├── reviewed/
│   ├── lessons/
│   ├── outcomes/
│   ├── worksheets/
│   └── flashcards/
│
├── packages/
│   └── builder/
│
├── benchmarks/
│
├── docs/
│
├── THIRD_PARTY_NOTICES.md
├── MODEL_CARD.md
├── DATA_CARD.md
├── product.md
└── backend.md
```

------------------------------------------------------------------------

# 24. External repositories to study/build around

## Primary

1.  IndicTrans2 https://github.com/AI4Bharat/IndicTrans2

2.  IndicConformerASR https://github.com/AI4Bharat/IndicConformerASR

3.  sherpa-onnx https://github.com/k2-fsa/sherpa-onnx

4.  ONNX Runtime https://github.com/microsoft/onnxruntime

## ASR alternatives

5.  Vosk https://github.com/alphacep/vosk-api

6.  whisper.cpp https://github.com/ggerganov/whisper.cpp

## TTS

7.  Piper https://github.com/OHF-Voice/piper1-gpl

8.  Coqui TTS https://github.com/coqui-ai/TTS

## Study/reference only

9.  Uktam https://github.com/Uktam-ai/uktam

Uktam demonstrates offline Indic voice AI on Android, but its current
hardware requirements/model footprint do not match our 2 GB/\~500 MB
target. Do not fork it as the application base.

------------------------------------------------------------------------

# 25. Verified datasets

## A. COILD-MT-Corpus

URL: https://huggingface.co/datasets/ainlpml-iitp/COILD-MT-Corpus

Verified facts:

-   Hindi--Santali pair exists.
-   20,603 HIN-SAT sentence pairs.
-   CC BY 4.0.
-   Sentence-aligned text.

Use: - primary general Hindi→Santali fine-tuning data.

## B. Education_v2

URL: https://huggingface.co/datasets/coild-aikosh/Education_v2

Verified facts:

-   Hindi→Santali education pair exists.
-   education-domain corpus.
-   source-reviewed translations.
-   CC BY 4.0.

Use: - highest-priority domain adaptation data.

## C. Agriculture_v2

URL: https://huggingface.co/datasets/coild-aikosh/Agriculture_v2

Verified facts:

-   Hindi→Santali pair exists.
-   manually translated/source-reviewed domain data.
-   CC BY 4.0.

Use: - supplementary vocabulary/domain transfer only.

## D. IndicVoices

URL: https://huggingface.co/datasets/ai4bharat/IndicVoices

Verified facts:

-   Santali configuration exists.
-   audio + text.
-   CC BY 4.0.
-   train and validation splits exist.

Use: - Santali TTS/ASR research.

## E. Common Voice Santali

Official: https://commonvoice.mozilla.org/

Use: - additional Santali speech/ASR data. - verify exact version and
current dataset terms before incorporating into released model.

## F. IndicTrans2 BPCC

URL: https://huggingface.co/datasets/ai4bharat/BPCC

Use: - general translation training/reference. - artifact licenses vary;
preserve provenance.

## G. IN22-Gen / IN22-Conv

URLs: https://huggingface.co/datasets/ai4bharat/IN22-Gen
https://huggingface.co/datasets/ai4bharat/IN22-Conv

Use: - evaluation/benchmarking.

Do not train on test data.

------------------------------------------------------------------------

# 26. Official pedagogy sources

NIPUN Bharat portal: https://nipunbharat.education.gov.in/

NIPUN Bharat Guidelines:
https://www.education.gov.in/sites/upload_files/mhrd/files/nipun_bharat_eng1.pdf

NIPUN planning template:
https://nipunbharat.education.gov.in/PlanTemplate.aspx

Jharkhand Education Project Council quality education page:
https://jepc.jharkhand.gov.in/program/quality-education

These are the sources to use when constructing the competency/outcome
mapping and demonstrating alignment with the problem context.

------------------------------------------------------------------------

# 27. Data governance

For every dataset:

``` text
dataset_id
source_url
version
license
retrieval_date
language
script
domain
speaker/consent metadata if applicable
preprocessing
derived_dataset
redistribution_allowed
```

Never mix datasets without retaining provenance.

------------------------------------------------------------------------

# 28. Model cards

Every released model needs:

``` text
model_id
base_model
training_datasets
dataset_licenses
fine_tuning_method
quantization
parameter_count
model_size
supported_language
supported_script
limitations
evaluation
hardware
latency
license
```

------------------------------------------------------------------------

# 29. Operational package lifecycle

``` text
DRAFT
  ↓
QA
  ↓
SIGNED
  ↓
PUBLISHED
  ↓
DOWNLOADED
  ↓
HASH VERIFIED
  ↓
INSTALLED
  ↓
ACTIVE
```

Rollback:

``` text
active v1.1
   ↓
failure detected
   ↓
activate v1.0
```

No destructive update.

------------------------------------------------------------------------

# 30. SIH deployment story

The architecture should be presented as:

``` text
CENTRAL CONTENT / MODEL LAB
          │
          │ initial sync / USB
          ▼
LOW-COST TABLET
          │
          ├── local ASR
          ├── local translation
          ├── local TTS
          ├── local curriculum
          ├── local worksheets
          └── local flashcards
```

This directly addresses the stated connectivity constraint.

------------------------------------------------------------------------

# 31. Final architecture decision

## Device

Kotlin + Jetpack Compose.

## Database

Room/SQLite.

## ASR

Benchmark: - IndicConformer - Whisper.cpp tiny - Vosk

Select the smallest acceptable model.

## Translation

IndicTrans2 distilled 320M as the starting point.

Fine-tune on: - HIN-SAT COILD - Education_v2 - curated FLN corpus.

Then optimize/quantize.

## TTS

Use the current Quipus Santali model as a **quality/reference model
only** because its \~1.24 GB artifact is incompatible with the device
budget.

Train/distill a compact Santali voice and deploy through
sherpa-onnx/VITS-compatible inference.

## Runtime

sherpa-onnx + ONNX Runtime where appropriate.

## Content

Pretranslated lesson packages + deterministic worksheet templates +
prebuilt flashcards.

## Backend

Optional FastAPI content/model distribution plane.

Never required for classroom inference.

------------------------------------------------------------------------

# 32. The one architectural rule

**The tablet must be able to lose the internet permanently after
synchronization and still perform the complete core classroom
workflow.**

If removing the SIM/Wi-Fi breaks:

-   speech recognition,
-   translation,
-   TTS,
-   lessons,
-   worksheets,
-   flashcards,

the MVP fails the core product requirement.
