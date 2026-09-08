#!/usr/bin/env python3
"""Download + prepare Hindi->Santali parallel data for IndicTrans2 fine-tuning.

Sources (both CC BY 4.0, gated — license already accepted on HF as clxtch):
  - ainlpml-iitp/COILD-MT-Corpus  : HIN-SAT/Hindi.txt + HIN-SAT/Santali.txt (line-aligned)
  - coild-aikosh/Education_v2     : HIN-sat/source_reviewed/EDU/*.tsv (id|src_hi|tgt_sat|domain)

Output:
  <repo>/datasets/coild/hi_sat.tsv
  <repo>/datasets/education_v2/hi_sat.tsv
  <repo>/datasets/processed/hi_sat.train.tsv   (99%)
  <repo>/datasets/processed/hi_sat.dev.tsv      (1%, ~1K held out, never IN22)
"""
import os
import sys
import random

from huggingface_hub import hf_hub_download, snapshot_download

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DATA_DIR = os.path.join(REPO, "datasets")
COILD_OUT = os.path.join(DATA_DIR, "coild")
EDU_OUT = os.path.join(DATA_DIR, "education_v2")
PROC_OUT = os.path.join(DATA_DIR, "processed")

random.seed(26042)
SEED = 26042

TOKEN = os.environ.get("HF_TOKEN")


def log(*a):
    print("[download]", *a, flush=True)


def load_coild():
    log("COILD: downloading HIN-SAT pair...")
    src = hf_hub_download(
        "ainlpml-iitp/COILD-MT-Corpus",
        "HIN-SAT/Hindi.txt",
        repo_type="dataset",
        token=TOKEN,
    )
    tgt = hf_hub_download(
        "ainlpml-iitp/COILD-MT-Corpus",
        "HIN-SAT/Santali.txt",
        repo_type="dataset",
        token=TOKEN,
    )
    with open(src, encoding="utf-8") as f, open(tgt, encoding="utf-8") as g:
        pairs = []
        for s, t in zip(f, g):
            s, t = s.strip(), t.strip()
            if s and t:
                pairs.append((s, t))
    log(f"  COILD HIN-SAT pairs: {len(pairs)}")
    return pairs


def load_edu():
    log("Education_v2: snapshotting HIN-sat...")
    root = snapshot_download(
        "coild-aikosh/Education_v2",
        repo_type="dataset",
        allow_patterns="HIN-sat/**",
        token=TOKEN,
    )
    pairs = []
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if not fn.endswith(".txt"):
                continue
            fp = os.path.join(dirpath, fn)
            with open(fp, encoding="utf-8") as f:
                for line in f:
                    line = line.rstrip("\n")
                    if not line:
                        continue
                    cols = line.split("\t")
                    if len(cols) < 3:
                        continue
                    s, t = cols[1].strip(), cols[2].strip()
                    if s and t:
                        pairs.append((s, t))
    log(f"  Education_v2 HIN-sat pairs: {len(pairs)}")
    return pairs


def write_tsv(path, pairs):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for s, t in pairs:
            f.write(f"{s}\t{t}\n")
    log(f"  wrote {len(pairs)} -> {path}")


def main():
    coild = load_coild()
    edu = load_edu()

    write_tsv(os.path.join(COILD_OUT, "hi_sat.tsv"), coild)
    write_tsv(os.path.join(EDU_OUT, "hi_sat.tsv"), edu)

    all_pairs = coild + edu
    random.shuffle(all_pairs)
    n_dev = max(500, min(2000, len(all_pairs) // 100))
    dev = all_pairs[:n_dev]
    train = all_pairs[n_dev:]
    log(f"Total pairs: {len(all_pairs)} | train: {len(train)} | dev: {len(dev)}")

    write_tsv(os.path.join(PROC_OUT, "hi_sat.train.tsv"), train)
    write_tsv(os.path.join(PROC_OUT, "hi_sat.dev.tsv"), dev)
    log("DONE")


if __name__ == "__main__":
    sys.exit(main())
