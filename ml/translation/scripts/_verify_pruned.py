import ctranslate2
from transformers import AutoTokenizer
from IndicTransToolkit import IndicProcessor
CKPT = "ai4bharat/indictrans2-indic-indic-dist-320M"
CT2 = "/home/clutch/Desktop/Vachak/models/indictrans2_ct2_int8_pruned"
tok = AutoTokenizer.from_pretrained(CKPT, trust_remote_code=True)
proc = IndicProcessor(inference=True)
tr = ctranslate2.Translator(CT2, device="cpu", compute_type="int8", inter_threads=1, intra_threads=1)

def has_od(d, u0, u1):
    return any(u0 <= ord(c) <= u1 for c in d)

HI_SA = ["वह स्कूल जा रहा है।", "मैं पढ़ रहा हूँ।", "यह एक अच्छी किताब है।"]
SA_HI = ["ᱚᱱᱚ ᱥᱠᱩᱞ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟᱭ ᱾", "ᱱᱚᱣᱟ ᱢᱤᱫ ᱱᱟᱯᱟᱢ ᱯᱩᱥᱛᱠ ᱠᱟᱱᱟ ᱾", "ᱟᱞᱚᱢ ᱫᱚ ᱚᱲᱟᱜ ᱨᱮ ᱢᱮᱱᱟᱭᱟ ᱾"]
for name, sents, sl, tl, expect in [("HI->SA", HI_SA, "hin_Deva", "sat_Olck", (0x1C50, 0x1C7F)),
                                      ("SA->HI", SA_HI, "sat_Olck", "hin_Deva", (0x0900, 0x097F))]:
    print(f"-- {name} --", flush=True)
    batch = proc.preprocess_batch(sents, src_lang=sl, tgt_lang=tl)
    enc = tok(batch, return_tensors="pt", padding=True, truncation=True, max_length=256)
    src = [tok.convert_ids_to_tokens(ids) for ids in enc.input_ids.tolist()]
    out = tr.translate_batch(src, max_decoding_length=256, beam_size=4)
    for s, r in zip(sents, out):
        toks = r.hypotheses[0]
        text = proc.postprocess_batch([tok.decode(tok.convert_tokens_to_ids(toks))], lang=tl)[0]
        odia = has_od(text, 0x0B00, 0x0B7F)
        ok_script = has_od(text, *expect)
        print(f"  ODIRA={odia} TGT_SCRIPT={ok_script} | {text}", flush=True)
