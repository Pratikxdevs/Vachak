"""Judge-facing demo flow (PHASE 12).

Runs the 90-120s narrative offline using the orchestrator with DEV-FIXTURE mock
engines, so the *flow* is demonstrable without models. All timings shown are
labeled DEV FIXTURE and are NOT Android latency (see benchmarks). For the real
SIH demo, swap the mocks for sherpa-onnx / IndicTrans2 via the EngineProvider and
run on the 2GB tablet with WiFi OFF.

    python scripts/demo_flow.py            # narrated, runs pipeline once
    python scripts/demo_flow.py --script   # print the timed script only

Flow sections (target ~90-120s):
  P problem -> O offline proof -> C classroom -> V voice -> T translation
  -> S speech -> L learning content -> R performance -> I impact/multilingal
"""
from __future__ import annotations

import argparse
import os
import sys
import time

_ROOT = os.path.dirname(os.path.dirname(__file__))
if _ROOT not in sys.path:
    sys.path.insert(0, _ROOT)

from shared.orchestrator import (  # noqa: E402
    Components,
    Orchestrator,
    MockASR,
    MockContext,
    MockNMT,
    MockTTS,
    MockVAD,
    MockValidator,
    MockSpeaker,
)
from ml.benchmarks.harness import network_request_audit  # noqa: E402

SECTIONS = [
    ("P", "Problem", "कक्षा में शिक्षक हिंदी बोलते हैं, बच्चे संताली जानते हैं — भाषा की दीवार।"),
    ("O", "Offline proof", "WiFi बंद। ऐप कोई नेटवर्क कॉल नहीं करता — ऑडिट चलाएँ।"),
    ("C", "Classroom", "FLN पाठ लोड करें (प्री-कंप्यूटेड अनुवाद)।"),
    ("V", "Voice", "पुश-टू-टॉक: शिक्षक हिंदी बोलते हैं।"),
    ("T", "Translation", "हिंदी → संताली (Ol Chiki) अनुवाद।"),
    ("S", "Speech", "संताली आडियो बजाएँ (TTS)।"),
    ("L", "Learning content", "वर्कशीट + फ्लैशकार्ड (टेम्प्लेट-आधारित, प्रीबिल्ट)।"),
    ("R", "Performance", "पाइपलाइन <3s बजट (असल डिवाइस पर PENDING)।"),
    ("I", "Impact / multi-language", "अन्य भाषाओं के लिए भाषा पैक जोड़ें।"),
]


def run_pipeline() -> None:
    orch = Orchestrator(Components(
        vad=MockVAD(), asr=MockASR(), context=MockContext(),
        nmt=MockNMT(), validator=MockValidator(), tts=MockTTS(),
        speaker=MockSpeaker(),
    ))
    r = orch.process_utterance("audio-fixture")
    print(f"    ASR : {r.transcript}")
    print(f"    MT  : {r.translation}  (DEV FIXTURE)")
    print(f"    TTS : audio ready = {r.present_audio is not None}  (DEV FIXTURE)")
    print(f"    status={r.status.value} total_dev_ms={r.total_ms:.1f} (DEV FIXTURE, not Android)")


def main() -> int:
    ap = argparse.ArgumentParser(description="Vachak 90-120s demo flow")
    ap.add_argument("--script", action="store_true", help="print the timed script only")
    args = ap.parse_args()

    print("=" * 64)
    print("VACHAK — SIH26042 OFFLINE DEMO (DEV FIXTURE orchestration)")
    print("=" * 64)
    for code, title, blurb in SECTIONS:
        print(f"\n[{code}] {title}")
        print(f"    {blurb}")
        if args.script:
            continue
        if code == "O":
            ok = network_request_audit()
            print(f"    offline audit: {'PASS (no network imports)' if ok else 'FAIL'}")
        if code == "V":
            run_pipeline()
    print("\n" + "=" * 64)
    print("Demo narrative complete. Real on-device run: WiFi OFF, 2GB tablet,")
    print("sherpa-onnx (ASR/TTS) + IndicTrans2 (MT) via EngineProvider.")
    print("Latency/battery are PENDING REAL-DEVICE MEASUREMENT (see benchmarks).")
    print("=" * 64)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
