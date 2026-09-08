#!/usr/bin/env python3
"""Compare IndicTrans2 ONNX bundle vs the PyTorch reference (non-cache greedy).

Non-cache greedy: at each step feed the FULL decoder sequence + real encoder
hidden states to decoder_model (no KV cache). This is exactly model.generate's
math, so parity reflects export/quant fidelity, not decode-bookkeeping bugs.
"""
from __future__ import annotations

import argparse
import logging
import time
from pathlib import Path

import numpy as np
import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger(__name__)

DEFAULT_HI = [
    "नमस्ते बच्चों।", "आज का पाठ शुरू करें।", "कृपया अपनी किताब खोलें।",
    "अक्षर 'अ' से शुरू करें।", "सब मिलकर गिनती करें।", "एक, दो, तीन, चार, पाँच।",
    "यह एक सेब है।", "वह लाल रंग है।", "स्कूल जाना अच्छी बात है।",
    "मैं पढ़ना पसंद करता हूँ।", "तुमने यह अच्छा किया।", "पानी पीना सेहत के लिए अच्छा है।",
    "अपने हाथ धो लें।", "ध्यान से सुनो।", "ब्लैकबोर्ड देखो।",
    "पेड़ हरे होते हैं।", "गाय दूध देती है।", "हमारा गाँव बहुत सुंदर है।",
    "संताली एक सुंदर भाषा है।", "तुम कहाँ जा रहे हो?", "खाना खा लो।",
]


def load_sessions(d: Path):
    import onnxruntime as ort
    opts = ort.SessionOptions()
    opts.log_severity_level = 3
    enc = ort.InferenceSession(str(d / "encoder_model.onnx"), sess_options=opts)
    dec = ort.InferenceSession(str(d / "decoder_model.onnx"), sess_options=opts)
    return enc, dec


def greedy_onnx(enc, dec, texts, src, tgt, tok, proc, max_new=128):
    out = []
    for text in texts:
        prefixed = proc.preprocess_batch([text], src_lang=src, tgt_lang=tgt, is_target=False)
        ids = tok.encode(prefixed[0], add_special_tokens=False)
        input_ids = np.array([ids], dtype=np.int64)
        amask = np.ones((1, len(ids)), dtype=np.int64)
        enc_out = enc.run(["last_hidden_state"], {"input_ids": input_ids, "attention_mask": amask})[0]
        gen = [2]
        for _ in range(max_new):
            di = np.array([gen], dtype=np.int64)
            r = dec.run(None, {"input_ids": di, "encoder_hidden_states": enc_out,
                               "encoder_attention_mask": amask})
            logits = r[0]
            nxt = int(np.argmax(logits[0, -1, :]))
            gen.append(nxt)
            if nxt == 2:
                break
        with tok.as_target_tokenizer():
            txt = tok.decode(gen, skip_special_tokens=True)
        out.append(proc.postprocess_batch([txt], lang=tgt)[0])
    return out


def greedy_pt(model, tok, proc, texts, src, tgt, max_new=128):
    out = []
    for text in texts:
        prefixed = proc.preprocess_batch([text], src_lang=src, tgt_lang=tgt, is_target=False)
        enc = tok(prefixed, return_tensors="pt", padding=True, truncation=True).to(model.device)
        with torch.inference_mode():
            gen = model.generate(**enc, num_beams=1, max_new_tokens=max_new, do_sample=False)
        with tok.as_target_tokenizer():
            txt = tok.decode(gen[0], skip_special_tokens=True)
        out.append(proc.postprocess_batch([txt], lang=tgt)[0])
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--onnx_dir", required=True, type=Path)
    ap.add_argument("--pt_model", default="ai4bharat/indictrans2-indic-indic-dist-320M")
    ap.add_argument("--finetuned_dir", default=None)
    ap.add_argument("--src", default="hin_Deva")
    ap.add_argument("--tgt", default="sat_Olck")
    ap.add_argument("--fixtures", default=None)
    a = ap.parse_args()

    texts = DEFAULT_HI
    if a.fixtures and Path(a.fixtures).exists():
        texts = [l.strip() for l in open(a.fixtures, encoding="utf-8") if l.strip()]

    tok = AutoTokenizer.from_pretrained(a.pt_model, trust_remote_code=True)
    proc = IndicProcessor(inference=True)
    pt_model = AutoModelForSeq2SeqLM.from_pretrained(
        a.finetuned_dir or a.pt_model, trust_remote_code=True,
        torch_dtype=torch.float16).to("cuda").eval()

    enc, dec = load_sessions(a.onnx_dir)

    t0 = time.perf_counter()
    onnx_out = greedy_onnx(enc, dec, texts, a.src, a.tgt, tok, proc)
    t_onnx = (time.perf_counter() - t0) * 1000.0 / len(texts)

    t0 = time.perf_counter()
    pt_out = greedy_pt(pt_model, tok, proc, texts, a.src, a.tgt)
    t_pt = (time.perf_counter() - t0) * 1000.0 / len(texts)

    tok_eq = sum(1 for o, p in zip(onnx_out, pt_out) if o.strip() == p.strip())
    print("\n=== TRANSLATIONS (Hindi -> Santali) ===")
    for h, o, p in zip(texts, onnx_out, pt_out):
        mark = "OK " if o.strip() == p.strip() else "DIFF"
        print(f"[{mark}] HI: {h}\n     ONNX: {o}\n     PT  : {p}")
    size = sum(f.stat().st_size for f in a.onnx_dir.iterdir() if f.suffix in (".onnx", ".data")) / 1e6
    print(f"\n=== RESULT ===")
    print(f"fixtures           : {len(texts)}")
    print(f"token/text exact   : {tok_eq}/{len(texts)} = {100*tok_eq/len(texts):.1f}%")
    print(f"ONNX bundle size   : {size:.0f} MB")
    print(f"latency ONNX/PT    : {t_onnx:.0f} ms / {t_pt:.0f} ms per sentence")


if __name__ == "__main__":
    main()
