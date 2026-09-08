#!/usr/bin/env python3
"""Benchmark a HuggingFace IndicTrans2 model (base or merged). Reports latency
(warmup + p50/p95) and, if --ref given, chrF/BLEU vs references.

Usage:
  python benchmark.py --model <hf_dir_or_ckpt> --src hindi.txt [--ref santali.txt] --out report.txt
"""
import os
import sys
import time
import argparse
import statistics
import torch
from IndicTransToolkit import IndicProcessor
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from sacrebleu.metrics import CHRF, BLEU


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--src", required=True)
    ap.add_argument("--ref", default=None)
    ap.add_argument("--out", default=None)
    ap.add_argument("--bs", type=int, default=8)
    ap.add_argument("--warmup", type=int, default=3)
    args = ap.parse_args()

    srcs = [l.rstrip("\n").strip() for l in open(args.src, encoding="utf-8") if l.strip()]
    refs = None
    if args.ref:
        refs = [l.rstrip("\n").strip() for l in open(args.ref, encoding="utf-8") if l.strip()]

    print(f" | > loading {args.model}", flush=True)
    tok = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(
        args.model, trust_remote_code=True, attn_implementation="eager").to("cuda").half()
    model.eval()
    proc = IndicProcessor(inference=True)

    def translate(batch):
        proc_src = proc.preprocess_batch(batch, src_lang="hin_Deva", tgt_lang="sat_Olck",
                                          is_target=False)
        enc = tok(proc_src, return_tensors="pt", padding=True, truncation=True,
                  max_length=256).to("cuda")
        with torch.no_grad():
            out = model.generate(**enc, num_beams=5, max_new_tokens=256)
        dec = tok.batch_decode(out, skip_special_tokens=True)
        proc_tgt = proc.postprocess_batch(dec, lang="sat_Olck")
        return proc_tgt

    # warmup
    for i in range(args.warmup):
        translate(srcs[: min(args.bs, len(srcs))])

    times = []
    preds = []
    for i in range(0, len(srcs), args.bs):
        b = srcs[i:i + args.bs]
        t0 = time.perf_counter()
        p = translate(b)
        torch.cuda.synchronize()
        times.append((time.perf_counter() - t0) / len(b))
        preds += p

    p50 = statistics.median(times) * 1000
    p95 = sorted(times)[int(len(times) * 0.95)] * 1000
    print(f" | > sentences={len(srcs)} latency p50={p50:.1f}ms p95={p95:.1f}ms/sentence", flush=True)

    rep = []
    rep.append(f"model={args.model}")
    rep.append(f"sentences={len(srcs)}")
    rep.append(f"latency_p50_ms={p50:.1f}")
    rep.append(f"latency_p95_ms={p95:.1f}")
    if refs and len(refs) == len(preds):
        chrf = CHRF().corpus_score(preds, [refs]).score
        bleu = BLEU().corpus_score(preds, [refs]).score
        rep.append(f"chrF={chrf:.2f}")
        rep.append(f"BLEU={bleu:.2f}")
        print(f" | > chrF={chrf:.2f} BLEU={bleu:.2f}", flush=True)
    for s, p in zip(srcs, preds):
        rep.append(f"HIN: {s}")
        rep.append(f"SAT: {p}")
    out = "\n".join(rep)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(out + "\n")
        print(f" | > wrote {args.out}", flush=True)
    else:
        print(out)


if __name__ == "__main__":
    sys.exit(main())
