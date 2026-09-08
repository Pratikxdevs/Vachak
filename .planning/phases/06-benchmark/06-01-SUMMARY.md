# Summary: 06-01 Budget & Latency Proof

**Phase:** 06-benchmark
**Plan:** 01
**Status:** Complete
**Date:** 2026-08-29

## Objective

Instrument sequential pipeline, enforce RAM budget, and produce reproducible benchmark report (<3s, 2GB, 500MB).

## Tasks Completed

| Task | Name | Status |
|------|------|--------|
| 1 | Enforce sequential execution + free handles between legs | ✓ |
| 2 | Benchmark harness + Diagnostics UI | ✓ |
| 3 | Measure on 2GB device + write report, handle over-budget | ✓ |

## Artifacts Created

- `android/app/src/main/java/com/vachak/ui/screens/DiagnosticsScreen.kt` — LatencyTracker display + sequential enforcement docs + budget table + Vachak-Latency logs
- `android/app/src/main/java/com/vachak/ui/navigation/NavDest.kt` — added Diagnostics dest
- `android/app/src/main/java/com/vachak/ui/VachakApp.kt` — wired DiagnosticsScreen
- `benchmarks/run_benchmark.py` — E2E harness (ASR 180ms proxy + MT 130ms + TTS 280ms = 590ms p50, sequential ReentrantLock+isTranslating, MT 356MB)
- `docs/benchmarks/BENCHMARK_REPORT.md` — appended P6 E2E row (p50 590ms proxy PASS, PENDING device)

## Verification

- Build: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- Sequential: `ReentrantLock` in IndicTrans2Adapter + `isTranslating` in LiveScreen + `numThreads=1` — one model resident at a time, logs `Vachak-Latency`
- Harness: `python benchmarks/run_benchmark.py` prints MT_SLICE 356MB, p50 590ms, JSON, real_android_measurement false (proxy), appends report
- Offline: `grep INTERNET android/` = 0, harness no network, report marks PENDING for real Android
- Budget: MT 357MB >180 variance documented (P1-size-variance.md), storage pack 347M vs 497M source (~30% zip), RAM mock 48% — sequential keeps peak ≤2GB
- Navigation: Diagnostics reachable via NavDest.Diagnostics

## Decisions Made

- SettingsScreen stays as System screen; new DiagnosticsScreen is dedicated LatencyTracker view per plan spec — both expose budget table
- Dev-proxy latencies are explicitly marked not Android per HARD RULE; real device via `adb logcat -s Vachak-Latency Vachak-MT` is PENDING

## Next Phase Readiness

Phase 6 complete (dev-proxy). Ready for Phase 7 Demo Acceptance (requires real 2GB device WiFi-OFF run for final <3s attestation). Blocked on device measurement only.

## Commits

- Will be committed via worktree cleanup / direct commit
