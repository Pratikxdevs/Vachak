import os, time, sentencepiece as spm
import ctranslate2, torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit.processor import IndicProcessor

SNAP = "/home/clutch/.cache/huggingface/hub/models--ai4bharat--indictrans2-indic-indic-dist-320M/snapshots/ffb7582b6d43791f1fb26b2153fc065f2e9ea575"
CKPT = "ai4bharat/indictrans2-indic-indic-dist-320M"
CT2 = "/home/clutch/Desktop/Vachak/ml/models/indictrans2_ct2_fp32_v2"

sp_src = spm.SentencePieceProcessor(model_file=os.path.join(SNAP, "model.SRC"))
sp_tgt = spm.SentencePieceProcessor(model_file=os.path.join(SNAP, "model.TGT"))

ip = IndicProcessor(inference=True)
src_lang, tgt_lang = "hin_Deva", "sat_Olck"
tests = [
    "बच्चों, पाँच आम गिनो।",
    "यह कितना है?",
    "लाल रंग क्या है?",
    "एक दो तीन चार पाँच।",
    "सब लोग मिलकर गाओ।",
]

# ---- HF reference ----
tok = AutoTokenizer.from_pretrained(CKPT, trust_remote_code=True)
model = AutoModelForSeq2SeqLM.from_pretrained(CKPT, trust_remote_code=True, attn_implementation="eager", low_cpu_mem_usage=True).half().to("cuda").eval()
print("=== HF reference (cuda) ===")
for sent in tests:
    batch = ip.preprocess_batch([sent], src_lang=src_lang, tgt_lang=tgt_lang)
    inp = tok(batch, truncation=True, padding="longest", return_tensors="pt", return_attention_mask=True).to("cuda")
    with torch.no_grad():
        gen = model.generate(**inp, max_length=256, num_beams=5)
    dec = tok.batch_decode(gen, skip_special_tokens=True, clean_up_tokenization_spaces=True)
    print(f"HIN: {sent}")
    print(f"OLC: {ip.postprocess_batch(dec, lang=tgt_lang)[0]}")
    print()

# ---- CT2 under test ----
translator = ctranslate2.Translator(CT2, device="cpu")
print("=== CT2 (M2M100 loader, no layernorm_embedding) ===")
for sent in tests:
    batch = ip.preprocess_batch([sent], src_lang=src_lang, tgt_lang=tgt_lang)
    toks = sp_src.encode(batch[0], out_type=str)
    t0 = time.time()
    out = translator.translate_batch([toks], beam_size=5, max_decoding_length=256, batch_type="tokens")[0].hypotheses[0]
    dt = time.time() - t0
    detok = sp_tgt.decode(out)
    print(f"HIN: {sent}")
    print(f"RAW: {' '.join(out)}")
    print(f"OLC: {detok}  ({dt*1000:.0f}ms)")
    print()
