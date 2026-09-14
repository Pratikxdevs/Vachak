# MVP architecture and stack

We aim for a **fully offline** Android app (Android 9+, 2 GB RAM) that the teacher can install. All models and content are bundled (or sideloaded) so **no internet** is needed after initial setup. The core on-device pipeline is:

- **Voice pipeline:** Teacher’s Hindi speech → (ASR) → Hindi text → (MT) → Santhali text → (TTS) → Santhali audio, in <3 s. Push-to-talk UI triggers this cascade. We keep it sequential to limit RAM.
- **Translation engine:** Pretrained Transformer (IndicTrans2) fine-tuned on Hindi→Santhali parallel text.
- **ASR engine:** Offline Hindi speech-to-text (preferably a small model; e.g. Vosk Hindi or Whisper-tiny as fallback if IndicConformer is too big).
- **TTS engine:** Offline Santhali voice (we likely must train a small model, e.g. with Coqui/Piper) to speak Ol Chiki output.

Besides live translation, the app includes:
- **Curriculum browser:** JSON/SQLite of grade/lesson content. Teacher picks grade/subject/lesson. 
- **Worksheet generator:** Template-based worksheets (fill-in, matching, simple math) auto-filled with Hindi questions and their Santhali translations.
- **Flashcards:** Per-lesson image+text flashcards (bundled assets) with Hindi and Santhali labels.
- **Settings:** Show “OFFLINE” mode, model details, etc.

The on-device tech stack will use **Kotlin/Compose**, Android Room for local data, and ML runtimes via ONNX Runtime Mobile (or sherpa-onnx) with quantized models. 

# Data and models

We must curate all training data. Key datasets:

- **Hindi–Santhali parallel text:** The IIT Patna *COILD-MT* corpus includes **20,603 Hindi–Santali (sat_Olck)** sentence pairs. We will use this (CC BY 4.0) to fine-tune translation. (AdiBhashaa reports similarly sized corpora.) Additional Hindi ↔ Santali examples can come from scraped tribal-school content if available.

- **Hindi ASR data:** For Hindi speech recognition, we can rely on existing models/datasets. (IndicConformer’s Hindi model or off-the-shelf Hindi ASR.)

- **Santali speech data:** We need *Santhali* voice recordings to train TTS (and possibly future ASR). Mozilla Common Voice has Santali (Ol Chiki) clips: their 2026 collection contains **533 validated Santali utterances** (∼4.5 s each on average). We will use Common Voice Santali (and possibly crowd-sourced recordings) to build a Santhali TTS dataset. 

- **Curriculum content:** We will get Tamil Nadu/NIPUN Bharat textbooks or Jharkhand MTB-MLE materials (Hindi “Activity instructions” etc.) as source text. Those Hindi lesson scripts and assessment prompts (FLN curriculum) will be translated to Santali. This content is teacher/education dept material and must be assembled (for example as a JSON DB).

**Models to train/convert:** 

1. **MT model:** Start with AI4Bharat IndicTrans2 (320M distilled Indic→Indic model, MIT license). It already supports Santali (Ol Chiki). Fine-tune it on our Hindi–Santali corpus. Then quantize (4/8-bit) and export via CTranslate2/ONNX for mobile inference. We may also test an NLLB-based model (if any distilled Hindi→Santali checkpoints exist) for comparison, but IndicTrans2 is our first choice.

2. **ASR model:** For Hindi speech recognition, possible models include Whisper-tiny (quantized) or the AI4Bharat IndicConformer Hindi model. IndicConformer provides an English-like ASR for Hindi with good accuracy. We will benchmark:
   - **IndicConformer (hi)** – 600M model (MIT license) vs. smaller Whisper or Vosk models. If the large model is too big, we can try a distilled variant (there is work on 30M Conformer models). As a fallback, **Vosk Hindi** (open-source Kaldi model ~50MB) is proven for on-device use.
   We do *not* need Santali ASR for the teacher (the teacher speaks Hindi). (Santali ASR would only be needed if we bidirectional translate student queries, a stretch goal.)

3. **TTS model:** This is the riskiest part. There is *no* publicly available high-quality Santhali TTS. We will need to train one. Possible approaches:
   - Use Coqui TTS or Piper (Rhasspy) training. We may need to collect ~2–3 hours of Santhali speech from native speakers (likely from tribal teachers or students) to train a FastSpeech/HiFiGAN pipeline or a lightweight VITS model. Barring that, we could try a multi-speaker Asia-centric TTS model and fine-tune on any small Santali corpus. Output must sound intelligible for learners.
   - **Runtime:** Prefer an ONNX/Piper engine for Android. Rhasspy Piper supports exporting to ONNX/PyTorch for embedded. Or use sherpa-onnx’s TTS component if available.

4. **Language assets:** We’ll need tokenizers (SentencePiece) for Hindi/Santali and lexicons for Ol Chiki.

All trained/converted models must fit in our app bundle. Rough budgets: MT model ~150MB INT8, Hindi ASR ~50–100MB, TTS ~50–100MB (to reach ~500MB). We will explore INT8 or int4 quantization where possible.

# Code and repos to leverage

We will **re-use** and fork existing open-source projects rather than coding ML components from scratch:

- **Android + ONNX:** Microsoft’s ONNX Runtime Android examples show how to load quantized models and run inference [Microsoft ONNX examples] (for example, see their “Qwen_QA/Android” demo in the onnxruntime repo). We will follow that pattern (ORT mobile) for loading ASR/MT/TTS engines. Sherpa-onnx (k2-fsa) also offers Android bindings for streaming ASR/TTS that could simplify integration.

- **Translation:** AI4Bharat’s [IndicTrans2 GitHub] contains code to download and run their models. We can use its inference scripts (for example, CTranslate2 conversion) as a base. We’ll fine-tune their model on COILD data.

- **ASR:** AI4Bharat’s [IndicConformerASR GitHub] provides pretrained models for Hindi and Santali (see lines for Hindi (hi) and Santali (sat)). We can download the Hindi model and convert it to ONNX. Alternatively, use Whisper.cpp (an embedded C++ Whisper) or Vosk’s Android demo repository for quick integration.

- **TTS:** Use Coqui TTS or Rhasspy Piper repositories to train our Santhali voice. For runtime, if using Piper, we can leverage its native inference code (it has an experimental Rust/Android port). If Coqui, export to ONNX. 

- **Curriculum and UI:** We’ll write our own app logic (Kotlin). But for structure, we may reference “Uktam” (an offline Indic voice translator app) for guidance. Uktam runs offline ASR→MT→TTS on Android, although it requires high-end devices; our constraints are tougher, but the design is similar.

# Features

We map exactly to the SIH requirements:

1. **Hindi→Santhali text translation:** We will implement lesson-scope translation using the fine-tuned IndicTrans2 model (MVP for at least one target language). This covers scripts (Hindi Devanagari to Santali Ol Chiki) and domain (FLN curriculum). (IndicTrans2 supports **sat_Olck** output.)

2. **Real-time voice-to-voice:** Push-to-talk button triggers live translation: microphone → Hindi ASR → translate → Santali TTS (Ol Chiki script output spoken). We target <3s total (e.g. ASR ~1s, MT ~0.5s, TTS ~1s) by using small models. During demo, show a latency counter. 

3. **Fully offline:** All models and data are embedded or sideloaded. No cloud calls. We will disable/avoid any network usage in the demo.

4. **Bilingual worksheets:** For each lesson, the app generates (or has pre-generated) PDF/HTML worksheets with Hindi questions/instructions and their Santhali translation side by side. These are based on fill-in, match-the-pair, counting, etc., aligned to the lesson content and NIPUN learning outcomes. (These use templated content — no on-device LLM — so it’s deterministic.)

5. **Visual flashcards:** Pre-made image+text flashcards per lesson are included. For example, pictures of fruits or numbers with Hindi + Santali labels. These are static assets (not generated by ML). We bundle them with the lesson content.

**Supporting features:**

- **Content sync:** On setup, the tablet can download a ZIP of models+curriculum via Wi-Fi (or be side-loaded via USB). After that, everything is local. (For the MVP we can manually install via ADB or include in the APK.)

- **Lesson browser:** The UI lets the teacher pick Class (grade) → Subject → Topic/Lesson. We’ll include a handful (e.g. Class1-3 math/language basics) to demo variety.

- **Audio playback:** Any translated text can be played by tapping a speaker icon. Also we’d let teachers replay the voice translation output if needed.

- **Two-way (stretch):** As a bonus, we *could* implement Santali speech recognition and Hindi TTS so that student questions in Santali are recognized and translated back. This is nice-to-have if time permits (IndicConformer has a Santali ASR model).

- **Extensibility:** Architect the code so adding Ho or Mundari would mainly mean new corpora, lexicons, and possibly new models (their languages too could use new IndicTrans2 fine-tuning). Our UI can show “Language” choices for translation once supported.

**Not required:** No user accounts, no cloud backend, no multi-user sync. All is local or one-time sync.

# Tech stack summary

- **Android App:** Kotlin + Jetpack Compose UI. Room/SQLite for content DB.
- **ML runtime:** ONNX Runtime Mobile (or sherpa-onnx) to run quantized models. We may use the `ort` library via JNI or the provided Android AAR.
- **ASR:** Vosk or Whisper.cpp for Hindi ASR (choose based on latency/accuracy). Vosk has a Java API, Whisper requires C++ integration (Ambisonic), but either can be converted to ONNX.
- **MT:** Fairseq-trained IndicTrans2 model. Inference via ONNX/CTranslate2 in Kotlin.
- **TTS:** ONNX engine for our custom Santali voice (possibly the one used by Piper).
- **Content:** JSON/SQLite for lessons; PDF generation libraries if needed for worksheets.

# Repositories to build on

- **AI4Bharat/IndicTrans2** – for translation models (supports Santali, Ol Chiki).
- **AI4Bharat/IndicConformerASR** – for downloading pretrained ASR (Hindi, and Santali if needed).
- **Microsoft ONNXRuntime Android examples** – for embedding quantized ONNX models in an Android app.
- **rhasspy/piper (or Coqui TTS)** – for training a Santhali TTS and exporting a small voice model.
- **Vosk Android demo** – as a fallback for Hindi ASR offline.
- **Sherpa-onnx** – optional runtime library for integrated ASR/TTS streaming on Android.
- **Uktam (GitHub)** – as an architecture reference (offline voice translation app).

We will fork/clone these where needed and integrate into our repository (the final GitHub submission will also have our Android code plus any config/scripts we write). 

# Backend/Sync (one-time only)

We actually won’t need a persistent backend server for the demo. However, we will define a simple “content sync” mechanism:

- **Initial content packaging:** All lesson scripts (Hindi text), pre-translated Santali text (for static content), images, model binaries, and templates are prepared on a workstation. We create a ZIP or APK expansion file with these assets.
- **Updating:** In a real deployment, an admin could upload new content bundle to a server and tablets download it (or use a USB stick). For SIH, we may simulate this by copying via ADB or bundling in APK.
- **Offline assertion:** In the UI we will show “OFFLINE” mode clearly, and have an intentional demo step where we toggle airplane mode to prove nothing breaks.

If a backend is needed in writing: We would outline an HTTP file server (or simple S3) serving the content package and optionally new model updates. But since “no cloud calls” is a constraint, we treat the backend as outside MVP (just mention that updates could be via manual sync).

# Summary of decisions

- **Minimum scope:** Hindi→Santhali only, teacher-facing. (Ho/Mundari support is architecture-extensible but not built in MVP.)
- **Models:** IndicTrans2 (fine-tuned) for MT; Android-quantized. Hindi ASR via Vosk/Whisper (benchmark and choose). Santali TTS to be trained.
- **Data:** Use COILD 20K Hindi–Santali pairs for translation. Use CommonVoice Santali audio (533 clips) for TTS. Curate curriculum text for the specific grades.
- **Repos:** IndicTrans2, IndicConformer, ONNX examples, Vosk demo, Coqui/Piper.
- **App:** All offline. Kotlin + Room + ONNX. UI with lessons, translation button, worksheets (template PDF), flashcards (image widgets).
- **Output:** Demo video showing the flow (teacher speaks Hindi, app replies in Santali, offline). The GitHub repo will include code, model reference, and instructions.

By focusing on **one tribal language (Santali)** and showcasing real-time Hindi→Santali audio translation on-device, along with bilingual worksheets and flashcards, we meet all core SIH features. We avoid overreach (no cloud, no extra LLM) and leverage existing open resources. With this plan, we have a clear, buildable MVP that satisfies the problem statement. 

**Sources:** We will base our models/data choices on AI4Bharat IndicTrans2 (with Santali support), the IIT Patna COILD corpus (Hindi–Santali 20K pairs), and Mozilla Common Voice Santali (Santhali audio). These ensure our MVP uses proven resources for quality and breadth. All APIs used (e.g. ONNX Runtime) and scripts (finetune, convert) are from open-source, MIT/Apache-licensed projects referenced above.