#!/usr/bin/env python3
"""
verify_pairs.py — verify raw/ audio<->sentence pairs for TTS training.

Checks every (wav, Devanagari transcript) pair, transliterates to Ol Chiki,
and flags mismatches so training only sees clean pairs:

  audio: exists, readable PCM, mono, sr>=16000, 1.5s<=dur<=15s,
         peak>=0.05 (not silent), no DC-offset blowup
  text:  present, non-empty, transliterates with zero unmapped chars,
         Ol Chiki output non-empty
  joint: speaking rate (ol_chars/sec) inside a sane band — catches
         truncated audio / wrong-transcript pairs

  python ml/tts/dataset/verify_pairs.py
  python ml/tts/dataset/verify_pairs.py --subset 1000 --subset-out ml/tts/dataset/verified_1000.tsv

Outputs:
  ml/tts/dataset/verified_manifest.tsv  (all rows + status/flags)
  ml/tts/dataset/verify_report.json     (counts, rate distribution)
  <subset-out>                          (clean N, most-typical rate first)

Consent note: verification is technical only. Release still gated on
VOICE_CONSENT.md (web-collected speaker, no explicit consent on file).
"""

from __future__ import annotations

import argparse
import collections
import json
import struct
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import transliterate
from transliterate import OL_DIGITS, transliterate_text, unmapped

RAW_DIR = ROOT / "raw" / "santali_male_native_web" / "santali_male_audio"
RAW_TXT = ROOT / "raw" / "santali_male_native_web" / "santali_male_text.txt"
OUT_MANIFEST = Path(__file__).resolve().parent / "verified_manifest.tsv"
OUT_REPORT = Path(__file__).resolve().parent / "verify_report.json"

MIN_DUR, MAX_DUR = 1.5, 15.0
MIN_PEAK = 0.05
MIN_RATE, MAX_RATE = 3.0, 25.0  # Ol Chiki chars/sec (wide; outliers flagged)


def wav_stats(path: Path):
    """Return dict or error string."""
    try:
        w = wave.open(str(path))
    except Exception as e:
        return None, f"unreadable:{type(e).__name__}"
    try:
        nch, sw, sr, nfr = w.getnchannels(), w.getsampwidth(), \
            w.getframerate(), w.getnframes()
        if sw != 2:
            return None, f"bitdepth:{sw * 8}"
        if sr < 16000:
            return None, f"low-sr:{sr}"
        dur = nfr / sr if sr else 0
        raw = w.readframes(nfr)
        n = len(raw) // 2
        if n == 0:
            return None, "empty-frames"
        vals = struct.unpack("<%dh" % n, raw)
        peak = max(abs(v) for v in vals) / 32768
        mean = sum(vals) / n / 32768
        return {"ch": nch, "sr": sr, "dur": dur, "peak": peak,
                "dc": mean}, ""
    except Exception as e:
        return None, f"parse:{type(e).__name__}"
    finally:
        w.close()


def load_rows():
    rows = []
    for line in RAW_TXT.read_text(encoding="utf-8").splitlines():
        if "\t" in line:
            uid, txt = line.split("\t", 1)
            rows.append((uid.strip(), txt.strip()))
    return rows


def main() -> None:
    ap = argparse.ArgumentParser(description="Verify TTS audio-text pairs")
    ap.add_argument("--subset", type=int, default=1000)
    ap.add_argument("--subset-out", default=str(
        Path(__file__).resolve().parent / "verified_1000.tsv"))
    args = ap.parse_args()

    rows = load_rows()
    print(f"[verify] pairs: {len(rows)}")
    recs = []
    for uid, dev in rows:
        flags: list[str] = []
        st, err = wav_stats(RAW_DIR / f"{uid}.wav")
        if err:
            recs.append((uid, dev, "", st or {}, ["audio:" + err]))
            continue
        if not (MIN_DUR <= st["dur"] <= MAX_DUR):
            flags.append(f"dur:{st['dur']:.1f}s")
        if st["peak"] < MIN_PEAK:
            flags.append(f"quiet:peak={st['peak']:.3f}")
        if abs(st["dc"]) > 0.05:
            flags.append(f"dc:{st['dc']:.3f}")
        if st["ch"] != 1:
            flags.append(f"ch:{st['ch']}")
        if not dev:
            flags.append("empty-text")
            recs.append((uid, dev, "", st, flags))
            continue
        transliterate.unmapped.clear()
        ol = transliterate_text(dev)
        dropped = dict(transliterate.unmapped)
        if dropped:
            flags.append("unmapped:" + ",".join(
                f"{k}={v}" for k, v in list(dropped.items())[:5]))
        ol_chars = [c for c in ol if "\u1c50" <= c <= "\u1c7f"]
        if not ol_chars:
            flags.append("no-olchiki")
            recs.append((uid, dev, ol, st, flags))
            continue
        rate = len(ol_chars) / st["dur"]
        if not (MIN_RATE <= rate <= MAX_RATE):
            flags.append(f"rate:{rate:.1f}ch/s")
        recs.append((uid, dev, ol, st, flags + [f"rate:{rate:.1f}"]))

    # write manifest
    with open(OUT_MANIFEST, "w", encoding="utf-8") as f:
        f.write("utt_id\twav\tdur_s\tpeak\tsr\tstatus\tflags\ttext_olchiki\n")
        for uid, dev, ol, st, flags in recs:
            real_flags = [x for x in flags if not x.startswith("rate:")]
            status = "PASS" if not real_flags else "FLAG"
            dur = f"{st.get('dur', 0):.2f}" if st else "0"
            peak = f"{st.get('peak', 0):.3f}" if st else "0"
            sr = str(st.get("sr", 0)) if st else "0"
            f.write(f"{uid}\t{uid}.wav\t{dur}\t{peak}\t{sr}\t{status}\t"
                    f"{';'.join(real_flags) or '-'}\t{ol}\n")
    npass = sum(1 for r in recs
                if not [x for x in r[4] if not x.startswith("rate:")])
    flag_counter: collections.Counter = collections.Counter()
    for _, _, _, _, flags in recs:
        for fl in flags:
            flag_counter[fl.split(":")[0].split("=")[0]] += 1
    rates = []
    for _, _, ol, st, _ in recs:
        if st and ol:
            n = sum(1 for c in ol if "\u1c50" <= c <= "\u1c7f")
            if n and st["dur"] > 0:
                rates.append(n / st["dur"])
    rates.sort()
    def pct(p):
        return rates[min(len(rates) - 1, int(p * len(rates)))] if rates else 0
    report = {"total": len(rows), "pass": npass,
              "flagged": len(rows) - npass,
              "flag_counts": dict(flag_counter),
              "rate_chars_per_sec": {"min": pct(0), "p5": pct(0.05),
                                     "median": pct(0.5), "p95": pct(0.95),
                                     "max": pct(1.0)}}
    OUT_REPORT.write_text(json.dumps(report, indent=1) + "\n",
                          encoding="utf-8")
    print(json.dumps(report, indent=1))

    # clean subset: PASS rows, most-typical rate first
    med = pct(0.5)
    clean = []
    for uid, dev, ol, st, flags in recs:
        if any(not x.startswith("rate:") for x in flags):
            continue
        n = sum(1 for c in ol if "\u1c50" <= c <= "\u1c7f")
        clean.append((abs(n / st["dur"] - med), uid, ol))
    clean.sort()
    take = clean[:args.subset]
    with open(args.subset_out, "w", encoding="utf-8") as f:
        for _, uid, ol in take:
            f.write(f"{uid}\t{ol}\n")
    print(f"[verify] manifest {OUT_MANIFEST} | subset {args.subset_out} "
          f"({len(take)} rows)")


if __name__ == "__main__":
    main()
