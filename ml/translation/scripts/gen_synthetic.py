#!/usr/bin/env python3
"""Forge a synthetic Hindi<->Santali parallel corpus via self-distillation.

Real fine-tuning data (COILD-MT-Corpus / Education_v2) is gated on HF. Per the
project lead's instruction, we forge a placeholder corpus now so the LoRA
pipeline runs end-to-end; real data replaces it tomorrow.

Method: take a curated + templated Hindi seed set (classroom / FLN domain),
translate Hin->Sat with the base IndicTrans2 model, and translate the resulting
Sat back to Hin to grow a bidirectional set. Output: TSV hi\tsat (headerless).
"""
from __future__ import annotations

import sys
import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor


MODEL = "ai4bharat/indictrans2-indic-indic-dist-320M"
OUT = "ml/finetune/data/synthetic_hi_sat.tsv"
BATCH = 1
MAX_NEW = 128

# ---- Curated Hindi classroom / FLN seeds (hi) ----
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


def templates() -> list[str]:
    out: list[str] = []
    numbers = ["एक", "दो", "तीन", "चार", "पाँच", "छः", "सात", "आठ", "नौ", "दस"]
    colors = ["लाल", "नीला", "हरा", "पीला", "काला", "सफ़ेद"]
    fruits = ["सेब", "केला", "आम", "अमरूद", "नारियल"]
    for n in numbers:
        out.append(f"गिनती: {n}।")
    for c in colors:
        out.append(f"यह {c} रंग है।")
    for f in fruits:
        out.append(f"यह एक {f} है।")
    for n in numbers:
        for f in fruits:
            out.append(f"{n} {f} मेज़ पर हैं।")
    animals = ["गाय", "कुत्ता", "बिल्ली", "हाथी", "घोड़ा", "बंदर"]
    for a in animals:
        out.append(f"{a} एक जानवर है।")
    verbs = ["दौड़ रहा है", "खा रहा है", "सो रहा है", "पढ़ रहा है", "गा रहा है"]
    for a in animals:
        for v in verbs:
            out.append(f"{a} {v}।")
    return out


def translate(texts, src, tgt, tok, proc, model):
    prefixed = proc.preprocess_batch(texts, src_lang=src, tgt_lang=tgt, is_target=False)
    out = []
    for i in range(0, len(prefixed), BATCH):
        chunk = prefixed[i : i + BATCH]
        enc = tok(chunk, truncation=True, padding=True, return_tensors="pt",
                  return_attention_mask=True).to(model.device)
        with torch.inference_mode():
            gen = model.generate(**enc, num_beams=1, do_sample=False,
                                 max_new_tokens=MAX_NEW, early_stopping=True,
                                 forced_bos_token_id=2, decoder_start_token_id=2)
        dec = tok.batch_decode(gen, skip_special_tokens=True,
                               clean_up_tokenization_spaces=True)
        out.extend(proc.postprocess_batch(dec, lang=tgt))
    return out


def main() -> None:
    hi_seeds = CURATED_HI + templates()
    print(f"[gen] {len(hi_seeds)} Hindi seeds", flush=True)

    tok = AutoTokenizer.from_pretrained(MODEL, trust_remote_code=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(
        MODEL, trust_remote_code=True, torch_dtype=torch.float16,
    ).to("cuda").eval()
    proc = IndicProcessor(inference=True)

    # Hin -> Sat
    sat = translate(hi_seeds, "hin_Deva", "sat_Olck", tok, proc, model)
    # Sat -> Hin (grow reverse pairs)
    hin_back = translate(sat, "sat_Olck", "hin_Deva", tok, proc, model)

    pairs = []
    for h, s in zip(hi_seeds, sat):
        if h.strip() and s.strip():
            pairs.append((h.strip(), s.strip()))
    for s, h in zip(sat, hin_back):
        if s.strip() and h.strip():
            pairs.append((h.strip(), s.strip()))

    with open(OUT, "w", encoding="utf-8") as f:
        for s, t in pairs:
            f.write(f"{s}\t{t}\n")
    print(f"[gen] wrote {len(pairs)} synthetic pairs -> {OUT}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
