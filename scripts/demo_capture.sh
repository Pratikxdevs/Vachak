#!/usr/bin/env bash
# scripts/demo_capture.sh — WiFi OFF demo evidence capture for SIH26042
# Records adb logcat -s Vachak-* + dumpsys meminfo + screenshots for docs/demo-acceptance.md
# Usage: ./scripts/demo_capture.sh [--device-id <id>]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/docs/demo/screens"
LOG_JSON="/tmp/vachak_run.json"
DEVICE_ID="${1:-}"

if [[ "$1" == "--device-id" && -n "${2:-}" ]]; then DEVICE_ID="$2"; shift 2; fi
if [[ -z "$DEVICE_ID" ]]; then DEVICE_ID=$(adb devices | awk 'NR==2{print $1}'); fi
if [[ -z "$DEVICE_ID" || "$DEVICE_ID" == "List"* ]]; then echo "No device found (adb devices)"; exit 1; fi

mkdir -p "$OUT_DIR"
echo "[demo_capture] device $DEVICE_ID"
adb logcat -c
echo "[demo_capture] starting logcat Vachak-* -> $LOG_JSON"
adb logcat -s Vachak-Latency:V Vachak-MT:V Vachak-ASR:V Vachak-VAD:V Vachak-TTS:V Vachak-Pack:V --format=json > "$LOG_JSON" 2>&1 &
LOGCAT_PID=$!
trap 'kill $LOGCAT_PID 2>/dev/null || true' EXIT

echo "[demo_capture] disabling WiFi (offline gate)"
adb -s "$DEVICE_ID" shell svc wifi disable || adb shell svc wifi disable
adb -s "$DEVICE_ID" shell dumpsys wifi | grep -i "Wi-Fi is" || true

echo "[demo_capture] device info"
adb -s "$DEVICE_ID" shell getprop ro.product.model || true
adb -s "$DEVICE_ID" shell getprop ro.build.version.release || true
adb -s "$DEVICE_ID" shell getprop ro.product.cpu.abi || true
adb -s "$DEVICE_ID" shell dumpsys meminfo com.vachak | head -n 40 || true

echo "[demo_capture] running harness --mode=device (forbid_dev_as_android)"
python3 "$ROOT/benchmarks/run_benchmark.py" --mode=device --device-id "$DEVICE_ID" 2>&1 | tee /tmp/demo_benchmark.log || echo "[demo_capture] harness pending (no device) — PENDING kept"

echo "[demo_capture] screenshots (manual demo must have run: open->lesson->WiFi OFF->translate->play->worksheet->flashcards->PTT->Santali->audio->diagnostics)"
for name in home live diagnostics; do
  adb -s "$DEVICE_ID" shell screencap -p "/sdcard/screen_${name}.png" 2>/dev/null && adb -s "$DEVICE_ID" pull "/sdcard/screen_${name}.png" "$OUT_DIR/screen_${name}.png" 2>/dev/null && echo "  saved $OUT_DIR/screen_${name}.png" || echo "  skip $name (no frame)"
done

echo "[demo_capture] latency"
adb logcat -d | grep -E "Vachak-Latency|VOICE END total" | head -n 20 || echo "  no latency yet (run PTT first)"

echo "[demo_capture] verify DRAFT + SME gate"
python3 -c "from curriculum.data import load_sat; db=load_sat(); print(db.validate_warnings())" | grep -q DRAFT && echo "  DRAFT watermark present" || echo "  DRAFT missing"
python3 "$ROOT/packages/build_pack.py" --require-approved 2>&1 | grep -q "SME GATE FAIL" && echo "  SME gate correctly fails on DRAFT" || echo "  SME gate unexpected (maybe APPROVED?)"

echo "[demo_capture] meminfo + wifi status"
adb -s "$DEVICE_ID" shell dumpsys meminfo com.vachak | grep -E "TOTAL|Java Heap" | head -n 5 || true
adb -s "$DEVICE_ID" shell dumpsys wifi | grep -i "Wi-Fi is" || true

kill $LOGCAT_PID 2>/dev/null || true
echo "[demo_capture] log $LOG_JSON head"
head -n 20 "$LOG_JSON" 2>/dev/null || true
echo "[demo_capture] done — re-enable WiFi with: adb shell svc wifi enable"
