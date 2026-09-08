#!/usr/bin/env python3
"""Bidirectional Hindi<->Santali LoRA fine-tune of IndicTrans2. T4-ready.

Self-contained: only needs this file + data/*.tsv + pip requirements.
Survives Studio restarts: every epoch saves latest + best adapters and a
state file; re-run the same command with --resume <output_dir> to continue.

Data: TSV a<TAB>b. Direction inferred per row by script
(Devanagari-first = hi->sat, OlChiki-first = sat->hi).
"""
from __future__ import annotations
import argparse, glob, json, os, random, signal, sys, time
from pathlib import Path

import torch
from torch.utils.data import DataLoader, Dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

STOP = False
def _on_signal(s, f):
    global STOP
    print("[train] signal -> save + exit after this batch", flush=True)
    globals()["STOP"] = True


def _real_processor():
    try:
        from IndicTransToolkit import IndicProcessor
        return IndicProcessor(inference=True)
    except Exception as e:
        print(f"[train] IndicTransToolkit missing ({e})", flush=True)
        sys.exit("pip install indictranstoolkit")


_PROC = None
def indic_preprocess(text, src_lang, tgt_lang, is_target=False):
    global _PROC
    if _PROC is None:
        _PROC = _real_processor()
    if is_target:
        return _PROC.preprocess_batch([text], src_lang=tgt_lang,
                                      tgt_lang=src_lang,
                                      is_target=True)[0]
    return _PROC.preprocess_batch([text], src_lang=src_lang,
                                  tgt_lang=tgt_lang, is_target=False)[0]


def _is_ol_first(s):
    ol = sum(1 for c in s if "᱐" <= c <= "᱿")
    dv = sum(1 for c in s if "\u0900" <= c <= "\u097f")
    return ol > dv


class PairedDataset(Dataset):
    def __init__(self, pairs, tok, tgt_lang, max_len=128):
        self.items = []
        for a, b in pairs:
            if _is_ol_first(a):
                sl, dl, st, dt = tgt_lang, "hin_Deva", a, b
            else:
                sl, dl, st, dt = "hin_Deva", tgt_lang, a, b
            src = indic_preprocess(st, sl, dl, is_target=False)
            tgt = indic_preprocess(dt, dl, sl, is_target=True)
            s = tok(src, return_tensors="pt", padding="max_length",
                    truncation=True, max_length=max_len)
            with tok.as_target_tokenizer():
                t = tok(tgt, return_tensors="pt", padding="max_length",
                        truncation=True, max_length=max_len)
            labels = t["input_ids"].clone()
            labels[labels == tok.pad_token_id] = -100
            self.items.append({"input_ids": s["input_ids"][0],
                               "attention_mask": s["attention_mask"][0],
                               "labels": labels[0]})

    def __len__(self): return len(self.items)
    def __getitem__(self, i): return self.items[i]


def collate(batch):
    return {k: torch.stack([b[k] for b in batch])
            for k in ("input_ids", "attention_mask", "labels")}


def load_pairs(tsv):
    pairs = []
    with open(tsv, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            p = line.split("\t")
            if len(p) >= 2 and p[0].strip() and p[1].strip():
                pairs.append((p[0].strip(), p[1].strip()))
    return pairs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data_dir", default="data")
    ap.add_argument("--model",
                    default="ai4bharat/indictrans2-indic-indic-dist-320M")
    ap.add_argument("--output_dir", default="adapter_sat_bidi")
    ap.add_argument("--tgt_lang", default="sat_Olck")
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--batch_size", type=int, default=8)
    ap.add_argument("--grad_accum", type=int, default=2)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--max_len", type=int, default=128)
    ap.add_argument("--lora_r", type=int, default=64)
    ap.add_argument("--lora_alpha", type=int, default=128)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--resume", default=None)
    a = ap.parse_args()

    signal.signal(signal.SIGTERM, _on_signal)
    signal.signal(signal.SIGINT, _on_signal)

    random.seed(a.seed)
    train = load_pairs(os.path.join(a.data_dir, "train.tsv"))
    dev = load_pairs(os.path.join(a.data_dir, "dev.tsv"))
    print(f"[train] {len(train)} train / {len(dev)} dev -> {a.tgt_lang}",
          flush=True)

    outdir, bestdir = a.output_dir, os.path.join(a.output_dir,
                                                 "best_adapter")
    Path(outdir).mkdir(parents=True, exist_ok=True)
    Path(bestdir).mkdir(parents=True, exist_ok=True)
    state_p = os.path.join(outdir, "training_state.json")
    start_epoch, best_dev = 0, float("inf")
    if os.path.exists(state_p):
        try:
            st = json.load(open(state_p))
            start_epoch = int(st.get("completed_epochs", 0))
            best_dev = float(st.get("best_dev_loss", float("inf")))
            print(f"[train] resume state: epochs={start_epoch} "
                  f"best_dev={best_dev:.3f}", flush=True)
        except Exception as e:
            print(f"[train] bad state ({e}), fresh start", flush=True)

    tok = AutoTokenizer.from_pretrained(a.model, trust_remote_code=True)
    t0 = time.time()
    train_ds = PairedDataset(train, tok, a.tgt_lang, a.max_len)
    dev_ds = PairedDataset(dev, tok, a.tgt_lang, a.max_len)
    print(f"[train] tokenized in {time.time()-t0:.0f}s", flush=True)
    train_dl = DataLoader(train_ds, batch_size=a.batch_size, shuffle=True,
                          collate_fn=collate, num_workers=0)
    dev_dl = DataLoader(dev_ds, batch_size=a.batch_size, shuffle=False,
                        collate_fn=collate, num_workers=0)

    model = AutoModelForSeq2SeqLM.from_pretrained(
        a.model, trust_remote_code=True, torch_dtype=torch.float16).to("cuda")
    model = prepare_model_for_kbit_training(
        model, use_gradient_checkpointing=False)
    lora = LoraConfig(r=a.lora_r, lora_alpha=a.lora_alpha, lora_dropout=0.05,
                      bias="none", task_type="SEQ_2_SEQ_LM",
                      target_modules=["q_proj", "k_proj", "v_proj",
                                      "out_proj", "fc1", "fc2"])
    if a.resume:
        from safetensors.torch import load_file
        cand = glob.glob(f"{a.resume}/adapter_model.safetensors")
        if not cand:
            raise RuntimeError(f"no adapter in {a.resume}")
        saved = load_file(cand[0], device="cpu")
        saved = {k.replace(".lora_A.", ".lora_A.default.")
                  .replace(".lora_B.", ".lora_B.default."): v
                 for k, v in saved.items()}
        model = get_peft_model(model, lora)
        names = {n for n, p in model.named_parameters() if p.requires_grad}
        missing = [n for n in names if n not in saved]
        if missing:
            raise RuntimeError(f"resume FAILED: {len(missing)} missing "
                               f"(e.g. {missing[0]})")
        model.load_state_dict({n: saved[n] for n in names}, strict=False)
        del saved
        print(f"[train] resumed {len(names)} tensors from {a.resume}",
              flush=True)
    else:
        model = get_peft_model(model, lora)
    model.print_trainable_parameters()

    opt = torch.optim.AdamW((p for p in model.parameters()
                             if p.requires_grad), lr=a.lr)
    scaler = torch.cuda.amp.GradScaler()
    last_ep = start_epoch
    for epoch in range(start_epoch, a.epochs):
        if STOP:
            break
        model.train()
        tot, t_ep = 0.0, time.time()
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
            if bi % 200 == 0:
                print(f"[train] ep{epoch+1} b{bi}/{len(train_dl)} "
                      f"loss={out.loss.item():.3f}", flush=True)
            if STOP:
                break
        model.eval()
        dloss = 0.0
        with torch.no_grad():
            for b in dev_dl:
                b = {k: v.to("cuda") for k, v in b.items()}
                with torch.cuda.amp.autocast(dtype=torch.float16):
                    dloss += model(**b).loss.item()
        dloss /= max(1, len(dev_dl))
        print(f"[train] ep{epoch+1}/{a.epochs} train={tot/max(1,len(train_dl)):.3f} "
              f"dev={dloss:.3f} min={(time.time()-t_ep)/60:.1f}", flush=True)
        model.save_pretrained(outdir)
        if dloss < best_dev and not STOP:
            best_dev = dloss
            model.save_pretrained(bestdir)
            print(f"[train] NEW BEST dev={dloss:.3f}", flush=True)
        last_ep = epoch + 1
        json.dump({"completed_epochs": last_ep, "best_dev_loss": best_dev},
                  open(state_p, "w"), indent=1)
        if STOP:
            break
    json.dump({"completed_epochs": last_ep, "best_dev_loss": best_dev},
              open(state_p, "w"), indent=1)
    print(f"[train] DONE latest->{outdir} best(dev={best_dev:.3f})->{bestdir}",
          flush=True)


if __name__ == "__main__":
    main()
