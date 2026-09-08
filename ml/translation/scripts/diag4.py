#!/usr/bin/env python3
import numpy as np, torch, onnxruntime as ort
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor

M="ai4bharat/indictrans2-indic-indic-dist-320M"; O="/home/clutch/Desktop/Vachak/ml/models/it2_onnx_fp32"
dev="cpu"
proc=IndicProcessor(inference=True); tok=AutoTokenizer.from_pretrained(M,trust_remote_code=True)
model=AutoModelForSeq2SeqLM.from_pretrained(M,trust_remote_code=True).to(dev).float()
dec=model.model.decoder; lm=model.lm_head
so=ort.SessionOptions(); so.graph_optimization_level=ort.GraphOptimizationLevel.ORT_DISABLE_ALL
es=ort.InferenceSession(f"{O}/encoder_model.onnx",so,providers=["CPUExecutionProvider"])
ds=ort.InferenceSession(f"{O}/decoder_model.onnx",so,providers=["CPUExecutionProvider"])
dp=ort.InferenceSession(f"{O}/decoder_with_past_model.onnx",so,providers=["CPUExecutionProvider"])
NL=18
pnames=[f"past_key_values.{l}.decoder.key" for l in range(NL)]+[f"past_key_values.{l}.decoder.value" for l in range(NL)]+ \
      [f"past_key_values.{l}.encoder.key" for l in range(NL)]+[f"past_key_values.{l}.encoder.value" for l in range(NL)]

ip=proc.preprocess_batch(["नमस्ते बच्चों।"],src_lang="hin_Deva",tgt_lang="sat_Olck",is_target=False)
e=tok(ip,return_tensors="pt",padding=True,truncation=True).to(dev)
with torch.no_grad():
    enc=model.get_encoder()(input_ids=e.input_ids,attention_mask=e.attention_mask).last_hidden_state
enc_np=enc.to("cpu").numpy(); amask=e.attention_mask.to("cpu").numpy()
enc_o=es.run(None,{"input_ids":e.input_ids.to("cpu").numpy(),"attention_mask":amask})[0]

# HF cached
with torch.no_grad():
    inp=torch.tensor([[2]],device=dev); past=None; hf=[2]
    for _ in range(8):
        o=dec(input_ids=inp,encoder_hidden_states=enc,encoder_attention_mask=e.attention_mask,past_key_values=past,use_cache=True)
        n=int(lm(o.last_hidden_state)[:,-1,:].argmax())
        if n==2: break
        hf.append(n); inp=torch.tensor([[n]],device=dev); past=o.past_key_values
print("HF cached:", hf)

# ONNX cached
r=ds.run(None,{"input_ids":np.array([[2]],dtype=np.int64),"encoder_attention_mask":amask,"encoder_hidden_states":enc_o})
on=[2]; past=r[1:]
for _ in range(8):
    feeds={"input_ids":np.array([[on[-1]]],dtype=np.int64),"encoder_attention_mask":amask}
    for nm,x in zip(pnames,past): feeds[nm]=x
    rr=dp.run(None,feeds)
    n=int(np.argmax(rr[0][0,-1]))
    if n==2: break
    on.append(n); past=rr[1:]
print("ONNX cached:", on)
