#!/usr/bin/env python3
"""Minimal LoRA fine-tune of IndicTrans2 (Hin_Deva -> Sat_Olck) on RTX 3050 4GB.

Avoids HF Seq2SeqTrainer / IndicDataCollator / datasets to dodge the
transformers<->peft<->accelerate version skew (forked DataLoader workers fail
to import `pad_without_fast_tokenizer_warning`). Manual fp16 AMP loop, LoRA
only on q/k/v/out projections. Saves a PEFT adapter.
"""
from __future__ import annotations
import argparse
import math
import random
from pathlib import Path

import torch
from torch.utils.data import DataLoader, Dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor


class PairedDataset(Dataset):
    def __init__(self, pairs, tok, proc, max_len=128):
        self.items = []
        for hi, sat in pairs:
            src = proc.preprocess_batch([hi], src_lang="hin_Deva", tgt_lang="sat_Olck", is_target=False)[0]
            tgt = proc.preprocess_batch([sat], src_lang="sat_Olck", tgt_lang="hin_Deva", is_target=True)[0]
            s = tok(src, return_tensors="pt", padding="max_length", truncation=True, max_length=max_len)
            with tok.as_target_tokenizer():
                t = tok(tgt, return_tensors="pt", padding="max_length", truncation=True, max_length=max_len)
            labels = t["input_ids"].clone()
            labels[labels == tok.pad_token_id] = -100
            self.items.append({
                "input_ids": s["input_ids"][0],
                "attention_mask": s["attention_mask"][0],
                "labels": labels[0],
            })

    def __len__(self):
        return len(self.items)

    def __getitem__(self, i):
        return self.items[i]


def collate(batch):
    return {
        "input_ids": torch.stack([b["input_ids"] for b in batch]),
        "attention_mask": torch.stack([b["attention_mask"] for b in batch]),
        "labels": torch.stack([b["labels"] for b in batch]),
    }


def load_pairs(tsv):
    pairs = []
    with open(tsv, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            hi, sat = line.split("\t")
            if hi.strip() and sat.strip():
                pairs.append((hi.strip(), sat.strip()))
    return pairs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv", required=True)
    ap.add_argument("--dev_tsv", default=None)
    ap.add_argument("--output_dir", required=True)
    ap.add_argument("--epochs", type=int, default=5)
    ap.add_argument("--batch_size", type=int, default=4)
    ap.add_argument("--grad_accum", type=int, default=4)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--max_len", type=int, default=128)
    ap.add_argument("--seed", type=int, default=42)
    a = ap.parse_args()

    random.seed(a.seed)
    train = load_pairs(a.tsv)
    if a.dev_tsv:
        dev = load_pairs(a.dev_tsv)
    else:
        random.shuffle(train)
        n_dev = max(1, int(len(train) * 0.1))
        dev, train = train[:n_dev], train[n_dev:]
    print(f"[ft] {len(train)} train / {len(dev)} dev", flush=True)

    tok = AutoTokenizer.from_pretrained("ai4bharat/indictrans2-indic-indic-dist-320M", trust_remote_code=True)
    proc = IndicProcessor(inference=True)
    train_ds = PairedDataset(train, tok, proc, a.max_len)
    dev_ds = PairedDataset(dev, tok, proc, a.max_len)
    train_dl = DataLoader(train_ds, batch_size=a.batch_size, shuffle=True, collate_fn=collate, num_workers=0)
    dev_dl = DataLoader(dev_ds, batch_size=a.batch_size, shuffle=False, collate_fn=collate, num_workers=0)

    model = AutoModelForSeq2SeqLM.from_pretrained(
        "ai4bharat/indictrans2-indic-indic-dist-320M", trust_remote_code=True,
        attn_implementation="eager", torch_dtype=torch.float16,
    ).to("cuda")
    model = prepare_model_for_kbit_training(model, use_gradient_checkpointing=False)
    lora = LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05, bias="none",
                      task_type="SEQ_2_SEQ_LM",
                      target_modules=["q_proj", "k_proj", "v_proj", "out_proj"])
    model = get_peft_model(model, lora)
    model.print_trainable_parameters()

    opt = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad), lr=a.lr)
    scaler = torch.cuda.amp.GradScaler()

    for epoch in range(a.epochs):
        model.train()
        tot = 0.0
        opt.zero_grad()
        for bi, b in enumerate(train_dl):
            b = {k: v.to("cuda") for k, v in b.items()}
            with torch.cuda.amp.autocast(dtype=torch.float16):
                out = model(**b)
                loss = out.loss / a.grad_accum
            scaler.scale(loss).backward()
            tot += out.loss.item()
            if (bi + 1) % a.grad_accum == 0:
                scaler.unscale_(opt)
                torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
                scaler.step(opt)
                scaler.update()
                opt.zero_grad()
            if bi % 10 == 0:
                print(f"[ft] ep{epoch+1} b{bi} loss={out.loss.item():.3f}", flush=True)
        # dev loss
        model.eval()
        dloss = 0.0
        with torch.no_grad():
            for b in dev_dl:
                b = {k: v.to("cuda") for k, v in b.items()}
                with torch.cuda.amp.autocast(dtype=torch.float16):
                    dloss += model(**b).loss.item()
        print(f"[ft] ep{epoch+1} train_loss={tot/len(train_dl):.3f} dev_loss={dloss/len(dev_dl):.3f}", flush=True)

    Path(a.output_dir).mkdir(parents=True, exist_ok=True)
    model.save_pretrained(a.output_dir)
    print(f"[ft] saved adapter -> {a.output_dir}", flush=True)


if __name__ == "__main__":
    main()
