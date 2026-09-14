# SIH26042 --- Vernacular Pedagogy & Real-Time Translation

## MVP Product & System Master Specification

**Competition target:** Smart India Hackathon (SIH26042)\
**MVP language:** Hindi → Santali (Ol Chiki)\
**Target device:** Android 9+, 2 GB RAM, low-cost tablet\
**Storage budget:** \~500 MB total app + models + core content\
**Runtime:** Fully offline after initial content synchronization\
**Primary objective:** Deliver a reliable, judge-demoable classroom
bridge rather than a general-purpose translator.

------------------------------------------------------------------------

# 1. Product thesis

A Hindi-medium teacher should be able to conduct foundational literacy
and numeracy instruction for Santali-speaking children without learning
Santali and without an internet connection.

The MVP is deliberately narrow:

> **Teacher speaks Hindi → tablet recognizes Hindi → translates to
> Santali in Ol Chiki → synthesizes Santali speech → student hears it.**

The same content engine turns FLN lessons into bilingual worksheets and
visual flashcards.

The system is not a chatbot, ERP, social platform, or cloud translation
service.

------------------------------------------------------------------------

# 2. Problem-to-product mapping

  -----------------------------------------------------------------------
  SIH requirement                     MVP implementation
  ----------------------------------- -----------------------------------
  Hindi → tribal-language translation IndicTrans2 Hindi (`hin_Deva`) →
                                      Santali (`sat_Olck`)

  Curriculum translation              Curated FLN
                                      lesson/activity/assessment corpus

  Voice-to-voice                      Push-to-talk ASR → MT → TTS

  \<3 seconds                         Stage-level latency instrumentation
                                      and optimization

  Offline                             All inference local; no runtime
                                      network

  2 GB RAM                            Sequential model loading; unload
                                      ASR before TTS where possible

  \~500 MB                            Distilled/quantized models +
                                      curated content

  Worksheets                          Deterministic templates

  Flashcards                          Prebuilt image/text assets

  NIPUN alignment                     Outcome IDs stored as metadata

  Initial sync                        Signed content/model manifest

  Extensibility                       LanguagePack interface; Ho/Mundari
                                      are future packs
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 3. Scope

## Must ship

1.  Hindi → Santali text translation.
2.  Hindi speech → Santali speech.
3.  Offline operation.
4.  Bilingual worksheet generation.
5.  Visual bilingual flashcards.
6.  Lesson/content browser.
7.  Audio playback.
8.  Offline Hindi ASR.
9.  Content/model package installation.
10. Visible latency/offline diagnostics for demo and QA.

## Expected supporting features

-   One-time content synchronization.
-   Lesson browser by grade/subject/unit.
-   Local lesson history.
-   Translation text display before playback.
-   Retry/edit/re-speak.
-   Model/version information.

## Stretch only after core is stable

-   Santali → Hindi reverse conversation.
-   Ho/Mundari language packs.
-   Teacher usage dashboard.
-   Student pronunciation practice.

## Explicitly out of scope

-   Login/authentication.
-   Cloud inference.
-   Runtime API calls.
-   Student accounts.
-   Multi-user collaboration.
-   General chatbot.
-   School ERP.
-   Attendance.
-   Parent app.
-   AI-generated worksheets.
-   Real-time cloud sync.
-   Large LLM.

------------------------------------------------------------------------

# 4. User journey

## Journey A --- Lesson

Teacher opens:

`Grade 1 → Mathematics → Counting → 1–10`

The lesson contains:

-   learning outcome
-   teacher script
-   activity instructions
-   assessment prompts
-   Hindi text
-   Santali/Ol Chiki translation
-   audio playback
-   flashcards
-   worksheet

## Journey B --- Live translation

1.  Tap **Speak**.
2.  Teacher speaks Hindi.
3.  Recording stops on button release.
4.  Hindi ASR runs locally.
5.  Translation runs locally.
6.  Santali TTS runs locally.
7.  Santali audio plays.
8.  UI shows:
    -   recognized Hindi
    -   Santali translation
    -   total latency
    -   OFFLINE badge

The pipeline is sequential by design.

`ASR → MT → TTS`

Do not keep all large models resident if memory pressure is excessive.

------------------------------------------------------------------------

# 5. Recommended technical stack

## Android

-   Kotlin
-   Jetpack Compose
-   Android Room
-   Android AssetManager
-   WorkManager only for non-inference package installation/maintenance
-   minSdk 28 / Android 9 target
-   arm64-v8a first

## ML

-   ONNX Runtime / ONNX Runtime Mobile where appropriate
-   sherpa-onnx for ASR/TTS integration where its supported model format
    is advantageous
-   CTranslate2 as the translation optimization benchmark
-   SentencePiece / IndicTrans tokenizer components

## Data

-   SQLite/Room
-   JSON content manifests
-   local binary model packages
-   WebP/JPEG flashcard assets
-   WAV/PCM only where pre-generated lesson audio is used

## Packaging

-   APK/AAB for application
-   separate language/content package for development and USB
    installation
-   production demo can bundle a verified package into the APK if size
    permits

------------------------------------------------------------------------

# 6. Model plan

## 6.1 Translation --- primary

### Base model

**AI4Bharat IndicTrans2 Indic-Indic distilled 320M**

Language pair:

`hin_Deva → sat_Olck`

IndicTrans2 explicitly supports Santali in Ol Chiki and Hindi, and
publishes a distilled Indic-Indic 320M checkpoint plus
training/fine-tuning and CTranslate2 inference tooling.

Repository: https://github.com/AI4Bharat/IndicTrans2

Model:
https://huggingface.co/ai4bharat/indictrans2-indic-indic-dist-320M

### Training strategy

Do NOT train from scratch.

Stage 1: - benchmark pretrained model.

Stage 2: - fine-tune on education-domain Hindi→Santali data.

Stage 3: - add a small high-quality classroom corpus.

Stage 4: - export/convert to efficient inference format.

Stage 5: - INT8 benchmark first. - INT4 only if quality/runtime/storage
justify it.

### Translation dataset stack

1.  COILD-MT-Corpus:
    -   HIN-SAT: 20,603 sentence pairs.
    -   CC BY 4.0.
    -   https://huggingface.co/datasets/ainlpml-iitp/COILD-MT-Corpus
2.  Education_v2:
    -   Hindi→Santali education-domain parallel corpus.
    -   Source-reviewed data.
    -   CC BY 4.0.
    -   https://huggingface.co/datasets/coild-aikosh/Education_v2
3.  Agriculture_v2:
    -   Hindi→Santali domain data.
    -   CC BY 4.0.
    -   Useful only for vocabulary transfer, not as core FLN data.
    -   https://huggingface.co/datasets/coild-aikosh/Agriculture_v2
4.  IndicTrans2 BPCC:
    -   general multilingual pretraining/benchmark resource.
    -   BPCC artifacts have different licenses; inspect artifact-level
        license before redistribution.
    -   https://huggingface.co/datasets/ai4bharat/BPCC
5.  IN22-Gen / IN22-Conv:
    -   evaluation, not training.
    -   Santali and Hindi included.
    -   https://huggingface.co/datasets/ai4bharat/IN22-Gen
    -   https://huggingface.co/datasets/ai4bharat/IN22-Conv

### Critical training rule

The final training split must be separated from evaluation.

Never train on IN22 test sentences.

------------------------------------------------------------------------

# 7. Translation domain dataset we create

Create:

`SIH-FLN-HI-SAT`

Target initial size:

-   2,000--5,000 high-quality sentence pairs.
-   500 held-out evaluation pairs.
-   100 adversarial/error-analysis pairs.

Categories:

-   classroom commands
-   counting
-   number comparison
-   addition/subtraction
-   shapes
-   measurement
-   colours
-   alphabet/phonological awareness
-   vocabulary
-   stories/rhymes
-   assessment prompts
-   teacher instructions
-   student responses

Every target sentence should be reviewed by a fluent Santali speaker.

Record:

-   source Hindi
-   target Ol Chiki
-   grade
-   subject
-   lesson
-   NIPUN outcome ID
-   sentence type
-   reviewer
-   review status
-   terminology tags

------------------------------------------------------------------------

# 8. ASR plan

## Primary benchmark

AI4Bharat IndicConformerASR.

It provides Hindi and Santali checkpoints and is MIT licensed.

Repository: https://github.com/AI4Bharat/IndicConformerASR

However, the public flagship model is too large for our 500 MB total
storage target. Do not ship the large checkpoint without a
smaller/quantized model being proven.

## Benchmarks

### Candidate A

IndicConformer Hindi --- smallest practical checkpoint.

### Candidate B

Whisper.cpp tiny/base, quantized.

Repository: https://github.com/ggerganov/whisper.cpp

### Candidate C

Vosk Hindi.

Repository: https://github.com/alphacep/vosk-api

Vosk is Apache-2.0 and has Android/Kotlin integration.

### Selection rule

Choose the smallest model that passes:

-   classroom Hindi WER target
-   robust recognition of short commands
-   acceptable CPU latency
-   acceptable RAM
-   total package budget

Do not select based on benchmark WER alone.

------------------------------------------------------------------------

# 9. TTS plan

## New finding: Santali TTS already exists

A current open model, `hyperneuronAILabs/quipus-0.6-speechv2`,
explicitly includes Santali and provides male/female Santali voices.

Model: https://huggingface.co/hyperneuronAILabs/quipus-0.6-speechv2

License: MIT.

Important limitation:

-   0.6B parameters.
-   Repository/model files are \~1.24 GB.
-   Therefore it **cannot be shipped as-is** under our \~500 MB total
    application budget.
-   Its reported speed figures are not Android/2 GB measurements.

Use it as:

1.  a quality reference;
2.  a source for validating Santali pronunciation/voice quality;
3.  a teacher-demo fallback during server-side research;
4.  a possible teacher-model for distillation.

Do not put the raw Quipus model into the tablet MVP.

## Final on-device TTS

Preferred architecture:

`small VITS/Piper-compatible Santali model → sherpa-onnx`

sherpa-onnx supports offline Android TTS and VITS/Piper model families.

Repository: https://github.com/k2-fsa/sherpa-onnx

Piper successor: https://github.com/OHF-Voice/piper1-gpl

Piper is GPL-3.0, so licensing must be handled deliberately.

### TTS training data

Primary:

AI4Bharat IndicVoices Santali subset.

Dataset: https://huggingface.co/datasets/ai4bharat/IndicVoices

License: CC BY 4.0.

The Santali configuration contains audio + text and a train/validation
split.

A published analysis reports 19,779 Santali IndicVoices training samples
and 249 validation samples, with additional Common Voice data.

Secondary: Mozilla Common Voice Santali.

Use the official Common Voice distribution where possible:
https://commonvoice.mozilla.org/

Common Voice is useful for ASR and can provide additional Santali
speech/text, but dataset version and exact license/terms must be
recorded in the final model card.

### TTS training objective

Train/fine-tune a compact Santali VITS/Piper-style voice.

Target:

-   1--2 speakers initially.
-   clean Ol Chiki text.
-   16/22.05 kHz depending on chosen architecture.
-   model target \<50--80 MB if feasible.
-   CPU inference on the target tablet.

------------------------------------------------------------------------

# 10. Voice pipeline

``` text
Teacher microphone
        ↓
Push-to-talk recorder
        ↓
Hindi ASR
        ↓
Text normalization
        ↓
Terminology normalization
        ↓
IndicTrans2 Hindi → Santali
        ↓
Post-processing / Ol Chiki validation
        ↓
Santali TTS
        ↓
Android AudioTrack
```

No parallel model execution.

No cloud calls.

No LLM.

------------------------------------------------------------------------

# 11. Latency budget

Target:

`< 3.0 seconds`

Suggested engineering budget:

  Stage                           Target
  ------------------------ -------------
  Recording/finalization     100--300 ms
  Hindi ASR                      ≤ 1.0 s
  Translation                    ≤ 0.5 s
  TTS                            ≤ 1.0 s
  Audio startup/buffer           ≤ 0.2 s
  Total                          ≤ 3.0 s

The actual accepted limits must be determined from testing on the chosen
2 GB tablet.

The UI must log:

`ASR ms + MT ms + TTS ms + total ms`

------------------------------------------------------------------------

# 12. Offline architecture

The app must operate with:

`Wi-Fi OFF + mobile data OFF`

after installation/content sync.

Runtime network calls are prohibited.

All runtime dependencies must be local:

-   models
-   tokenizer
-   language metadata
-   lessons
-   worksheets
-   flashcards
-   audio
-   configuration

------------------------------------------------------------------------

# 13. Model lifecycle

Use a model registry:

``` text
model_manifest.json

language_pack: sat-v1
asr:
  model_id
  sha256
  size
  runtime
mt:
  model_id
  sha256
  size
  runtime
tts:
  model_id
  sha256
  size
  runtime
```

The app verifies SHA-256 before activating a package.

Only one large inference model should be active at a time if memory
testing requires it.

------------------------------------------------------------------------

# 14. Storage budget

Hard target:

  Asset                       Budget
  -------------------- -------------
  App                      40--70 MB
  Hindi ASR                30--80 MB
  MT                     100--180 MB
  Santali TTS              20--80 MB
  Tokenizers/runtime       20--50 MB
  Curriculum               10--30 MB
  Images/flashcards        20--50 MB
  Safety margin            30--50 MB
  **Total**              **≤500 MB**

These are engineering budgets, not guaranteed model sizes.

The project is not considered hardware-feasible until the final exported
artifacts are measured on-device.

------------------------------------------------------------------------

# 15. Pedagogy engine

No AI generation at runtime.

All lessons are structured records:

``` json
{
  "lesson_id": "G1-MATH-COUNT-01",
  "grade": 1,
  "subject": "mathematics",
  "domain": "FLN",
  "outcomes": ["ILM-..."],
  "teacher_script": [],
  "activities": [],
  "assessment": [],
  "flashcards": [],
  "worksheet_template": "count_objects_v1"
}
```

Translation can be precomputed for packaged curriculum content.

Live translation remains available for spontaneous teacher dialogue.

------------------------------------------------------------------------

# 16. NIPUN Bharat alignment

Use NIPUN Bharat learning outcomes/competencies as metadata, not as a
marketing label.

Official reference: https://nipunbharat.education.gov.in/

Official guidelines:
https://www.education.gov.in/sites/upload_files/mhrd/files/nipun_bharat_eng1.pdf

Example competency mapping:

-   phonological awareness
-   reading comprehension
-   vocabulary
-   number identification
-   number comparison
-   addition/subtraction
-   patterns
-   measurement

Each lesson stores:

`goal → competency → learning outcome → activity → assessment`

------------------------------------------------------------------------

# 17. Worksheets

Templates only.

Initial templates:

1.  Count objects.
2.  Match picture to word.
3.  Fill missing number.
4.  Compare numbers.
5.  Match word/picture.
6.  Circle correct answer.
7.  Oral assessment prompt.
8.  Simple reading/vocabulary exercise.

Output:

``` text
Hindi prompt
────────────────
Santali prompt
────────────────
Student answer area
```

Worksheet generation must be deterministic and instant.

------------------------------------------------------------------------

# 18. Flashcards

Prebuilt assets.

Each card:

``` text
card_id
lesson_id
image
hindi_text
santali_text
audio_file
outcome_id
```

Do not generate images with AI on-device.

Use compressed WebP/JPEG assets.

------------------------------------------------------------------------

# 19. Android screens

1.  Splash / model status
2.  Home
3.  Grade selection
4.  Subject selection
5.  Lesson browser
6.  Lesson detail
7.  Live Translate
8.  Translation result
9.  Flashcards
10. Worksheet
11. Downloads/Language Pack
12. Settings/Diagnostics

The Live Translate screen is the hero screen.

------------------------------------------------------------------------

# 21. Extensibility

Use:

``` text
LanguagePack
 ├── languageCode
 ├── script
 ├── tokenizer
 ├── asrModel
 ├── mtModel
 ├── ttsModel
 ├── terminology
 └── content
```

Then:

``` text
Hindi → Santali
Hindi → Ho
Hindi → Mundari
```

become different language-pack configurations rather than separate
applications.

Do not ship Ho/Mundari in the MVP.

------------------------------------------------------------------------

# 22. Evaluation

## Translation

-   BLEU
-   chrF
-   COMET where feasible
-   human adequacy
-   human fluency
-   terminology accuracy
-   Ol Chiki/script correctness

## ASR

-   WER
-   command accuracy
-   classroom noise robustness
-   latency

## TTS

-   intelligibility
-   MOS/native-speaker rating
-   real-time factor
-   first-audio latency
-   memory

## System

-   cold-start time
-   peak RAM
-   APK/package size
-   battery impact
-   offline reliability
-   crash-free sessions

------------------------------------------------------------------------

# 23. Demo acceptance test

The final SIH demo must demonstrate:

1.  Open app.
2.  Show preloaded lesson.
3.  Turn Wi-Fi/mobile data OFF.
4.  Translate a lesson sentence.
5.  Play Santali audio.
6.  Generate worksheet.
7.  Open flashcards.
8.  Press-and-hold microphone.
9.  Speak Hindi.
10. Show recognized Hindi.
11. Show Santali translation.
12. Play Santali speech.
13. Show `<3s` latency.
14. Repeat with another classroom command.
15. Show device/model diagnostics.

No hidden internet.

------------------------------------------------------------------------

# 24. Repository stack

## Core

### IndicTrans2

https://github.com/AI4Bharat/IndicTrans2

Use for: - MT model - fine-tuning - tokenizer - evaluation - CT2
conversion

### IndicConformerASR

https://github.com/AI4Bharat/IndicConformerASR

Use for: - Hindi ASR benchmark - Santali ASR future extension

### sherpa-onnx

https://github.com/k2-fsa/sherpa-onnx

Use for: - Android offline ASR/TTS runtime - ONNX model execution -
Android JNI integration

### ONNX Runtime

https://github.com/microsoft/onnxruntime

Use for: - ONNX mobile inference - custom model execution -
quantization/deployment reference

### Vosk

https://github.com/alphacep/vosk-api

Use as: - lightweight Hindi ASR fallback

### whisper.cpp

https://github.com/ggerganov/whisper.cpp

Use as: - Whisper tiny/base benchmark

### Piper

https://github.com/OHF-Voice/piper1-gpl

Use as: - compact TTS architecture/training option

### Coqui TTS

https://github.com/coqui-ai/TTS

Use as: - TTS research/training alternative

------------------------------------------------------------------------

# 25. License policy

Before merging any external repository/model:

1.  Record repository license.
2.  Record model license separately.
3.  Record dataset license separately.
4.  Record voice/dataset consent/terms where applicable.
5.  Keep `THIRD_PARTY_NOTICES.md`.
6.  Keep original copyright notices.
7.  Do not assume a model inherits the repository's license.
8.  Do not redistribute gated/restricted artifacts without satisfying
    their terms.

Known useful licenses:

-   IndicTrans2 code/model checkpoints: MIT.
-   IndicConformerASR: MIT.
-   sherpa-onnx: Apache-2.0.
-   ONNX Runtime: MIT.
-   Vosk: Apache-2.0.
-   COILD-MT-Corpus: CC BY 4.0.
-   Education_v2: CC BY 4.0.
-   IndicVoices: CC BY 4.0.
-   Quipus 0.6 speechv2: MIT, but too large for direct on-device use.
-   Piper successor: GPL-3.0.

------------------------------------------------------------------------

# 26. Development phases

## Phase 0 --- Feasibility

Do first.

-   benchmark IndicTrans2 Hindi→Santali
-   benchmark ASR candidates
-   benchmark Quipus Santali voice
-   train/prototype compact Santali TTS
-   measure on actual 2 GB tablet

No UI polish.

## Phase 1 --- MT

-   education corpus cleaning
-   fine-tuning
-   evaluation
-   quantization
-   mobile inference

## Phase 2 --- Speech

-   Hindi ASR
-   Santali TTS
-   end-to-end latency
-   memory optimization

## Phase 3 --- Android

-   lesson browser
-   live translation
-   offline model manager
-   local database

## Phase 4 --- Pedagogy

-   worksheets
-   flashcards
-   outcome mapping
-   lesson content

## Phase 5 --- SIH polish

-   diagnostics
-   latency visualization
-   offline proof
-   demo script
-   GitHub documentation
-   architecture diagram
-   evaluation report

------------------------------------------------------------------------

# 27. Definition of done

The MVP is complete only when all are true:

-   [ ] Android 9+ works.
-   [ ] Tested on 2 GB RAM device.
-   [ ] App + deployed models/content ≤500 MB target.
-   [ ] Wi-Fi disabled during test.
-   [ ] Hindi speech recognized locally.
-   [ ] Hindi translated to Santali/Ol Chiki locally.
-   [ ] Santali synthesized locally.
-   [ ] End-to-end latency ≤3 seconds for defined demo utterances.
-   [ ] Lesson browser works.
-   [ ] Worksheets generated offline.
-   [ ] Flashcards work offline.
-   [ ] NIPUN outcome IDs attached to lessons.
-   [ ] Content package can be installed without internet.
-   [ ] All third-party licenses documented.
-   [ ] Benchmark results published in repository.
-   [ ] Demo can be reproduced from a clean checkout.

------------------------------------------------------------------------

# 28. Winning SIH positioning

The product is not:

> "an AI translator."

It is:

> **An offline classroom language bridge that lets a Hindi-medium
> teacher deliver NIPUN-aligned foundational learning in Santali on a
> low-cost Android tablet without internet.**

The strongest demo claims must be measurable:

-   offline
-   low-end hardware
-   sub-3-second voice pipeline
-   Ol Chiki
-   FLN/NIPUN alignment
-   bilingual teaching material
-   extensible language-pack architecture
