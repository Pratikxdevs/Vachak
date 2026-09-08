#!/usr/bin/env python3
import sys, torch, numpy as np, onnxruntime as ort
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor

MODEL_ID = "ai4bharat/indictrans2-indic-indic-dist-320M"
ONNX = "/home/clutch/Desktop/Vachak/ml/models/it2_onnx_fp32"
HI = "खाना खा लो।"

dev = "cuda" if torch.cuda.is_available() else "cpu"
proc = IndicProcessor(inference=True)
tok = AutoTokenizer.from_pretrained(MODEL_ID, trust_remote_code=True)
model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_ID, trust_remote_code=True).to(dev).float()

with torch.no_grad():
    ip = proc.preprocess_batch([HI], src_lang="hin_Deva", tgt_lang="sat_Olck", is_target=False)
    ids = tok(ip, return_tensors="pt", padding=True, truncation=True).to(dev)
    enc = model.get_encoder()(input_ids=ids.input_ids, attention_mask=ids.attention_mask).last_hidden_state
    enc = enc.to("cpu").float().numpy()
amask = ids.attention_mask.to("cpu").numpy()

so = ort.SessionOptions(); so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL
enc_s = ort.InferenceSession(f"{ONNX}/encoder_model.onnx", so, providers=["CPUExecutionProvider"])
dec_s = ort.InferenceSession(f"{ONNX}/decoder_model.onnx", so, providers=["CPUExecutionProvider"])

enc_out = enc_s.run(None, {"input_ids": ids.input_ids.to("cpu").numpy(), "attention_mask": amask})[0]
print("enc max diff", float(np.abs(enc - enc_out).max()), "shape", enc_out.shape)

bos = 2
gen = [bos]
for t in range(14):
    dids = np.array([gen], dtype=np.int64)
    logits = dec_s.run(None, {"input_ids": dids, "encoder_attention_mask": amask, "encoder_hidden_states": enc_out})[0]
    nxt = int(np.argmax(logits[0, -1]))
    print(f"step {t}: feed_len={len(gen)} argmax={nxt} gen={gen}")
    if nxt == 2:
        break
    gen.append(nxt)

with torch.no_grad():
    out = model.generate(input_ids=ids.input_ids, attention_mask=ids.attention_mask, num_beams=1,
                          max_new_tokens=24, forced_bos_token_id=2, decoder_start_token_id=2)
    gold = out[0].tolist()
print("GENERATE:", gold)
