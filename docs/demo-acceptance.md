# Demo Acceptance — WiFi OFF Full Script (SIH26042)

**Hard rule:** No hidden internet. App opens → lesson loads → WiFi OFF → translate lesson → play Santali audio → generate worksheet → flashcards → push-to-talk Hindi → show recognized text → show Santali → play audio → show <3s latency → diagnostics. See `docs/DEMO.md` for 110s script.

## Preconditions

- Device: Target Tablet 2GB / Android 9 / arm64-v8a (primary SIH target) — see `docs/benchmarks/BENCHMARK_REPORT.md` Device matrix.
- Pack installed: `sat_Olck-v0.1.0.vachakpack` via SAF `ManagePacksScreen` → `filesDir/packs/<id>/` with sha256 verify.
- ONNX preserved at `android/ml/src/main/assets/vachak_models/mt` (357M) until CT2 migration benchmark PASS — do not delete.
- Curriculum: `curriculum/lessons/sat_lessons.json` meta.content_status = `AUTHOR-DRAFT` — Diagnostics shows DRAFT watermark (curriculum/data.py validate_warnings). Pack `build_pack --require-approved` must FAIL on DRAFT.

## WiFi OFF Capture Script

```bash
# 1. Build + install (offline APK, no INTERNET permission)
cd /home/clutch/Desktop/Vachak
./android/gradlew :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# 2. Clear logcat, capture device info
adb logcat -c
adb shell getprop ro.product.model; adb shell getprop ro.build.version.release; adb shell getprop ro.product.cpu.abi
adb shell dumpsys meminfo com.vachak | head -n 30

# 3. Start offline trace (Vachak-* + latency + MT)
adb logcat -s Vachak-Latency:V Vachak-MT:V Vachak-ASR:V Vachak-VAD:V Vachak-TTS:V Vachak-Pack:V --format=json > /tmp/vachak_run.json &
LOGCAT_PID=$!

# 4. Disable WiFi (airplane / wifi) — hard offline gate
adb shell svc wifi disable
adb shell dumpsys wifi | grep -i "Wi-Fi is"
# Expect: Wi-Fi is disabled

# 5. Run device benchmark harness (fills BENCHMARK_REPORT.md p50/p95 from elapsedRealtimeNanos, forbid_dev_as_android)
python benchmarks/run_benchmark.py --mode=device --device-id $(adb devices | awk 'NR==2{print $1}') --out docs/benchmarks/BENCHMARK_REPORT.md
# Guard: harness fails if any latency/RAM metric tagged Android but measured on dev host (forbid_dev_as_android)

# 6. Manual demo flow (observe + screenshot)
# open app → Home → Curriculum → open lesson → WiFi OFF already → Translate → Play Audio (Ol Chiki U+1C50 tick) → Tools → Worksheet → Flashcards → Live → PTT Hindi "नमस्ते" → recognized text → Santali "ᱡᱚᱦᱟᱨ" → Play Audio → latency badge <3s → Settings → Diagnostics (check DRAFT banner + sequential ReentrantLock proof)
adb shell screencap -p /sdcard/screen_home.png && adb pull /sdcard/screen_home.png docs/demo/screens/
adb shell screencap -p /sdcard/screen_live.png && adb pull /sdcard/screen_live.png docs/demo/screens/
adb shell screencap -p /sdcard/screen_diagnostics.png && adb pull /sdcard/screen_diagnostics.png docs/demo/screens/

# 7. Verify latency + offline
adb logcat -d | grep -E "Vachak-Latency|VOICE END total"
# Expect: VOICE END total 136ms <3s (example) on 2GB device, sequential ASR≤1s MT≤0.5s TTS≤1s
adb shell dumpsys meminfo com.vachak | grep -E "TOTAL|Java Heap"
adb shell dumpsys wifi | grep -i "Wi-Fi is"
# Must still be disabled

# 8. Stop trace + re-enable (after evidence)
kill $LOGCAT_PID
adb shell svc wifi enable
cat /tmp/vachak_run.json | head -n 20

# 9. Verify pack provenance + DRAFT gate
python -c "from curriculum.data import load_sat; db=load_sat(); print(db.validate_warnings())" | grep DRAFT
python packages/build_pack.py --require-approved 2>&1 | grep -q "SME GATE FAIL" && echo "SME gate correctly fails on DRAFT" || echo "SME gate unexpected"
grep -c provenance curriculum/lessons/sat_lessons.json  # expect >=15
grep -r "when(current)" android/app/src/main/java/com/vachak/ui/VachakApp.kt && echo "FAIL when(current) still present" || echo "PASS no when(current)"
```

## Evidence Artifacts

- `adb logcat -s Vachak-* --format=json` JSON lines (elapsedRealtimeNanos p50/p95)
- `dumpsys meminfo` + `dumpsys wifi` snapshots
- Screenshots: `docs/demo/screens/screen_*.png`
- `docs/benchmarks/BENCHMARK_REPORT.md` Measured (not PENDING) — see Device matrix below
- `BENCHMARK_REPORT.md` Device matrix must be filled with real_android_measurement=yes for target 2GB
- `scripts/demo_capture.sh` (this script extracted) records all of the above

## Device Matrix (copy to BENCHMARK_REPORT.md — forbid_dev_as_android)

| Device | RAM | Android | Role | real_android_measurement | p50 total | p95 total | within <3s |
|--------|-----|---------|------|--------------------------|-----------|-----------|------------|
| Target Tablet | 2 GB | 9 | primary SIH target | **yes** (adb) | PENDING → fill from device run | PENDING | YES/NO |
| Reference Tablet | 4 GB | 13 | comparison | yes | PENDING | PENDING | — |
| Dev Machine | 16 GB | — | build/CI only (NOT latency representative) | **no** — forbid_dev_as_android guard fails if dev latency claimed as Android | 590 ms (proxy) | 590 ms (proxy) | proxy only |

**HARD RULE (forbid_dev_as_android):** `benchmarks/run_benchmark.py` + `ml/benchmarks/harness.py` reject any report where a latency/RAM metric is tagged `device=android` but `real_android_measurement != true` or host is dev x86_64. BENCHMARK_REPORT.md `Measured` column must be `PENDING` until filled on real 2GB arm64, not dev.

## SME Gate

- `curriculum/lessons/sat_lessons.json` stays `AUTHOR-DRAFT` / `DRAFT` watermark until native speaker SME review.
- `curriculum/data.py validate_warnings()` → `DRAFT: ... 2.2% verified` warning.
- `DiagnosticsScreen` shows DRAFT banner (errorContainer).
- `packages/build_pack.py --require-approved` fails (exit 2) if `MACHINE_TRANSLATED` would be `APPROVED`. Update `docs/MODEL_AND_DATA_PROVENANCE.md` provenance when SME signs.

## Verification Gates (from Plan 10-03)

- `./gradlew assembleDebug` + `assembleRelease` (R8) both PASS, `apkanalyzer` `arm64` only, `grep -r "when(current)" VachakApp.kt` ==0, `grep -r "allowMainThreadQueries" android/content` only debug.
- `adb logcat --pid=$(pidof com.vachak) | grep Latency` shows `VOICE END total 136ms <3s` on 2GB device, `BENCHMARK_REPORT.md` `Measured` filled not `PENDING`.
- `grep -c provenance curriculum/lessons/sat_lessons.json` >=15, `pack --require-approved` fails on `DRAFT` as expected.
- Manual WiFi OFF demo script `docs/DEMO.md 110s` passes.

## References

- `docs/DEMO.md` (110s full script)
- `docs/benchmarks/BENCHMARK_REPORT.md` (device matrix + P1/P3/P2/Conversion rows, PENDING until device)
- `scripts/demo_capture.sh` (executable capture)
- `benchmarks/run_benchmark.py --mode=device` (harness with forbid_dev_as_android)
- `curriculum/data.py` (validate_warnings DRAFT)
- `android/app/src/main/java/com/vachak/ui/screens/DiagnosticsScreen.kt` (DRAFT banner)
