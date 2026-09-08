# PLAN — P3: ASR End-to-End Verification (on device)

**Goal:** Confirm push-to-talk captures mic audio and returns recognized Hindi text on-device.

**Context:**
- Whisper-tiny Hindi ASR + Silero VAD adapters load and the mic is stable (verified empirically).
- Push-to-talk UI calls ASR but recognized text is **not yet confirmed** end-to-end.
- Emulator often has no mic source → need a WAV-fed path for headless/Studio-emulator testing.

**Tasks:**
1. Tap flow: mic (16k mono PCM16) → Silero `Vad` → whisper `OfflineRecognizer.decode` → UI text.
2. Add a **"load WAV → ASR"** debug path so the recognizer is verifiable without a physical mic.
3. Show recognized text in `ASR result:`; log `Vachak-ASR` (samples, latency).
4. Assert no network; assert runs within ASR ≤1 s budget.

**Verify:** on a device/mic OR WAV, decoder emits plausible Hindi transcript; `LatencyTracker`
records ASR leg.

**Acceptance:** Push-to-talk populates `ASR result:` with real Hindi, no crash.
