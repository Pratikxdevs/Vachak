#!/usr/bin/env python3
"""Convert the synthetic Hin<->Sat TSV into the train/dev layout expected by
train_lora_qlora.py:  data_dir/{split}/{src}-{tgt}/{split}.{lang}
Raw (non-prefixed) text is written; IndicProcessor prefixes at load time.
"""
from __future__ import annotations
import argparse
import random
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv", required=True)
    ap.add_argument("--out_dir", required=True)
    ap.add_argument("--src", default="hin_Deva")
    ap.add_argument("--tgt", default="sat_Olck")
    ap.add_argument("--dev_frac", type=float, default=0.1)
    ap.add_argument("--seed", type=int, default=42)
    a = ap.parse_args()

    pairs = []
    with open(a.tsv, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            hi, sat = line.split("\t")
            if hi.strip() and sat.strip():
                pairs.append((hi.strip(), sat.strip()))
    random.seed(a.seed)
    random.shuffle(pairs)
    n_dev = max(1, int(len(pairs) * a.dev_frac))
    dev, train = pairs[:n_dev], pairs[n_dev:]

    for split, data in (("train", train), ("dev", dev)):
        d = Path(a.out_dir) / split / f"{a.src}-{a.tgt}"
        d.mkdir(parents=True, exist_ok=True)
        with open(d / f"{split}.{a.src}", "w", encoding="utf-8") as fs, \
             open(d / f"{split}.{a.tgt}", "w", encoding="utf-8") as ft:
            for hi, sat in data:
                fs.write(hi + "\n")
                ft.write(sat + "\n")
    print(f"wrote {len(train)} train / {len(dev)} dev pairs -> {a.out_dir}")


if __name__ == "__main__":
    main()
