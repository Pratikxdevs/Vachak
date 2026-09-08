#!/usr/bin/env python3
"""Convert a Hindi->Santali TSV (hi\\tsat, headerless) into the directory layout
expected by IndicTrans2's huggingface_interface/train_lora.py:

    out_dir/train/hin_Deva-sat_Olck/train.hin_Deva
    out_dir/train/hin_Deva-sat_Olck/train.sat_Olck
    out_dir/dev/  hin_Deva-sat_Olck/dev.hin_Deva
    out_dir/dev/  hin_Deva-sat_Olck/dev.sat_Olck

IndicProcessor adds the <2sat_Olck> control token itself, so raw text is written.
"""
import os
import sys
import argparse
import random


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="TSV hi\\tsat (headerless)")
    ap.add_argument("--out", required=True, help="IndicTrans2 data_dir")
    ap.add_argument("--src_lang", default="hin_Deva")
    ap.add_argument("--tgt_lang", default="sat_Olck")
    ap.add_argument("--dev_size", type=int, default=1000)
    ap.add_argument("--seed", type=int, default=26042)
    args = ap.parse_args()

    random.seed(args.seed)
    pairs = []
    with open(args.input, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            cols = line.split("\t")
            if len(cols) < 2:
                continue
            s, t = cols[0].strip(), cols[1].strip()
            if s and t:
                pairs.append((s, t))
    random.shuffle(pairs)

    dev = pairs[: args.dev_size]
    train = pairs[args.dev_size :]
    print(f"total={len(pairs)} train={len(train)} dev={len(dev)}", flush=True)

    pair = f"{args.src_lang}-{args.tgt_lang}"

    def write(split, data):
        d = os.path.join(args.out, split, pair)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, f"{split}.{args.src_lang}"), "w", encoding="utf-8") as fs, \
             open(os.path.join(d, f"{split}.{args.tgt_lang}"), "w", encoding="utf-8") as ft:
            for s, t in data:
                fs.write(s + "\n")
                ft.write(t + "\n")

    write("train", train)
    write("dev", dev)
    print("wrote layout under", args.out, flush=True)


if __name__ == "__main__":
    sys.exit(main())
