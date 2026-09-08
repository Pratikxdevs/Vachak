#!/usr/bin/env python3
import sys, torch, numpy as np, onnxruntime as ort
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor

MODEL_ID = "ai4bharat/indictrans2-indic-indic-dist-320M"
ONNX = "/home/clutch/Desktop/Vachak/ml/models/it2_onnx_fp32"
dev = "cuda" if torch.cuda.is_available() else "cpu"
proc = IndicProcessor(inference=True)
tok = AutoTokenizer.from_pretrained(MODEL_ID, trust_remote_code=True)
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_ID, trust_remote_code=True).to(dev).float()
decoder = model.model.decoder
lm_head = model.lm_head

def enc_of(text):
    ip = proc.preprocess_batch([text], src_lang="hin_Deva", tgt_lang="sat_Olck", is_target=False)
    e = tok(ip, return_tensors="pt", padding=True, truncation=True).to(dev)
    with torch.no_grad():
        h = model.get_encoder()(input_ids=e.input_ids, attention_mask=e.attention_mask).last_hidden_state
    return e.input_ids, e.attention_mask, h

so = ort.SessionOptions(); so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
enc_s = ort.InferenceSession(f"{ONNX}/encoder_model.onnx", so, providers=["CPUExecutionProvider"])
dec_s = ort.InferenceSession(f"{ONNX}/decoder_model.onnx", so, providers=["CPUExecutionProvider"])
dec_p = ort.InferenceSession(f"{ONNX}/decoder_with_past_model.onnx", so, providers=["CPUExecutionProvider"])
NL = 18
pnames = [f"past_key_values.{l}.decoder.key" for l in range(NL)] + [f"past_key_values.{l}.decoder.value" for l in range(NL)] + \
         [f"past_key_values.{l}.encoder.key" for l in range(NL)] + [f"past_key_values.{l}.encoder.value" for l in range(NL)]

def onnx_decode(text):
    iids, amask, enc = enc_of(text)
    enc_np = enc.to("cpu").numpy(); amask_np = amask.to("cpu").numpy()
    enc_onnx = enc_s.run(None, {"input_ids": iids.to("cpu").numpy(), "attention_mask": amask_np})[0]
    r = dec_s.run(None, {"input_ids": np.array([[2]], dtype=np.int64),
                         "encoder_attention_mask": amask_np, "encoder_hidden_states": enc_onnx})
    nxt = int(np.argmax(r[0][0, -1])); seq = [2]; past = r[1:]
    for _ in range(48):
        feeds = {"input_ids": np.array([[nxt]], dtype=np.int64), "encoder_attention_mask": amask_np}
        for k, name in enumerate(pnames):
            feeds[name] = past[k]
        rr = dec_p.run(None, feeds)
        nxt = int(np.argmax(rr[0][0, -1]))
        if nxt == 2: break
        seq.append(nxt); past = rr[1:]
    return seq

def hf_cached(text):
    iids, amask, enc = enc_of(text)
    with torch.no_grad():
        inp = torch.tensor([[2]], device=dev); past = None; seq = [2]
        for _ in range(48):
            out = decoder(input_ids=inp, encoder_hidden_states=enc, encoder_attention_mask=amask, past_key_values=past, use_cache=True)
            nxt = int(lm_head(out.last_hidden_state)[:, -1, :].argmax())
            if nxt == 2: break
            seq.append(nxt); inp = torch.tensor([[nxt]], device=dev); past = out.past_key_values
    return seq

for text in ["नमस्ते बच्चों।", "सब मिलकर गिनती करें।", "गाय दूध देती है।"]:
    print("TXT :", text)
    print("HF  :", hf_cached(text))
    print("ONNX:", onnx_decode(text))
    print()
