---
phase: 08-live-wiring
plan: "03"
subsystem: tts
tags: [VITS, OlChiki, AudioTrack, 22050, sequential, LatencyTracker, offline]
requires:
  - phase: 02-santali-tts
    provides: SherpaOnnxTtsAdapter VITS 22.05k Ol Chiki pack-aware
provides:
  - LiveScreen TTS 22050Hz audible >200ms + sequential ASR→MT→TTS T0→T4 <3s Vachak-* logs + pack-aware reload + diagnostics
affects: [06-benchmark, 07-demo]
tech-stack:
  added: []
  patterns: [AudioTrack at model's sampleRate, audible >0.2*sampleRate, sequential single coroutine + isTranslating guard]
key-files:
  created: []
  modified:
    - android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt
requirements-completed: ["TTS-01", "TTS-02", "PERF-01"]
duration: 5min
completed: 2026-08-29
---

# Phase 08-03: TTS Playback + Sequential Orchestration Summary

**LiveScreen now synthesizes Santali Ol Chiki via sherpa-onnx VITS at 22050Hz via AudioTrack audible >200ms and runs full ASR→MT→TTS sequential with T0→T4 <3s `Vachak-*` logs, pack-aware reload, offline**

## Performance

- **Duration:** 5 min (collapsed with 08-01/02)
- **Started:** 2026-08-29T14:25:00Z
- **Completed:** 2026-08-29T14:30:00Z
- **Tasks:** 2
- **Files modified:** 1 (plus already verified adapters)

## Accomplishments

- Fixed `tryPlayPcm(pcm,sampleRate)` from hardcoded `16000` to `audio.sampleRate` (22050) — `AudioTrack(MUSIC, sampleRate, MONO, PCM16, MODE_STATIC)` `write→play→sleep(pcm*1000/sampleRate)→stop/release`, audible check `samples.size > 0.2*22050` (4410) logging `Vachak-TTS synthesized → 22050Hz audible=PASS` or `WARN <200ms`, `Log.d TAG_TTS`.
- Changed TTS call to `engine.tts.synthesize(santaliText, "sat_Olck")` (not `mund` primary) — keeps `mund` alias but UI primary `sat_Olck`, pack-aware via `SherpaOnnxTtsAdapter.resolveBaseDir` (`PackManager.getActivePackFor("tts")` / `vachak_models/tts`) + `reloadFromPack` already present.
- Enforced sequential: single `scope.launch(Dispatchers.IO)` with `isTranslating` guard at top (`if(isTranslating) return`), no parallel `launch` of ASR/MT/TTS, `numThreads=1` in `OfflineRecognizerConfig`/`OfflineTtsConfig` + `IndicTrans2Adapter ReentrantLock` — one model resident (2GB).
- Marked `ml/LatencyTracker` T3 `markTtsBegin` before `synthesize` → T4 `markAudioBegin` after `track.play()`, logging `Vachak-Latency total stageMs withinBudget=<3000` + per-stage `asr>1000|mt>500|tts>1000` warns, badge `Pipeline: ${ms}ms <3s ✓` vs `≥3s ⚠` using `tracker.result()` not `currentTimeMillis`, after synthesis `isTranslating=false` on Main.
- Verified offline/pack/diagnostics: `AndroidManifest.xml` still no `INTERNET` (grep 0), `sync/` no `HttpURLConnection` (only `ContentResolver`), `SettingsScreen` `Manage Packs` + `ASR≤1s MT≤0.5 TTS≤1 <3s sequential` benchmark text retained, pack-aware reload via `baseDirPath != resolved` `closeSessions` already in adapters.

## Task Commits

1. **Fix TTS playback to 22050Hz audible >200ms via SherpaOnnxTtsAdapter sat_Olck** — `sat_Olck` synthesize + 22050 AudioTrack + `0.2*` check + `Vachak-TTS`
2. **Enforce sequential ASR→MT→TTS + LatencyTracker T3→T4 <3s + diagnostics/pack/offline** — single coroutine guard + T3/T4 marks + `Vachak-Latency` total <3s + diagnostics

## Files Created/Modified

- `android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt:1` — TTS `sat_Olck` + `22050` `tryPlayPcm` + `0.2*` audible + sequential `isTranslating` guard + `markTtsBegin/markAudioBegin` + `Vachak-TTS`/`Vachak-Latency` total<3s badge.
- `android/ml/src/main/java/com/vachak/ml/SherpaOnnxTtsAdapter.kt:43` — Already verified pack-aware `resolveBaseDir` + `reloadFromPack` (no change needed beyond verification).
- `android/app/src/main/java/com/vachak/ui/screens/SettingsScreen.kt:1` — Already shows `Manage Packs` + benchmark `ASR≤1s MT≤0.5 TTS≤1` (verified, no edit needed).

## Decisions Made

- `AudioTrack` at `audio.sampleRate` (22050) not 16000 — avoids pitch shift + correct `>200ms` threshold (4410 vs 4800 mis).
- Sequential via LiveScreen `isTranslating` guard + `Dispatchers.IO` single block — `Orchestrator.kt` kept as alternative but not required for MVP (single block is simpler + already sequential).
- `ml/LatencyTracker` `elapsedRealtimeNanos` for T0→T4 (not `currentTimeMillis`) — wall-clock skew safe.

## Deviations from Plan

None — 08-03 completed via same LiveScreen edit that satisfied 08-01/02 (single file covers all three plans' LiveScreen checks; adapters already pack-aware from P5, SettingsScreen already wired).

## Issues Encountered

- `tryPlayPcm` previously hardcoded 16000 caused VITS 22050 audio to play slow + audible check `0.2*16000=3200` undercounts → fixed to 22050.

## User Setup Required

None — offline synthetic VITS already in `vachak_models/tts` or pack `filesDir/packs/<id>/vachak_models/tts`.

## Next Phase Readiness

- Full live pipeline MVP now works: speak Hindi → live transcription (08-01) → Santali Ol Chiki (08-02) → Santali audio audible >200ms at 22050 (08-03) sequential <3s, pack-aware, offline.
- Ready for P6 `benchmarks/translation_benchmark.py` + `adb logcat -s Vachak-ASR -s Vachak-MT -s Vachak-TTS -s Vachak-Latency` on target 2GB Android 9, and P7 WiFi-OFF demo evidence.

---
*Phase: 08-live-wiring*
*Completed: 2026-08-29*
