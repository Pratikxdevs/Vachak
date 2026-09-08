#!/usr/bin/env python3
"""Score a trained adapter on held-out tests (BLEU + chrF, both directions).

Usage:
  python eval.py --adapter adapter_sat_bidi/best_adapter [--model ...] [--data_dir data]
Prints a table: test_classroom / test_general x hi->sat / sat->hi.
"""
from __future__ import annotations
import argparse, os, sys
import torch
from peft import PeftModel
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from sacrebleu.metrics import BLEU, CHRF

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from train import indic_preprocess, _is_ol_first  # noqa: E402

bleu, chrf = BLEU(), CHRF()


def load_rows(path):
    rows = []
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if line.strip():
            a, b = line.split("\t")[:2]
            rows.append((a.strip(), b.strip()))
    return rows


@torch.no_grad()
def translate(model, tok, texts, src_lang, tgt_lang, bs=16, max_len=128):
    outs = []
    for i in range(0, len(texts), bs):
        batch = [indic_preprocess(t, src_lang, tgt_lang, is_target=False)
                 for t in texts[i:i + bs]]
        enc = tok(batch, return_tensors="pt", padding=True, truncation=True,
                  max_length=max_len).to("cuda")
        gen = model.generate(**enc, max_length=max_len, num_beams=1,
                             do_sample=False)
        with tok.as_target_tokenizer():
            outs += [t.strip() for t in tok.batch_decode(
                gen, skip_special_tokens=True)]
    return outs


def score(rows):
    fwd_src = [a for a, b in rows if not _is_ol_first(a)]
    fwd_ref = [b for a, b in rows if not _is_ol_first(a)]
    # test files are stored hi->sat; build both directions explicitly
    return fwd_src, fwd_ref


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--adapter", required=True)
    ap.add_argument("--model",
                    default="ai4bharat/indictrans2-indic-indic-dist-320M")
    ap.add_argument("--data_dir", default="data")
    ap.add_argument("--max_len", type=int, default=128)
    a = ap.parse_args()

    tok = AutoTokenizer.from_pretrained(a.model, trust_remote_code=True)
    base = AutoModelForSeq2SeqLM.from_pretrained(
        a.model, trust_remote_code=True,
        torch_dtype=torch.float16).to("cuda")
    model = PeftModel.from_pretrained(base, a.adapter).to("cuda").eval()

    print(f"{'test':<16}{'dir':<10}{'BLEU':>8}{'chrF':>8}")
    for name in ("test_classroom", "test_general"):
        rows = load_rows(os.path.join(a.data_dir, name + ".tsv"))
        hi = [x[0] for x in rows]
        sat = [x[1] for x in rows]
        for label, srcs, refs, sl, tl in (
                ("hi->sat", hi, sat, "hin_Deva", "sat_Olck"),
                ("sat->hi", sat, hi, "sat_Olck", "hin_Deva")):
            hyps = translate(model, tok, srcs, sl, tl, max_len=a.max_len)
            b = bleu.corpus_score(hyps, [refs]).score
            c = chrf.corpus_score(hyps, [refs]).score
            print(f"{name:<16}{label:<10}{b:>8.1f}{c:>8.1f}", flush=True)


if __name__ == "__main__":
    main()
