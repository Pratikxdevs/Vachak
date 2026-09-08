#!/usr/bin/env python3
"""Re-save base IndicTrans2 as a NATIVE MBartForConditionalGeneration (+MBartConfig, tied
embeddings) so ctranslate2 can convert it without custom code. IndicTrans2 is mBART-style
(top-level encoder/decoder layer_norm, learned positional embeddings). Copies the IndicTrans
tokenizer files so the converter can read the vocabulary.
"""
import os, shutil
import torch
import transformers
from transformers import AutoModelForSeq2SeqLM, MBartConfig, MBartForConditionalGeneration

CKPT = "ai4bharat/indictrans2-indic-indic-dist-320M"
SNAPDIR = "/home/clutch/.cache/huggingface/hub/models--ai4bharat--indictrans2-indic-indic-dist-320M/snapshots/ffb7582b6d43791f1fb26b2153fc065f2e9ea575"
OUT = "/home/clutch/Desktop/Vachak/models/indictrans2_bart"
os.makedirs(OUT, exist_ok=True)

print(" | > loading base (trust_remote_code)", flush=True)
model = AutoModelForSeq2SeqLM.from_pretrained(CKPT, trust_remote_code=True, attn_implementation="eager")
sd = model.state_dict()

d_model = sd["model.encoder.layers.0.self_attn.q_proj.weight"].shape[0]
d_kv = d_model // 8
max_pos = int(model.config.max_source_positions)   # 256
vocab_size = sd["model.encoder.embed_tokens.weight"].shape[0]   # 122706 (incl. added tokens)
print(f" | > d_model={d_model} d_kv={d_kv} max_pos={max_pos} vocab={vocab_size}", flush=True)

# Replicate IndicTransSinusoidalPositionalEmbedding.get_embedding (fairseq formula).
import math
def sinusoidal(num_embeddings, dim):
    half = dim // 2
    emb = math.log(10000) / (half - 1)
    inv = torch.exp(torch.arange(half, dtype=torch.float) * -emb)
    pos = torch.arange(num_embeddings, dtype=torch.float).unsqueeze(1)
    e = pos * inv.unsqueeze(0)
    m = torch.cat([torch.sin(e), torch.cos(e)], dim=1)
    if dim % 2 == 1:
        m = torch.cat([m, torch.zeros(num_embeddings, 1)], dim=1)
    return m

# IndicTrans PE uses offset=2; MBart LearnedPositionalEmbedding also indexes at pos+2.
sin = sinusoidal(max_pos + 2, d_model)            # rows 0..max_pos+1

cfg = MBartConfig(
    vocab_size=vocab_size,
    d_model=d_model,
    encoder_layers=18, decoder_layers=18,
    encoder_attention_heads=8, decoder_attention_heads=8,
    encoder_ffn_dim=2048, decoder_ffn_dim=2048,
    activation_function="gelu",
    normalize_before=False,
    scale_embedding=True,
    tie_word_embeddings=True,
    max_position_embeddings=max_pos + 2,
    d_kv=d_kv,
    bos_token_id=0, eos_token_id=2, pad_token_id=1, decoder_start_token_id=2,
    is_encoder_decoder=True,
)

print(" | > building native MBART model", flush=True)
bart = MBartForConditionalGeneration(cfg)
# MBart sizes embed_positions to max_position_embeddings+2; match its real shape.
n_ep = bart.model.encoder.embed_positions.weight.shape[0]
learned_full = torch.zeros(n_ep, d_model)
learned_full[2:2 + max_pos] = sin[:max_pos]
with torch.no_grad():
    bart.model.encoder.embed_positions.weight.copy_(learned_full)
    bart.model.decoder.embed_positions.weight.copy_(learned_full)

# remap state_dict: tie the three embedding tensors into one shared
# lm_head in the released model is sized to config.vocab_size (122672) while embeddings
# were resized to 122706 (added tokens). Pad lm_head to match so load_state_dict succeeds.
lm_head = sd["lm_head.weight"]
if lm_head.shape[0] < vocab_size:
    lm_head = torch.cat([lm_head, torch.zeros(vocab_size - lm_head.shape[0], lm_head.shape[1])], dim=0)
new_sd = {}
for k, v in sd.items():
    if k == "model.encoder.embed_tokens.weight":
        new_sd["model.shared.weight"] = v
        new_sd["model.encoder.embed_tokens.weight"] = v
        new_sd["model.decoder.embed_tokens.weight"] = v
    elif k == "model.decoder.embed_tokens.weight":
        continue
    elif k == "lm_head.weight":
        new_sd["lm_head.weight"] = lm_head
    else:
        new_sd[k] = v

# final_logits_bias is absent in IndicTrans2 (lm_head has no bias); keep MBart default (zeros).
missing, unexpected = bart.load_state_dict(new_sd, strict=False)
print(f" | > load_state_dict missing={missing} unexpected={unexpected}", flush=True)

print(f" | > saving native MBART -> {OUT}", flush=True)
bart.save_pretrained(OUT)

# copy tokenizer files so converter can read vocabulary
for fn in ["tokenization_indictrans.py", "tokenizer_config.json",
           "special_tokens_map.json", "dict.SRC.json", "dict.TGT.json",
           "model.SRC", "model.TGT"]:
    src = os.path.join(SNAPDIR, fn)
    if os.path.exists(src):
        shutil.copy(src, os.path.join(OUT, fn))
        print(f" | > copied {fn}", flush=True)
print(" | > DONE", flush=True)
