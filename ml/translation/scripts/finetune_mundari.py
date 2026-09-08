#!/usr/bin/env python3
"""LoRA fine-tune of IndicTrans2 (Hin_Deva -> unr_Deva Mundari) on RTX 3050 4GB.

Clone of finetune_simple.py parameterized for Mundari: --tgt_lang (default
unr_Deva), --model local path (models/indictrans2_bart resaved as MBART, no
remote code needed), manual fp16 AMP loop, LoRA on q/k/v/out. Saves PEFT adapter.

Data: TSV hi<TAB>mun — Karya corpus (datasets/hin_mun/corpus.tsv) + dictionary
pairs (datasets/hin_mun/dict_pairs.tsv, OCR VHORO dictionary, word+sentence tiers).
"""
from __future__ import annotations
import argparse
import random
from pathlib import Path

import torch
from torch.utils.data import DataLoader, Dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer


def _real_processor():
    try:
        from IndicTransToolkit import IndicProcessor
        return IndicProcessor(inference=True)
    except Exception as e:
        print(f"[ft-mun] IndicTransToolkit unavailable ({e}), using built-in fallback", flush=True)
        return None


_REAL_PROC = None

def indic_preprocess(text, src_lang, tgt_lang, is_target=False):
    """Canonical preprocessing (real IndicTransToolkit when installed).
    Source: "<src> <tgt> <text>". Target: "<text> ONLY, no tags" — the toolkit
    strips tags for is_target=True. Training targets WITH tags teaches the
    model to emit tag garbage first (learned the hard way: double-tagged
    targets -> 'unrúDeva'/'Mul' echoes on every output)."""
    global _REAL_PROC
    if _REAL_PROC is None:
        _REAL_PROC = _real_processor()
        if _REAL_PROC is False:
            _REAL_PROC = None
    if _REAL_PROC is not None:
        try:
            if is_target:
                return _REAL_PROC.preprocess_batch([text], src_lang=tgt_lang, tgt_lang=src_lang, is_target=True)[0]
            return _REAL_PROC.preprocess_batch([text], src_lang=src_lang, tgt_lang=tgt_lang, is_target=False)[0]
        except Exception as e:
            print(f"[ft-mun] toolkit preprocess failed ({e}), fallback", flush=True)
    import unicodedata
    import re as _re
    t = unicodedata.normalize("NFKC", text)
    t = " ".join(t.split())
    _PUNCT = set('!"#$\'(),-./:;?@[\\]^_`{|}~।॥᱾᱿')
    t = "".join(" " + c + " " if c in _PUNCT else c for c in t)
    t = _re.sub(r"[ ]+", " ", t).strip()
    if is_target:
        return t
    return f"{src_lang} {tgt_lang} {t}" if t else f"{src_lang} {tgt_lang}"


def _is_olchiki_first(s):
    ol = sum(1 for c in s if "᱐" <= c <= "᱿")
    dv = sum(1 for c in s if "\u0900" <= c <= "\u097F")
    return ol > dv


class PairedDataset(Dataset):
    def __init__(self, pairs, tok, tgt_lang, max_len=128, bidir=False,
                 rev_lang="hin_Deva"):
        self.items = []
        for a, b in pairs:
            if bidir and _is_olchiki_first(a):
                src_lang, dst_lang, src_txt, dst_txt = tgt_lang, rev_lang, a, b
            else:
                src_lang, dst_lang, src_txt, dst_txt = "hin_Deva", tgt_lang, a, b
            src = indic_preprocess(src_txt, src_lang, dst_lang, is_target=False)
            tgt = indic_preprocess(dst_txt, dst_lang, src_lang, is_target=True)
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
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            hi, mun = parts[0].strip(), parts[1].strip()
            if hi and mun:
                pairs.append((hi, mun))
    return pairs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv", required=True)
    ap.add_argument("--dev_tsv", default=None)
    ap.add_argument("--output_dir", required=True)
    ap.add_argument("--model", default="models/indictrans2_bart")
    ap.add_argument("--tgt_lang", default="unr_Deva")
    ap.add_argument("--bidir", action="store_true",
                    help="Bidirectional: infer per-row direction by script "
                         "(Devanagari-first col = hi->tgt_lang, OlChiki-first col = reversed). "
                         "For Santali adapters serving both hindi-sant and sant-hindi.")
    ap.add_argument("--epochs", type=int, default=5)
    ap.add_argument("--batch_size", type=int, default=4)
    ap.add_argument("--grad_accum", type=int, default=4)
    ap.add_argument("--lr", type=float, default=2e-4)
    ap.add_argument("--max_len", type=int, default=128)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--resume", default=None,
                    help="PEFT adapter dir to resume from (continues training its weights)")
    ap.add_argument("--qlora4bit", action="store_true",
                    help="Load base in 4-bit (bitsandbytes) for tight VRAM")
    ap.add_argument("--grad_ckpt", action="store_true",
                    help="Gradient checkpointing (trades ~30% speed for large VRAM savings)")
    a = ap.parse_args()

    random.seed(a.seed)
    train = load_pairs(a.tsv)
    if a.dev_tsv:
        dev = load_pairs(a.dev_tsv)
    else:
        random.shuffle(train)
        n_dev = max(1, int(len(train) * 0.1))
        dev, train = train[:n_dev], train[n_dev:]
    print(f"[ft-mun] {len(train)} train / {len(dev)} dev -> {a.tgt_lang}", flush=True)

    tok = AutoTokenizer.from_pretrained(a.model, trust_remote_code=True)
    train_ds = PairedDataset(train, tok, a.tgt_lang, a.max_len, bidir=a.bidir)
    dev_ds = PairedDataset(dev, tok, a.tgt_lang, a.max_len, bidir=a.bidir)
    train_dl = DataLoader(train_ds, batch_size=a.batch_size, shuffle=True, collate_fn=collate, num_workers=0)
    dev_dl = DataLoader(dev_ds, batch_size=a.batch_size, shuffle=False, collate_fn=collate, num_workers=0)

    if a.qlora4bit:
        from transformers import BitsAndBytesConfig
        bnb = BitsAndBytesConfig(load_in_4bit=True,
                                 bnb_4bit_compute_dtype=torch.float16,
                                 bnb_4bit_use_double_quant=True)
        model = AutoModelForSeq2SeqLM.from_pretrained(
            a.model, trust_remote_code=True,
            attn_implementation="eager", quantization_config=bnb,
        )
        print("[ft-mun] base loaded 4-bit (QLoRA mode)", flush=True)
    else:
        model = AutoModelForSeq2SeqLM.from_pretrained(
            a.model, trust_remote_code=True,
            attn_implementation="eager", torch_dtype=torch.float16,
        ).to("cuda")
    model = prepare_model_for_kbit_training(model, use_gradient_checkpointing=a.grad_ckpt)
    if a.grad_ckpt:
        try:
            model.gradient_checkpointing_enable()
            # Checkpointing + use_cache=True detaches the graph (loss loses
            # grad_fn). Training never needs the KV cache — disable it.
            model.config.use_cache = False
        except Exception as e:
            print(f"[ft-mun] grad_ckpt enable note: {e}", flush=True)
    lora = LoraConfig(r=16, lora_alpha=32, lora_dropout=0.05, bias="none",
                      task_type="SEQ_2_SEQ_LM",
                      target_modules=["q_proj", "k_proj", "v_proj", "out_proj"])
    if a.resume:
        # Lean resume: read adapter tensors straight from disk (no probe model —
        # a probe costs ~700MB transient VRAM/RAM we cannot spare).
        from safetensors.torch import load_file
        import glob as _glob
        cand = _glob.glob(f"{a.resume}/adapter_model.safetensors")
        if not cand:
            raise RuntimeError(f"[ft-mun] resume FAILED: no adapter_model.safetensors in {a.resume}")
        saved = load_file(cand[0], device="cpu")
        # PEFT save_pretrained strips the ".default" adapter-name infix that
        # live modules carry (lora_A.weight on disk vs lora_A.default.weight
        # in memory) — restore it so keys map 1:1, mirroring from_pretrained.
        saved = {k.replace(".lora_A.", ".lora_A.default.").replace(".lora_B.", ".lora_B.default."): v
                 for k, v in saved.items()}
        model = get_peft_model(model, lora)
        trainable_names = {n for n, p in model.named_parameters() if p.requires_grad}
        missing = [n for n in trainable_names if n not in saved]
        if missing:
            raise RuntimeError(f"[ft-mun] resume FAILED: {len(missing)} LoRA tensors missing (e.g. {missing[0]}) — refusing silent restart")
        model.load_state_dict({n: saved[n] for n in trainable_names}, strict=False)
        del saved
        print(f"[ft-mun] resumed {len(trainable_names)} LoRA tensors from disk, continuing", flush=True)
    else:
        model = get_peft_model(model, lora)
    try:
        model = model.to("cuda")
    except Exception:
        pass
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
            if bi % 20 == 0:
                print(f"[ft-mun] ep{epoch+1} b{bi} loss={out.loss.item():.3f}", flush=True)
        model.eval()
        dloss = 0.0
        with torch.no_grad():
            for b in dev_dl:
                b = {k: v.to("cuda") for k, v in b.items()}
                with torch.cuda.amp.autocast(dtype=torch.float16):
                    dloss += model(**b).loss.item()
        print(f"[ft-mun] ep{epoch+1} train_loss={tot/len(train_dl):.3f} dev_loss={dloss/len(dev_dl):.3f}", flush=True)

    Path(a.output_dir).mkdir(parents=True, exist_ok=True)
    model.save_pretrained(a.output_dir)
    print(f"[ft-mun] saved adapter -> {a.output_dir}", flush=True)


if __name__ == "__main__":
    main()
