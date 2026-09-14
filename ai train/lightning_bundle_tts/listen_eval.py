#!/usr/bin/env python3
"""
listen_eval.py — the LISTENING bundle: hear what the distilled VITS learned.

Loads a VITS checkpoint from runs/ and synthesizes fixed Ol Chiki test
sentences (same set as the Sido validation + 2 held-out eval lines), so you
can A/B the student against the teacher references
(runs/quipus_sido_*.wav) by ear.

  python listen_eval.py --checkpoint runs/vits_sat/best_model.pth
  python listen_eval.py   # auto-picks best_model.pth, else latest checkpoint

Output: listen/*.wav + listen/report.txt (durations, rms).
Download the listen/ dir and the runs/quipus_sido_*.wav refs, then listen.
Intelligible Sido-like Santali on ᱡᱚᱦᱟᱨ + questions = training is done.
"""

from __future__ import annotations

import argparse
import glob
import json
import os
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = HERE / "data"
RUNS = HERE / "runs"
LISTEN = HERE / "listen"

FIXED_CASES = [
    ("johaar", "ᱡᱚᱦᱟᱨ"),
    ("mt-question", "ᱪᱮᱫ ᱱᱚᱣᱟ ᱫᱤᱱ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?"),
    ("lesson-line", "ᱟᱞᱮ ᱟᱥᱲᱟ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟ"),
]


def eval_cases():
    splits = json.loads((DATA / "splits.json").read_text(encoding="utf-8"))
    texts = {}
    for line in (DATA / "transcripts.tsv").read_text(
            encoding="utf-8").splitlines():
        if "\t" in line:
            uid, txt = line.split("\t", 1)
            texts[uid.strip()] = txt.strip()
    cases = list(FIXED_CASES)
    for uid in splits["eval"][:2]:
        cases.append((f"heldout-{uid}", texts[uid]))
    return cases


def pick_checkpoint(explicit: str | None) -> tuple[str, str]:
    if explicit:
        ckpt = explicit
    else:
        cands = [str(RUNS / "vits_sat" / "best_model.pth")]
        cands += sorted(glob.glob(str(RUNS / "vits_sat" / "checkpoint_*.pth")))
        cands = [c for c in cands if os.path.exists(c)]
        if not cands:
            raise SystemExit("no checkpoint found — train first (finetune_vits.py)")
        ckpt = cands[0] if cands[0].endswith("best_model.pth") else cands[-1]
    cfg = os.path.join(os.path.dirname(ckpt), "config.json")
    if not os.path.exists(cfg):
        raise SystemExit(f"config.json missing next to {ckpt}")
    return ckpt, cfg


def main() -> None:
    ap = argparse.ArgumentParser(description="Listening eval for distilled VITS")
    ap.add_argument("--checkpoint", default=None)
    ap.add_argument("--use-cuda", action="store_true", default=True)
    args = ap.parse_args()

    import soundfile as sf
    from TTS.utils.synthesizer import Synthesizer

    ckpt, cfg = pick_checkpoint(args.checkpoint)
    print(f"[listen] checkpoint {ckpt}")
    synth = Synthesizer(tts_checkpoint=ckpt, tts_config_path=cfg,
                        use_cuda=args.use_cuda)
    sr = synth.output_sample_rate
    LISTEN.mkdir(parents=True, exist_ok=True)
    report = [f"checkpoint: {ckpt}", f"sample_rate: {sr}"]
    import numpy as np

    for name, text in eval_cases():
        wav = synth.tts(text, split_sentences=False)
        a = np.asarray(wav, dtype=float)
        dur, rms = len(a) / sr, float((a ** 2).mean() ** 0.5)
        p = LISTEN / f"student_{name}.wav"
        sf.write(str(p), a, sr)
        ok = len(a) > 0.2 * sr and rms > 0.005
        line = (f"[listen] {'PASS' if ok else 'FAIL'} {name}: {dur:.2f}s, "
                f"rms {rms:.4f} -> {p}")
        print(line)
        report.append(line)
    refs = sorted(RUNS.glob("quipus_sido_*.wav"))
    report.append(f"sido_refs: {[r.name for r in refs]} "
                  "(A/B these against student_*.wav by ear)")
    (LISTEN / "report.txt").write_text("\n".join(report) + "\n",
                                       encoding="utf-8")
    print("[listen] download listen/ + runs/quipus_sido_*.wav and LISTEN.")


if __name__ == "__main__":
    main()
