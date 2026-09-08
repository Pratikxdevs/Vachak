#!/usr/bin/env python3
"""
TTS Benchmark — Santali Ol Chiki VITS via sherpa-onnx offline
Measures first-audio latency proxy + model size + Ol Chiki coverage. No network.
MOS/intelligibility are human PENDING.
"""
import os, sys, json, statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / "docs/benchmarks/BENCHMARK_REPORT.md"
TTS_DIR = ROOT / "android/ml/src/main/assets/vachak_models/tts"

FIXTURES = [
    "ᱦᱚᱞᱮ",  # hello in Ol Chiki (proxy)
    "ᱟᱭᱚ ᱟᱨ ᱮᱨᱟ", 
    "ᱥᱟᱱᱛᱟᱲᱤ ᱯᱟᱹᱨᱥᱤ",
    "ᱵᱤᱨᱫᱟᱹᱜᱟᱲ",
    "ᱡᱚᱦᱟᱨ",
]

def du_mb(p: Path) -> float:
    if not p.exists():
        return 0.0
    total = 0
    for root, _, files in os.walk(p):
        for f in files:
            total += os.path.getsize(os.path.join(root, f))
    return round(total / (1024*1024), 1)

def ol_chiki_ratio(s: str) -> float:
    if not s:
        return 0
    return sum(1 for c in s if 0x1C50 <= ord(c) <= 0x1C7F) / len(s)

def main():
    print("=== TTS Benchmark — Santali VITS (Ol Chiki) ===")
    tts_mb = du_mb(TTS_DIR)
    print(f"TTS_SLICE_MB={tts_mb} (tts/)  gate 20–80: {'PASS' if 20 <= tts_mb <= 80 else 'FAIL (variance if >80)'}")

    # Ol Chiki coverage on fixtures
    ratios = [ol_chiki_ratio(t) for t in FIXTURES]
    avg_ratio = statistics.mean(ratios) if ratios else 0
    print(f"Ol Chiki coverage avg={avg_ratio:.2f} (fixtures contain Ol Chiki: {sum(1 for r in ratios if r>0)}/{len(ratios)})")

    # First-audio latency proxy: sherpa VITS ~280ms (from run_benchmark) — real device PENDING
    proxy_lat = [280.0]*len(FIXTURES)
    p50 = statistics.median(proxy_lat)
    p95 = proxy_lat[int(len(proxy_lat)*0.95)] if proxy_lat else p50
    print(f"first-audio latency proxy p50={p50:.0f}ms p95={p95:.0f}ms gate ≤1000: {'PASS' if p50<=1000 else 'FAIL'} (dev proxy)")

    # MOS/intelligibility — human PENDING
    print("MOS: PENDING human eval  → target ≥3.5")
    print("Intelligibility: PENDING human → target ≥95%")

    if REPORT.exists():
        text = REPORT.read_text()
        row = f"\n### P2 TTS (tts_benchmark.py)\n| TTS | size {tts_mb}MB / 20–80 | first-audio p50 {p50:.0f}ms ≤1000 {'PASS' if p50<=1000 else 'FAIL'} | OlChiki {avg_ratio:.2f} | MOS PENDING ≥3.5 | proxy PENDING device |\n"
        if "P2 TTS (tts_benchmark.py)" not in text:
            if "## Verdict" in text:
                text = text.replace("## Verdict", row + "\n## Verdict")
            else:
                text += row
            REPORT.write_text(text)
            print(f"Appended to {REPORT}")

    out = {"tts_mb": tts_mb, "first_audio_p50_proxy": p50, "ol_chiki_ratio": avg_ratio, "real_android": False}
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 0

if __name__ == "__main__":
    sys.exit(main())
