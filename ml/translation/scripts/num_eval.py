#!/usr/bin/env python3
"""Eval numeral correctness on held-out frames + gold-dev regression check."""
from __future__ import annotations
import sys
from sacrebleu.metrics import BLEU, CHRF

sys.path.insert(0, ".")
from infer_it2 import IndicTrans2Translator  # noqa: E402

GV = "/home/clutch/Desktop/Vachak/ml/finetune/data/it2_goldverified_dev.tsv"
NUM = "/home/clutch/Desktop/Vachak/ml/finetune/data/it2_num_eval.tsv"
ADAPTER = "/home/clutch/Desktop/Vachak/ml/finetune/it2_goldnum_lora"


def load(tsv):
    hi, ref = [], []
    for line in open(tsv, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        h, s = line.split("\t")
        hi.append(h.strip()); ref.append(s.strip())
    return hi, ref


def main():
    base = IndicTrans2Translator(device="cuda")
    ft = IndicTrans2Translator(adapter_dir=ADAPTER, device="cuda")

    # --- numeral held-out eval (hi->sat) ---
    hi, ref = load(NUM)
    bp = base.translate(hi)
    fp = ft.translate(hi)
    b_ok = sum(1 for o, r in zip(bp, ref) if o.strip() == r.strip())
    f_ok = sum(1 for o, r in zip(fp, ref) if o.strip() == r.strip())
    print(f"NUMERAL held-out (unseen frames): base exact={b_ok}/{len(hi)}  FT exact={f_ok}/{len(hi)}")
    print("  sample FT mismatches (if any):")
    for h, r, o in list(zip(hi, ref, fp)):
        if o.strip() != r.strip():
            print(f"    HI {h}\n    REF {r}\n    FT  {o}")

    # --- gold dev regression (script-aware direction) ---
    DEVA = set(range(0x0900, 0x0980)); OLCK = set(range(0x1C50, 0x1C80))
    ghi, gref = load(GV)
    def sl(t):
        return "hin_Deva" if sum(c in DEVA for c in t) >= sum(c in OLCK for c in t) else "sat_Olck"
    bgo, fgo = [], []
    for s in ghi:
        lg = sl(s); tg = "sat_Olck" if lg == "hin_Deva" else "hin_Deva"
        bgo.append(base.translate([s], lg, tg)[0]); fgo.append(ft.translate([s], lg, tg)[0])
    bleu, chrf = BLEU(), CHRF()
    print(f"GOLD DEV regression: BASE BLEU={bleu.corpus_score(bgo,[gref]).score:.2f}  "
          f"FT BLEU={bleu.corpus_score(fgo,[gref]).score:.2f}  "
          f"FT chrF={chrf.corpus_score(fgo,[gref]).score:.2f}")


if __name__ == "__main__":
    main()
