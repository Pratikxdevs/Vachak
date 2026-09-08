#!/usr/bin/env python3
"""Eval base vs finetuned on the bidirectional gold dev split.

Detects each pair's source script (Devanagari=Hindi, Ol Chiki=Santali) and
translates in the correct direction before scoring BLEU/chrF vs the reference.
"""
from __future__ import annotations
import sys
from sacrebleu.metrics import BLEU, CHRF

sys.path.insert(0, ".")
from infer_it2 import IndicTrans2Translator  # noqa: E402

DEVA = set(range(0x0900, 0x0980))
OLCK = set(range(0x1C50, 0x1C80))


def src_lang(text: str) -> str:
    n_dev = sum(c in DEVA for c in text)
    n_ol = sum(c in OLCK for c in text)
    return "hin_Deva" if n_dev >= n_ol else "sat_Olck"


def looped(text, n=4):
    toks = text.split()
    return any(toks[i] == toks[i+1] == toks[i+2] == toks[i+3] for i in range(len(toks)-3)) or len(toks) > 40


def main():
    dev = "/home/clutch/Desktop/Vachak/ml/finetune/data/it2_goldverified_dev.tsv"
    srcs, refs = [], []
    for line in open(dev, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        h, s = line.split("\t")
        srcs.append(h.strip()); refs.append(s.strip())

    base = IndicTrans2Translator(device="cuda")
    ft = IndicTrans2Translator(adapter_dir="/home/clutch/Desktop/Vachak/ml/finetune/it2_goldverified_lora", device="cuda")

    base_out, ft_out = [], []
    for s in srcs:
        lg = src_lang(s)
        tgt = "sat_Olck" if lg == "hin_Deva" else "hin_Deva"
        base_out.append(base.translate([s], lg, tgt)[0])
        ft_out.append(ft.translate([s], lg, tgt)[0])

    bleu, chrf = BLEU(), CHRF()
    print(f"BASE : BLEU={bleu.corpus_score(base_out, [refs]).score:.2f}  chrF={chrf.corpus_score(base_out, [refs]).score:.2f}")
    print(f"FT   : BLEU={bleu.corpus_score(ft_out, [refs]).score:.2f}  chrF={chrf.corpus_score(ft_out, [refs]).score:.2f}")
    b_loop = sum(1 for o in base_out if looped(o))
    f_loop = sum(1 for o in ft_out if looped(o))
    print(f"loops: base={b_loop}/45  ft={f_loop}/45")
    exact = sum(1 for o, r in zip(ft_out, refs) if o.strip() == r.strip())
    print(f"FT exact-match vs gold: {exact}/45")

    # per direction
    for direction, lg in (("Hin->Sat", "hin_Deva"), ("Sat->Hin", "sat_Olck")):
        idx = [i for i, s in enumerate(srcs) if src_lang(s) == lg]
        if not idx:
            continue
        bo = [base_out[i] for i in idx]; fo = [ft_out[i] for i in idx]; rf = [refs[i] for i in idx]
        print(f"  [{direction}] n={len(idx)}  BASE BLEU={bleu.corpus_score(bo,[rf]).score:.2f}  FT BLEU={bleu.corpus_score(fo,[rf]).score:.2f}  FT chrF={chrf.corpus_score(fo,[rf]).score:.2f}")

    print("\n--- samples (mixed direction) ---")
    for s, r, b, f in list(zip(srcs, refs, base_out, ft_out))[:10]:
        print(f"SRC : {s}\nREF: {r}\nBASE:{b}\nFT  :{f}\n")


if __name__ == "__main__":
    main()
