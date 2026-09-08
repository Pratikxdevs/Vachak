#!/usr/bin/env python3
"""Overnight bidirectional Hindi<->Santali LoRA fine-tune of IndicTrans2.

Same proven recipe as ml/translation/scripts/finetune_mundari.py (local base
models/indictrans2_bart, manual fp16 AMP loop, LoRA r16 on q/k/v/out, bidir
script-inferred direction), plus overnight necessities:
  - per-epoch adapter saves (latest + best-by-dev-loss), so a morning
    Ctrl-C / kill loses at most the current epoch;
  - --resume from a previous output_dir (continues epoch count + best);
  - --time_budget_s graceful stop (saves and exits before the deadline);
  - SIGTERM/SIGINT handler that saves the latest adapter before exiting.

Data: TSV a<TAB>b rows; with --bidir, Devanagari-first rows train hi->sat and
OlChiki-first rows train sat->hi (script-inferred per row).
"""
from __future__ import annotations
import argparse
import json
import os
import random
import signal
import sys
import time
from pathlib import Path

import torch
from torch.utils.data import DataLoader
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

sys.path.insert(0, str(Path(__file__).resolve().parent))
from finetune_mundari import load_pairs, PairedDataset, collate  # noqa: E402

STOP = False


def _on_signal(signum, frame):
    global STOP
    print(f"[overnight] signal {signum} -> will save + exit after this batch",
          flush=True)
    STOP = True


def save_state(outdir, epoch_done, best_dev):
    with open(os.path.join(outdir, "training_state.json"), "w") as f:
        json.dump({"completed_epochs": epoch_done, "best_dev_loss": best_dev},
                  f, indent=1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--train_tsv", required=True)
    ap.add_argument("--dev_tsv", required=True)
    ap.add_argument("--output_dir", required=True)
    ap.add_argument("--model", default="models/indictrans2_bart")
    ap.add_argument("--tgt_lang", default="sat_Olck")
    ap.add_argument("--bidir", action="store_true")
    ap.add_argument("--epochs", type=int, default=16)
    ap.add_argument("--batch_size", type=int, default=4)
    ap.add_argument("--grad_accum", type=int, default=4)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--max_len", type=int, default=128)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--time_budget_s", type=float, default=27000,
                    help="graceful stop after this many seconds (7.5h)")
    ap.add_argument("--lora_r", type=int, default=32)
    ap.add_argument("--lora_alpha", type=int, default=64)
    ap.add_argument("--target_modules", type=str,
                    default="q_proj,k_proj,v_proj,out_proj,fc1,fc2")
    ap.add_argument("--resume", default=None,
                    help="PEFT adapter dir to resume weights from")
    a = ap.parse_args()

    signal.signal(signal.SIGTERM, _on_signal)
    signal.signal(signal.SIGINT, _on_signal)
    t_start = time.time()

    random.seed(a.seed)
    train = load_pairs(a.train_tsv)
    dev = load_pairs(a.dev_tsv)
    print(f"[overnight] {len(train)} train / {len(dev)} dev -> {a.tgt_lang} "
          f"bidir={a.bidir}", flush=True)

    outdir = a.output_dir
    bestdir = os.path.join(outdir, "best_adapter")
    Path(outdir).mkdir(parents=True, exist_ok=True)
    Path(bestdir).mkdir(parents=True, exist_ok=True)

    start_epoch, best_dev = 0, float("inf")
    state_p = os.path.join(outdir, "training_state.json")
    if os.path.exists(state_p):
        try:
            st = json.load(open(state_p))
            start_epoch = int(st.get("completed_epochs", 0))
            best_dev = float(st.get("best_dev_loss", float("inf")))
            print(f"[overnight] state: completed_epochs={start_epoch} "
                  f"best_dev={best_dev:.3f}", flush=True)
        except Exception as e:
            print(f"[overnight] bad state file, starting fresh ({e})",
                  flush=True)

    tok = AutoTokenizer.from_pretrained(a.model, trust_remote_code=True)
    t_tok = time.time()
    train_ds = PairedDataset(train, tok, a.tgt_lang, a.max_len, bidir=a.bidir)
    dev_ds = PairedDataset(dev, tok, a.tgt_lang, a.max_len, bidir=a.bidir)
    print(f"[overnight] tokenized in {time.time()-t_tok:.0f}s", flush=True)
    train_dl = DataLoader(train_ds, batch_size=a.batch_size, shuffle=True,
                          collate_fn=collate, num_workers=0)
    dev_dl = DataLoader(dev_ds, batch_size=a.batch_size, shuffle=False,
                        collate_fn=collate, num_workers=0)

    model = AutoModelForSeq2SeqLM.from_pretrained(
        a.model, trust_remote_code=True, attn_implementation="eager",
        torch_dtype=torch.float16).to("cuda")
    model = prepare_model_for_kbit_training(model,
                                            use_gradient_checkpointing=False)
    lora = LoraConfig(r=a.lora_r, lora_alpha=a.lora_alpha, lora_dropout=0.05,
                      bias="none", task_type="SEQ_2_SEQ_LM",
                      target_modules=a.target_modules.split(","))
    if a.resume:
        from safetensors.torch import load_file
        import glob as _glob
        cand = _glob.glob(f"{a.resume}/adapter_model.safetensors")
        if not cand:
            raise RuntimeError(f"resume FAILED: no adapter in {a.resume}")
        saved = load_file(cand[0], device="cpu")
        saved = {k.replace(".lora_A.", ".lora_A.default.")
                  .replace(".lora_B.", ".lora_B.default."): v
                 for k, v in saved.items()}
        model = get_peft_model(model, lora)
        names = {n for n, p in model.named_parameters() if p.requires_grad}
        missing = [n for n in names if n not in saved]
        if missing:
            raise RuntimeError(f"resume FAILED: {len(missing)} tensors "
                               f"missing (e.g. {missing[0]})")
        model.load_state_dict({n: saved[n] for n in names}, strict=False)
        del saved
        print(f"[overnight] resumed {len(names)} LoRA tensors from "
              f"{a.resume}", flush=True)
    else:
        model = get_peft_model(model, lora)
    try:
        model = model.to("cuda")
    except Exception:
        pass
    model.print_trainable_parameters()

    opt = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad),
                            lr=a.lr)
    scaler = torch.cuda.amp.GradScaler()

    for epoch in range(start_epoch, a.epochs):
        if STOP or (time.time() - t_start) > a.time_budget_s:
            print("[overnight] budget reached before epoch start, saving + "
                  "exit", flush=True)
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
            if bi % 100 == 0:
                el = time.time() - t_start
                print(f"[overnight] ep{epoch+1} b{bi}/{len(train_dl)} "
                      f"loss={out.loss.item():.3f} elapsed={el/3600:.2f}h",
                      flush=True)
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
        tloss = tot / max(1, len(train_dl))
        print(f"[overnight] ep{epoch+1}/{a.epochs} train={tloss:.3f} "
              f"dev={dloss:.3f} epoch_min={(time.time()-t_ep)/60:.1f} "
              f"elapsed_h={(time.time()-t_start)/3600:.2f}", flush=True)
        model.save_pretrained(outdir)  # latest
        if dloss < best_dev and not STOP:
            best_dev = dloss
            model.save_pretrained(bestdir)
            print(f"[overnight] new best dev={dloss:.3f} -> best_adapter",
                  flush=True)
        save_state(outdir, epoch + 1, best_dev)
        if STOP:
            print("[overnight] stopped by signal, saved latest + state",
                  flush=True)
            break

    save_state(outdir, epoch + 1 if 'epoch' in dir() else start_epoch,
               best_dev)
    print(f"[overnight] DONE latest->{outdir} best(dev={best_dev:.3f})"
          f"->{bestdir}", flush=True)


if __name__ == "__main__":
    main()
