#!/usr/bin/env python3
import sys, time, argparse
import numpy as np
import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor
import onnxruntime as ort

DEFAULT_HI = [
    "नमस्ते बच्चों।", "आज का पाठ शुरू करें।", "कृपया अपनी किताब खोलें।",
    "अक्षर 'अ' से शुरू करें।", "सब मिलकर गिनती करें।", "एक, दो, तीन, चार, पाँच।",
    "यह एक सेब है।", "वह लाल रंग है।", "स्कूल जाना अच्छी बात है।",
    "मैं पढ़ना पसंद करता हूँ।", "तुमने यह अच्छा किया।", "पानी पीना सेहत के लिए अच्छा है।",
    "अपने हाथ धो लें।", "ध्यान से सुनो।", "ब्लैकबोर्ड देखो।",
    "पेड़ हरे होते हैं।", "गाय दूध देती है।", "हमारा गाँव बहुत सुंदर है।",
    "संताली एक सुंदर भाषा है।", "तुम कहाँ जा रहे हो?", "खाना खा लो।",
]

def load_sessions(d):
    opts = ort.SessionOptions(); opts.log_severity_level = 3
    enc = ort.InferenceSession(str(d / "encoder_model.onnx"), sess_options=opts)
    dec = ort.InferenceSession(str(d / "decoder_model.onnx"), sess_options=opts)
    return enc, dec

def greedy_onnx(enc, dec, texts, src, tgt, tok, proc, max_new=128):
    out = []
    for text in texts:
        prefixed = proc.preprocess_batch([text], src_lang=src, tgt_lang=tgt, is_target=False)
        e = tok(prefixed, return_tensors="pt", padding=True, truncation=True)
        ids = e.input_ids.numpy().astype(np.int64)
        amask = e.attention_mask.numpy().astype(np.int64)
        enc_out = enc.run(["last_hidden_state"], {"input_ids": ids, "attention_mask": amask})[0]
        gen = [2]
        for _ in range(max_new):
            di = np.array([gen], dtype=np.int64)
            r = dec.run(None, {"input_ids": di, "encoder_attention_mask": amask, "encoder_hidden_states": enc_out})
            nxt = int(np.argmax(r[0][0, -1, :]))
            if nxt == 2:
                break
            gen.append(nxt)
        with tok.as_target_tokenizer():
            txt = tok.decode(gen, skip_special_tokens=True)
        out.append(proc.postprocess_batch([txt], lang=tgt)[0])
    return out

def greedy_pt(model, tok, proc, texts, src, tgt, max_new=128):
    out = []
    for text in texts:
        prefixed = proc.preprocess_batch([text], src_lang=src, tgt_lang=tgt, is_target=False)
        e = tok(prefixed, return_tensors="pt", padding=True, truncation=True).to(model.device)
        with torch.inference_mode():
            gen = model.generate(**e, num_beams=1, max_new_tokens=max_new, do_sample=False,
                                  forced_bos_token_id=2, decoder_start_token_id=2)
        with tok.as_target_tokenizer():
            txt = tok.decode(gen[0], skip_special_tokens=True)
        out.append(proc.postprocess_batch([txt], lang=tgt)[0])
    return out

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--onnx_dir", required=True, type=__import__("pathlib").Path)
    ap.add_argument("--pt_model", default="ai4bharat/indictrans2-indic-indic-dist-320M")
    ap.add_argument("--finetuned_dir", default=None)
    ap.add_argument("--src", default="hin_Deva"); ap.add_argument("--tgt", default="sat_Olck")
    a = ap.parse_args()
    texts = DEFAULT_HI
    tok = AutoTokenizer.from_pretrained(a.pt_model, trust_remote_code=True)
    proc = IndicProcessor(inference=True)
    pt_model = AutoModelForSeq2SeqLM.from_pretrained(
        a.finetuned_dir or a.pt_model, trust_remote_code=True, torch_dtype=torch.float16).to("cuda").eval()
    enc, dec = load_sessions(a.onnx_dir)
    t0 = time.perf_counter(); onnx_out = greedy_onnx(enc, dec, texts, a.src, a.tgt, tok, proc); t_onnx = (time.perf_counter()-t0)*1000/len(texts)
    t0 = time.perf_counter(); pt_out = greedy_pt(pt_model, tok, proc, texts, a.src, a.tgt); t_pt = (time.perf_counter()-t0)*1000/len(texts)
    eq = sum(1 for o, p in zip(onnx_out, pt_out) if o.strip() == p.strip())
    print("\n=== TRANSLATIONS ===")
    for h, o, p in zip(texts, onnx_out, pt_out):
        m = "OK " if o.strip() == p.strip() else "DIFF"
        print(f"[{m}] HI: {h}\n     ONNX: {o}\n     PT  : {p}")
    size = sum(f.stat().st_size for f in a.onnx_dir.iterdir() if f.suffix in (".onnx", ".data"))/1e6
    print(f"\n=== RESULT ===\nexact: {eq}/{len(texts)} = {100*eq/len(texts):.1f}%")
    print(f"size : {size:.0f} MB\nlatency ONNX/PT: {t_onnx:.0f}/{t_pt:.0f} ms")

if __name__ == "__main__":
    main()
