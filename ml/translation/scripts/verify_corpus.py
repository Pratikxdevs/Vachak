#!/usr/bin/env python3
"""Double-verify the generated Hindi<->Santali corpus against the BASE
IndicTrans2 model (ai4bharat/indictrans2-indic-indic-dist-320M).

For every SILVER pair we run two cross-checks with the frozen base model
(greedy, anti-loop settings identical to infer_it2.py):
  1. hi2sat : translate the Hindi source -> model_sat ; compare to our sat.
  2. sat2hi : translate our sat -> model_hi ; compare to our Hindi source.

Agreement is measured with token-level F1 (longest-common after whitespace
split; Ol Chiki words are space-separated). A pair is VERIFIED when BOTH
cross-checks pass (F1 >= THRESH); otherwise it goes to REVIEW (either our
generated Santali is wrong, or the base model is wrong -- both need a human).

This is NOT a guarantee of correctness: the base model can also be wrong, so
agreement only means "our generation is consistent with the model's knowledge".
Disagreement is a useful flag for human review. Gold pairs are trusted by
construction and copied through.

Outputs (datasets/hin_sat/):
  corpus.verified.tsv   hi<tab>sat  (passes both cross-checks)
  corpus.review.tsv     hi<tab>sat  (flagged for human review, with reason)
  verify_report.json    counts, F1 distribution, per-category review rate
"""
from __future__ import annotations
import json, sys
from pathlib import Path
from collections import Counter

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor

MODEL = "ai4bharat/indictrans2-indic-indic-dist-320M"
CORPUS = Path("datasets/hin_sat/corpus.jsonl")
OUT_DIR = Path("datasets/hin_sat")
BATCH = 64
MAX_NEW = 64
THRESH = 0.7


def tok_f1(a: str, b: str) -> float:
    a = a.strip().split()
    b = b.strip().split()
    if not a and not b:
        return 1.0
    if not a or not b:
        return 0.0
    sa, sb = Counter(a), Counter(b)
    inter = sum((sa & sb).values())
    if inter == 0:
        return 0.0
    prec = inter / len(a)
    rec = inter / len(b)
    return 2 * prec * rec / (prec + rec)


@torch.inference_mode()
def translate(texts, src, tgt, tok, proc, model):
    out = []
    for i in range(0, len(texts), BATCH):
        chunk = texts[i:i + BATCH]
        prefixed = proc.preprocess_batch(chunk, src_lang=src, tgt_lang=tgt, is_target=False)
        enc = tok(prefixed, return_tensors="pt", padding=True, truncation=True).to(model.device)
        gen = model.generate(**enc, num_beams=1, do_sample=False, max_new_tokens=MAX_NEW,
                             repetition_penalty=1.2, no_repeat_ngram_size=3)
        with tok.as_target_tokenizer():
            dec = tok.batch_decode(gen, skip_special_tokens=True)
        out.extend(proc.postprocess_batch(dec, lang=tgt))
    return out


def main():
    rows = [json.loads(l) for l in open(CORPUS, encoding="utf-8")]
    silver = [r for r in rows if r["tier"] == "silver"]
    gold = [r for r in rows if r["tier"] != "silver"]
    print(f"[verify] silver={len(silver)} gold={len(gold)}", flush=True)

    tok = AutoTokenizer.from_pretrained(MODEL, trust_remote_code=True)
    proc = IndicProcessor(inference=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(
        MODEL, trust_remote_code=True, torch_dtype=torch.float16).to("cuda").eval()

    hi_src = [r["hi"] for r in silver]
    sat_src = [r["sat"] for r in silver]

    print("[verify] hi->sat cross-check ...", flush=True)
    model_sat = translate(hi_src, "hin_Deva", "sat_Olck", tok, proc, model)
    print("[verify] sat->hi cross-check ...", flush=True)
    model_hi = translate(sat_src, "sat_Olck", "hin_Deva", tok, proc, model)

    verified, review = [], []
    f1_hi, f1_sat = [], []
    cat_review = Counter()
    for r, ms, mh in zip(silver, model_sat, model_hi):
        f1s = tok_f1(ms, r["sat"])
        f1h = tok_f1(mh, r["hi"])
        f1_sat.append(f1s)
        f1_hi.append(f1h)
        if f1s >= THRESH and f1h >= THRESH:
            verified.append(r)
        else:
            reason = []
            if f1s < THRESH:
                reason.append(f"hi2sat_f1={f1s:.2f}(model:{ms})")
            if f1h < THRESH:
                reason.append(f"sat2hi_f1={f1h:.2f}(model:{mh})")
            rr = dict(r)
            rr["review_reason"] = "; ".join(reason)
            rr["model_sat"] = ms
            rr["model_hi"] = mh
            review.append(rr)
            cat_review[r["category"]] += 1

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with open(OUT_DIR / "corpus.verified.tsv", "w", encoding="utf-8") as f:
        for r in verified:
            f.write(f"{r['hi']}\t{r['sat']}\n")
    with open(OUT_DIR / "corpus.review.tsv", "w", encoding="utf-8") as f:
        for r in review:
            f.write(f"{r['hi']}\t{r['sat']}\t{r.get('review_reason','')}\n")

    report = {
        "silver_total": len(silver),
        "verified": len(verified),
        "review": len(review),
        "verified_pct": round(100 * len(verified) / max(1, len(silver)), 1),
        "mean_hi2sat_f1": round(sum(f1_sat) / max(1, len(f1_sat)), 3),
        "mean_sat2hi_f1": round(sum(f1_hi) / max(1, len(f1_hi)), 3),
        "review_by_category": dict(cat_review.most_common()),
    }
    with open(OUT_DIR / "verify_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(f"[verify] VERIFIED : {len(verified)} ({report['verified_pct']}%)")
    print(f"[verify] REVIEW   : {len(review)}")
    print(f"[verify] mean hi2sat F1={report['mean_hi2sat_f1']}  sat2hi F1={report['mean_sat2hi_f1']}")
    print(f"[verify] written  : corpus.verified.tsv, corpus.review.tsv, verify_report.json")


if __name__ == "__main__":
    sys.exit(main())
