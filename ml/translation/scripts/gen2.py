#!/usr/bin/env python3
"""Generate synthetic Hin<->Sat parallel TSV using the EXACT generate path that
is verified to work (single-sequence greedy, batch=1), mirroring bench2.py.
Writes: OUT (headerless TSV hi<tab>sat).
"""
from __future__ import annotations
import sys, time
import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor

MODEL = "ai4bharat/indictrans2-indic-indic-dist-320M"
OUT = "ml/finetune/data/synthetic_hi_sat.tsv"
MAX_NEW = 128

CURATED_HI = [
    "नमस्ते बच्चों।", "आज का पाठ शुरू करें।", "कृपया अपनी किताब खोलें।",
    "अक्षर 'अ' से शुरू करें।", "सब मिलकर गिनती करें।", "एक, दो, तीन, चार, पाँच।",
    "यह एक सेब है।", "वह लाल रंग है।", "स्कूल जाना अच्छी बात है।",
    "मैं पढ़ना पसंद करता हूँ।", "तुमने यह अच्छा किया।", "कल हमारी छुट्टी है।",
    "पानी पीना सेहत के लिए अच्छा है।", "अपने हाथ धो लें।", "ध्यान से सुनो।",
    "ब्लैकबोर्ड देखो।", "अपना होमवर्क कर लो।", "शिक्षक का सम्मान करो।",
    "पेड़ हरे होते हैं।", "सूरज आकाश में है।", "गाय दूध देती है।",
    "हमारा गाँव बहुत सुंदर है।", "माँ बहुत प्यारी हैं।", "पढ़ाई से बुद्धि बढ़ती है।",
    "आज मौसम साफ़ है।", "खाना खा लो।", "सो जाओ, कल सुबह उठना है।",
    "अक्षर पहचानो।", "शब्दों का अर्थ समझो।", "धीरे-धीरे पढ़ो।",
    "गिनती एक से सौ तक करो।", "यह नीला रंग है।", "वह लड़का दौड़ रहा है।",
    "लड़की किताब पढ़ रही है।", "हमारी भाषा हिंदी है।", "संताली एक सुंदर भाषा है।",
    "तुम कहाँ जा रहे हो?", "मुझे पढ़ना अच्छा लगता है।", "सब लोग यहाँ आएं।",
    "चुपचाप बैठो।", "मेज़ पर किताब रखो।", "खेलने का समय हो गया।",
]

def templates():
    out = []
    numbers = ["एक", "दो", "तीन", "चार", "पाँच", "छः", "सात", "आठ", "नौ", "दस"]
    colors = ["लाल", "नीला", "हरा", "पीला", "काला", "सफ़ेद"]
    fruits = ["सेब", "केला", "आम", "अमरूद", "नारियल"]
    for n in numbers: out.append(f"गिनती: {n}।")
    for c in colors: out.append(f"यह {c} रंग है।")
    for f in fruits: out.append(f"यह एक {f} है।")
    for n in numbers:
        for f in fruits: out.append(f"{n} {f} मेज़ पर हैं।")
    animals = ["गाय", "कुत्ता", "बिल्ली", "हाथी", "घोड़ा", "बंदर"]
    for a in animals: out.append(f"{a} एक जानवर है।")
    verbs = ["दौड़ रहा है", "खा रहा है", "सो रहा है", "पढ़ रहा है", "गा रहा है"]
    for a in animals:
        for v in verbs: out.append(f"{a} {v}।")
    return out

@torch.inference_mode()
def greedy(tok, proc, model, texts, src, tgt):
    out = []
    for text in texts:
        prefixed = proc.preprocess_batch([text], src_lang=src, tgt_lang=tgt, is_target=False)
        e = tok(prefixed, return_tensors="pt", padding=True, truncation=True).to(model.device)
        gen = model.generate(**e, num_beams=1, max_new_tokens=MAX_NEW, do_sample=False,
                             repetition_penalty=1.2, no_repeat_ngram_size=3)
        with tok.as_target_tokenizer():
            txt = tok.decode(gen[0], skip_special_tokens=True)
        out.append(proc.postprocess_batch([txt], lang=tgt)[0])
    return out

def main():
    hi_seeds = CURATED_HI + templates()
    print(f"[gen] {len(hi_seeds)} seeds", flush=True)
    tok = AutoTokenizer.from_pretrained(MODEL, trust_remote_code=True)
    proc = IndicProcessor(inference=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(MODEL, trust_remote_code=True,
                                                  torch_dtype=torch.float16).to("cuda").eval()
    sat = greedy(tok, proc, model, hi_seeds, "hin_Deva", "sat_Olck")
    print(f"[gen] hin->sat done ({len(sat)})", flush=True)
    hin_back = greedy(tok, proc, model, sat, "sat_Olck", "hin_Deva")
    print(f"[gen] sat->hin done ({len(hin_back)})", flush=True)
    pairs = []
    for h, s in zip(hi_seeds, sat):
        if h.strip() and s.strip(): pairs.append((h.strip(), s.strip()))
    for s, h in zip(sat, hin_back):
        if s.strip() and h.strip(): pairs.append((h.strip(), s.strip()))
    with open(OUT, "w", encoding="utf-8") as f:
        for s, t in pairs: f.write(f"{s}\t{t}\n")
    print(f"[gen] wrote {len(pairs)} pairs -> {OUT}", flush=True)

if __name__ == "__main__":
    t0 = time.time(); main(); print(f"[gen] elapsed {time.time()-t0:.0f}s", flush=True)
