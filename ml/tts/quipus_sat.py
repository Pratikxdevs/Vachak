#!/usr/bin/env python3
"""
quipus_sat.py — Santali voice via Quipus 0.6-speechv2 (Sido male teacher).

Pretrained teacher, NOT from-scratch training (MIT weights, synthetic voice —
no human speaker is cloned; see VOICE_CONSENT.md §9).

Prompt + token recipe follows the official model card (v2):
  prompt:  "Sido: <Ol Chiki text> <audio_start> "
  stop:    <audio_end>
  parse:   <snac_l{L}_c{C}> tokens in FRAME_LAYER_PATTERN windows [0,1,2,2,1,2,2]
  vocode:  SNAC 24kHz (hubertsiuzdak/snac_24khz)

Runs under ml/tts/.venv-tts (torch + transformers + snac + soundfile).
GPU bf16 when available (0.6B fits RTX 3050 4GB), else CPU fp32.

  # validate Sido on 3 phrases (the go/no-go gate):
  ml/tts/.venv-tts/bin/python ml/tts/quipus_sat.py --validate
  # bulk teacher synthesis (resumable) for VITS distillation:
  ml/tts/.venv-tts/bin/python ml/tts/quipus_sat.py --bulk --limit 50
  ml/tts/.venv-tts/bin/python ml/tts/quipus_sat.py --bulk   # all 5284

Gated repo: needs HF accept + HF_TOKEN env (never committed).
"""

from __future__ import annotations

import argparse
import re
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ml" / "tts" / "dataset"))
from transliterate import normalize_mt  # noqa: E402

REPO = "hyperneuronAILabs/quipus-0.6-speechv2"
MODEL_DIR = ROOT / "modelpacks" / "quipus"
TEACHER_DIR = ROOT / "modelpacks" / "quipus-sat-sido"
RUNS = ROOT / "ml" / "tts" / "runs" / "santali_vits"
FRAME_LAYER_PATTERN = [0, 1, 2, 2, 1, 2, 2]
SAMPLE_RATE = 24000
SNAC_REPO = "hubertsiuzdak/snac_24khz"
TOKEN_RE = re.compile(r"<snac_l(\d+)_c(\d+)>")

VALIDATE_CASES = [
    ("johaar", "ᱡᱚᱦᱟᱨ"),
    ("mt-question", "ᱪᱮᱫ ᱱᱚᱣᱟ ᱫᱤᱱ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ?"),
    ("lesson-line", "ᱟᱞᱮ ᱟᱥᱲᱟ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟ"),
]


def load():
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer
    from snac import SNAC

    cuda = torch.cuda.is_available()
    dtype = torch.bfloat16 if cuda else torch.float32
    device = "cuda" if cuda else "cpu"
    print(f"[quipus] loading {MODEL_DIR} ({device}/{dtype}) ...")
    tok = AutoTokenizer.from_pretrained(str(MODEL_DIR), use_fast=False)
    model = AutoModelForCausalLM.from_pretrained(
        str(MODEL_DIR), torch_dtype=dtype,
        attn_implementation="eager").to(device).eval()
    audio_end = tok.convert_tokens_to_ids("<audio_end>")
    snac = SNAC.from_pretrained(SNAC_REPO).eval().to(device)
    print(f"[quipus] ready (audio_end={audio_end})")
    return tok, model, snac, device, audio_end


def synth(tok, model, snac, device, audio_end, text, speaker="Sido",
          temperature=0.7, top_p=0.9, max_tokens=1024):
    import torch

    prompt = f"{speaker}: {text} <audio_start> "
    inputs = tok(prompt, return_tensors="pt").to(device)
    t0 = time.time()
    with torch.no_grad():
        out = model.generate(**inputs, max_new_tokens=max_tokens,
                             temperature=temperature, top_p=top_p,
                             do_sample=True,
                             eos_token_id=[audio_end])
    dt = time.time() - t0
    ids = out[0][inputs.input_ids.shape[1]:].tolist()
    toks = tok.convert_ids_to_tokens(ids)
    parsed = []
    for t in toks:
        m = TOKEN_RE.fullmatch(t)
        if m:
            parsed.append((int(m.group(1)), int(m.group(2))))
    l0, l1, l2 = [], [], []
    i = 0
    while i + 7 <= len(parsed):
        window = parsed[i:i + 7]
        if [layer for layer, _ in window] == FRAME_LAYER_PATTERN:
            codes = [code for _, code in window]
            l0.append(codes[0])
            l1.extend([codes[1], codes[4]])
            l2.extend([codes[2], codes[3], codes[5], codes[6]])
            i += 7
        else:
            i += 1
    if not l0:
        return None, dt, 0
    with torch.no_grad():
        wav = snac.decode([
            torch.tensor([l0], dtype=torch.long, device=device),
            torch.tensor([l1], dtype=torch.long, device=device),
            torch.tensor([l2], dtype=torch.long, device=device),
        ])
    audio = wav.detach().squeeze().float().cpu().numpy()
    return audio, dt, len(l0)


def report(audio, dt):
    import numpy as np

    n = len(audio)
    rms = float((np.asarray(audio) ** 2).mean() ** 0.5)
    return n, n / SAMPLE_RATE, rms, dt


def cmd_validate(args) -> int:
    import soundfile as sf

    tok, model, snac, device, audio_end = load()
    failed = 0
    for name, ol in VALIDATE_CASES:
        audio, dt, frames = synth(tok, model, snac, device, audio_end,
                                  normalize_mt(ol), speaker=args.speaker,
                                  temperature=args.temperature)
        if audio is None:
            print(f"[validate] FAIL {name}: no SNAC frames")
            failed += 1
            continue
        n, dur, rms, _ = report(audio, dt)
        ok = n > 0.2 * SAMPLE_RATE and rms > 0.005
        failed += not ok
        print(f"[validate] {'PASS' if ok else 'FAIL'} {name}: "
              f"{n} samples @ {SAMPLE_RATE}Hz ({dur:.2f}s), wall {dt:.1f}s, "
              f"rms {rms:.4f}, frames {frames} <- {ol[:40]}")
        if args.write_wav:
            p = RUNS / f"quipus_{args.speaker.lower()}_{name}.wav"
            sf.write(str(p), audio, SAMPLE_RATE)
            print(f"[validate] wrote {p}")
    print(f"[validate] {'ALL PASS' if not failed else f'{failed} FAILED'}")
    return 1 if failed else 0


def cmd_bulk(args) -> int:
    import soundfile as sf

    rows = []
    for line in (ROOT / "raw" / "santali_male_native_web"
                 / "santali_male_text.txt").read_text(
                     encoding="utf-8").splitlines():
        if "\t" in line:
            uid, txt = line.split("\t", 1)
            rows.append((uid.strip(), txt.strip()))
    if args.limit:
        rows = rows[:args.limit]
    out_wavs = TEACHER_DIR / "wavs"
    out_wavs.mkdir(parents=True, exist_ok=True)
    manifest = TEACHER_DIR / "manifest.tsv"
    done = set()
    if manifest.exists():
        for line in manifest.read_text(encoding="utf-8").splitlines()[1:]:
            done.add(line.split("\t")[0])
    todo = [(u, t) for u, t in rows if u not in done]
    print(f"[bulk] {len(todo)} to synthesize ({len(done)} cached), "
          f"speaker={args.speaker}")

    tok, model, snac, device, audio_end = load()
    if not manifest.exists():
        with open(manifest, "w", encoding="utf-8") as f:
            f.write("utt_id\twav_path\ttext_olchiki\tspeaker\tdur_s\n")
    # NOTE: Ol Chiki texts come from the canonical TSV (single source).
    ol_map = {}
    for line in (ROOT / "ml" / "tts" / "dataset" / "santali_olchiki.tsv"
                 ).read_text(encoding="utf-8").splitlines()[1:]:
        parts = line.split("\t")
        if len(parts) >= 3:
            ol_map[parts[0]] = parts[2]
    with open(manifest, "a", encoding="utf-8") as f:
        for n, (uid, _) in enumerate(todo):
            ol = ol_map.get(uid, "")
            if not ol:
                print(f"[bulk] SKIP {uid}: no Ol Chiki text")
                continue
            audio, dt, frames = synth(tok, model, snac, device, audio_end,
                                      ol, speaker=args.speaker,
                                      temperature=args.temperature)
            if audio is None:
                print(f"[bulk] FAIL {uid}: no frames")
                continue
            dur = len(audio) / SAMPLE_RATE
            p = out_wavs / f"{uid}.wav"
            sf.write(str(p), audio, SAMPLE_RATE)
            f.write(f"{uid}\twavs/{uid}.wav\t{ol}\t{args.speaker}\t"
                    f"{dur:.2f}\n")
            f.flush()
            if (n + 1) % 25 == 0:
                print(f"[bulk] {n + 1}/{len(todo)} done "
                      f"(last {uid} {dur:.1f}s audio in {dt:.1f}s wall)")
    print(f"[bulk] manifest {manifest}")
    return 0


def main() -> None:
    ap = argparse.ArgumentParser(description="Quipus Santali teacher voice")
    ap.add_argument("--validate", action="store_true")
    ap.add_argument("--bulk", action="store_true")
    ap.add_argument("--speaker", default="Sido")
    ap.add_argument("--temperature", type=float, default=0.7)
    ap.add_argument("--write-wav", action="store_true")
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()
    if args.bulk:
        sys.exit(cmd_bulk(args))
    args.write_wav = True
    sys.exit(cmd_validate(args))


if __name__ == "__main__":
    main()
