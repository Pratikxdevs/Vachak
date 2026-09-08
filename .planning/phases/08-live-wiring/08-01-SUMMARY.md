---
phase: 08-live-wiring
plan: "01"
subsystem: audio
tags: [AudioRecord, RECORD_AUDIO, permission, VAD, ASR, whisper-tiny]
requires:
  - phase: 03-asr-e2e
    provides: SherpaAsrAdapter VAD-gated ASR + MainScreen recordMic reference
provides:
  - LiveScreen FAB -> AudioRecord 16k mono PCM16 4s max with RECORD_AUDIO launcher, VAD-gated ASR, hindiText DualLangCard, Vachak-ASR/VAD/Latency T0->T1 ≤1s
affects: [08-02, 08-03, 06-benchmark, 07-demo]
tech-stack:
  added: [ActivityResultContracts.RequestPermission, ContextCompat.checkSelfPermission, SnackbarHostState]
  patterns: [AudioRecord 16k mono PCM16 4s max background thread, permission gate before AudioRecord, VAD-gated Short/32768 bridge]
key-files:
  created: []
  modified:
    - android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt
    - android/app/src/main/java/com/vachak/ml/adapter/AudioPipeline.kt
requirements-completed: ["ASR-01", "ASR-02"]
duration: 15min
completed: 2026-08-29
---

# Phase 08-01: Audio Capture + ASR Live Transcription Summary

**LiveScreen FAB now captures 16k mono PCM16 via AudioRecord and shows real Hindi transcription from SherpaAsrAdapter (VAD-gated, Short/32768 bridge) with RECORD_AUDIO permission flow**

## Performance

- **Duration:** 15 min
- **Started:** 2026-08-29T14:05:00Z
- **Completed:** 2026-08-29T14:20:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Replaced `runPipeline(simulatedHindi)` + `delay(180)` mock with real `recordMic(sampleRate=16000,maxMs=4000)` (port of `MainScreen.kt:220` — `getMinBufferSize` + `AudioRecord(MIC,MONO,PCM16)` + `while(RECORDSTATE_RECORDING)` + `stop/release` → `ShortArray`) called on FAB stop.
- Added `RECORD_AUDIO` gate: `hasRecordPermission` via `ContextCompat.checkSelfPermission`, `rememberLauncherForActivityResult(RequestPermission)` showing `mic permission needed` snackbar + banner with `Allow` button, launcher shows `Microphone permission granted` / `Mic permission needed — enable in Settings` hints, `permissionDenied` state. `AndroidManifest.xml` already declares `RECORD_AUDIO` and no `INTERNET` (verified 0).
- Wired `engine.asr.transcribe(pcm,16000)` via `SherpaAsrAdapter` (VAD `SherpaVadDetector` → segments → `FloatArray(e-s){pcm[s+it]/32768.0f}` → `IndicConformerAsrAdapter` `OfflineRecognizer(null,config)` whispertiny + pack-aware `PackManager.getActivePackFor("asr")`) with `LatencyTracker` T0 `markSpeechBegin` → T1 `markAsr` + `Vachak-ASR` transcription log + `Vachak-VAD` + `Vachak-Latency ASR ≤1000` warn, silence hint for empty VAD segments.
- Fleshed `AudioPipeline.captureAndRecognize` from fixture silent buffer to real `AudioRecord.read` loop (same minBuf logic) to keep pipeline reusable for tests.

## Task Commits

1. **Add AudioRecord capture + RECORD_AUDIO launcher + VAD wiring** — LiveScreen FAB → permission launcher → recordMic → VAD → ASR
2. **Surface live Hindi transcription + LatencyT1 + errors** — `hindiText` DualLangCard live, `Vachak-ASR`/`Vachak-Latency`, silence `ERR` handling

**Plan metadata:** `08-01-PLAN.md`

## Files Created/Modified

- `android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt:1` — Added `recordMic`, `hasRecordPermission`, `permissionLauncher`, `SnackbarHost`, `LatencyTracker` T0/T1, `engine.asr.transcribe` real call, removed simulatedHindi/delay, added banner + FAB orange→red logic, logs `Vachak-ASR`/`Vachak-VAD`/`Vachak-Latency`.
- `android/app/src/main/java/com/vachak/ml/adapter/AudioPipeline.kt:1` — Replaced fixture `ShortArray(frameCount)` with real `AudioRecord` capture (minBuf, `STATE_INITIALIZED` guard, `RECORDSTATE_RECORDING` loop, `stop/release`, empty check).

## Decisions Made

- Reuse inline `recordMic` in LiveScreen (as in `MainScreen`) plus fix `AudioPipeline` to keep single truth for tests — both use same 16k/4s/mono pattern.
- Whole-buffer `SherpaVadDetector.detect(ShortArray)` for hold-to-talk MVP (simpler than streaming `runMicrophoneVad` partials) — streaming kept for future P6 incremental.
- `ml/LatencyTracker` `elapsedRealtimeNanos` canonical (not `currentTimeMillis`) for T0→T1.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- LiveScreen previously had zero permission handling; FAB would silently fail on Android 13+ without runtime grant. Fixed by adding launcher + banner + pre-check on start and on stop.

## User Setup Required

None — `RECORD_AUDIO` is declared, launcher requests at runtime; no manual setup.

## Next Phase Readiness

- 08-01 enables 08-02 MT live (needs `hindiText` real); 08-03 TTS will reuse same tracker.
- Ready for P6 latency capture (`adb logcat -s Vachak-ASR`).

---
*Phase: 08-live-wiring*
*Completed: 2026-08-29*
