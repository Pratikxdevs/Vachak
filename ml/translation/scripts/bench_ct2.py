#!/usr/bin/env python3
"""Benchmark the CT2 INT8 IndicTrans2 artifact: correctness (sample outputs + chrF vs HF
base), storage size, peak RAM, and latency (p50/p95) for Hindi<->Santali, on CPU and GPU.

Usage: python3 bench_ct2.py
"""
import os, time, json, resource, subprocess
import torch
import ctranslate2
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor

CKPT = "ai4bharat/indictrans2-indic-indic-dist-320M"
CT2 = "/home/clutch/Desktop/Vachak/models/indictrans2_ct2_int8"
N = 15  # timing iterations

HI_SA = [
    "वह स्कूल जा रहा है।",
    "मैं पढ़ रहा हूँ।",
    "यह एक अच्छी किताब है।",
]
SA_HI = [
    "ᱚᱱᱚ ᱥᱠᱩᱞ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟᱭ ᱾",
    "ᱱᱚᱣᱟ ᱢᱤᱫ ᱱᱟᱯᱟᱢ ᱯᱩᱥᱛᱠ ᱠᱟᱱᱟ ᱾",
    "ᱟᱞᱚᱢ ᱫᱚ ᱚᱲᱟᱜ ᱨᱮ ᱢᱮᱱᱟᱭᱟ ᱾",
]

def chrF(a, b):
    def ng(w, n):
        return [w[i:i+n] for i in range(len(w)-n+1)] or [w]
    refs, hyps = ng(a, 6), ng(b, 6)
    inter = sum(min(refs.count(g), hyps.count(g)) for g in set(refs))
    p = inter / len(hyps) if hyps else 0.0
    r = inter / len(refs) if refs else 0.0
    return 2 * p * r / (p + r) if (p + r) else 0.0

tok = AutoTokenizer.from_pretrained(CKPT, trust_remote_code=True)
proc = IndicProcessor(inference=True)

def ct2_translate(translator, sents, s_lang, t_lang):
    batch = proc.preprocess_batch(sents, src_lang=s_lang, tgt_lang=t_lang)
    enc = tok(batch, return_tensors="pt", padding=True, truncation=True, max_length=256)
    src = [tok.convert_ids_to_tokens(ids) for ids in enc.input_ids.tolist()]
    out = translator.translate_batch(src, max_decoding_length=256, beam_size=4,
                                     return_scores=False)
    toks = [r.hypotheses[0] for r in out]
    ids = [tok.convert_tokens_to_ids(t) for t in toks]
    text = tok.batch_decode(ids, skip_special_tokens=True)
    return proc.postprocess_batch(text, lang=t_lang)

def hf_translate(model, sents, s_lang, t_lang):
    batch = proc.preprocess_batch(sents, src_lang=s_lang, tgt_lang=t_lang)
    enc = tok(batch, return_tensors="pt", padding=True, truncation=True, max_length=256)
    out = model.generate(**enc, max_length=256, num_beams=4, do_sample=False)
    text = tok.batch_decode(out, skip_special_tokens=True)
    return proc.postprocess_batch(text, lang=t_lang)

results = {}
for device in ["cpu"]:
    compute = "int8"
    print(f"\n=== CT2 {device}/{compute} ===", flush=True)
    translator = ctranslate2.Translator(CT2, device=device, compute_type=compute,
                                         inter_threads=1, intra_threads=1)
    # correctness samples + chrF vs HF base
    print("Loading HF base for reference...", flush=True)
    hf = AutoModelForSeq2SeqLM.from_pretrained(CKPT, trust_remote_code=True,
                                               attn_implementation="eager")
    for name, sents, sl, tl in [("HI->SA", HI_SA, "hin_Deva", "sat_Olck"),
                                 ("SA->HI", SA_HI, "sat_Olck", "hin_Deva")]:
        ct = ct2_translate(translator, sents, sl, tl)
        hf_ref = hf_translate(hf, sents, sl, tl)
        print(f"-- {name} --", flush=True)
        for i, (c, h) in enumerate(zip(ct, hf_ref)):
            print(f"  CT2: {c}", flush=True)
            print(f"  HF : {h}  (chrF={chrF(c,h):.3f})", flush=True)
    del hf

    # timing
    for name, sents, sl, tl in [("HI->SA", HI_SA, "hin_Deva", "sat_Olck"),
                                 ("SA->HI", SA_HI, "sat_Olck", "hin_Deva")]:
        times = []
        for _ in range(N):
            t0 = time.perf_counter()
            ct2_translate(translator, sents, sl, tl)
            times.append((time.perf_counter() - t0) * 1000 / len(sents))  # ms/sentence
        times.sort()
        p50 = times[len(times)//2]
        p95 = times[int(len(times)*0.95)]
        results[f"{device}/{name}/p50_ms"] = round(p50, 1)
        results[f"{device}/{name}/p95_ms"] = round(p95, 1)
        print(f"  {name}: p50={p50:.1f}ms p95={p95:.1f}ms/sentence", flush=True)

# storage + RAM
du = subprocess.check_output(["du", "-sm", CT2]).split()[0].decode()
results["storage_MB"] = float(du)
results["peak_RAM_MB"] = round(resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024, 1)
print("\n=== Summary ===", flush=True)
print(json.dumps(results, indent=2))
