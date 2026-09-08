---
phase: 08-live-wiring
plan: "02"
subsystem: translation
tags: [IndicTrans2, sat_Olck, OlChiki, MT, ORT]
requires:
  - phase: 01-ondevice-mt
    provides: IndicTrans2Adapter sat_Olck Ol Chiki VITS pack-aware
provides:
  - LiveScreen MT sat_Olck Ol Chiki DualLangCard with Vachak-MT + T2 ≤500ms, no delay mock, pack-aware reload
affects: [08-03, 06-benchmark]
tech-stack:
  added: []
  patterns: [LanguagePair hi->sat_Olck, Ol Chiki regex U+1C50-U+1C7F validator]
key-files:
  created: []
  modified:
    - android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt
requirements-completed: ["MT-02", "MT-03"]
duration: 5min
completed: 2026-08-29
---

# Phase 08-02: MT Live Translation Summary

**LiveScreen now translates live Hindi to Santali Ol Chiki via IndicTrans2 sat_Olck with Ol Chiki validation tick and T2 ≤500ms logging (no mund primary, no delay mock)**

## Performance

- **Duration:** 5 min (collapsed with 08-01 single file edit)
- **Started:** 2026-08-29T14:20:00Z
- **Completed:** 2026-08-29T14:25:00Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments

- Replaced `delay(180)` + `LanguagePair("hi","mund")` mock with real `engine.translation.translate(asrText, LanguagePair("hi","sat_Olck"))` (keeps `mund` alias in `supports()` but UI primary is `sat_Olck` as required by Santali-only roadmap).
- Added Ol Chiki validation `sat.any{it.code in 0x1C50..0x1C7F}` (matches `IndicTrans2Adapter.kt:129` regex) logging `OlChikiValidator: no OlChiki` warn or `PASS`, setting `santaliText` live in second `DualLangCard` (`Santali — Ol Chiki (MT)`).
- Kept typed `OutlinedTextField` fallback for no-mic debug but primary is live ASR `hindiText` from 08-01.
- Marked `ml/LatencyTracker` T2 via `markTranslate(sat)` after MT, logging `Vachak-MT` input→sat + `Vachak-Latency translate ≤500` warn, sequential hold in same `Dispatchers.IO` coroutine (no parallel, `ReentrantLock` inside adapter).

## Task Commits

1. **Replace mock translate with real IndicTrans2 Hin→sat_Olck + Ol Chiki validation** — `sat_Olck` translate + validator + `Vachak-MT`
2. **Mark LatencyTracker T2 ≤500ms and log Vachak-Latency** — T2 `markTranslate` + ≤500 warn

## Files Created/Modified

- `android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt:1` — Removed `delay(180)` (count 0), changed both `LanguagePair` calls to `sat_Olck`, added `hasOlChiki` check + `Vachak-MT` logs, kept pack-aware `EngineProvider.real` (already `PackManager.getActivePackFor("mt")` + `closeSessions`).

## Decisions Made

- Santali-only primary `sat_Olck`; `mund` kept as alias for backward compat per `IndicTrans2Adapter.supports` — UI shows `sat_Olck` badge.
- MT kept sequential via adapter's `ReentrantLock` + LiveScreen single `launch(Dispatchers.IO)` — no extra lock needed.

## Deviations from Plan

None — 08-02 completed as part of same LiveScreen edit that satisfied 08-01 (single file, no extra changes needed for `IndicTrans2Adapter.kt` which was already pack-aware).

## Issues Encountered

None.

## User Setup Required

None.

## Next Phase Readiness

- 08-02 enables 08-03 TTS (needs `santaliText` real); together they enable full ASR→MT→TTS MVP for P6 <3s.

---
*Phase: 08-live-wiring*
*Completed: 2026-08-29*
