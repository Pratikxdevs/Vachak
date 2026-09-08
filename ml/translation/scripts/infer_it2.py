#!/usr/bin/env python3
"""Clean inference wrapper for IndicTrans2 (Hin_Deva -> Sat_Olck).

The PyTorch model decodes correctly and without repetition loops (verified
against HF cached decode). This module loads the base checkpoint or a merged
LoRA adapter and translates Hindi classroom text to Santali (Ol Chiki).

NOTE on ONNX: the custom IndicTrans2 decoder fails to export a correct
KV-cache / positional graph under transformers 4.36 / 4.57 + onnxruntime
(self-attn MatMul dimension mismatch / degenerate repetition). The encoder
exports fine; a correct ONNX decode path is tracked separately. For now the
deployable model is this PyTorch pipeline.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Iterable, List, Optional

import torch
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
from IndicTransToolkit import IndicProcessor


class IndicTrans2Translator:
    def __init__(
        self,
        model_id: str = "ai4bharat/indictrans2-indic-indic-dist-320M",
        adapter_dir: Optional[str] = None,
        device: str = "cuda",
        use_fp16: bool = True,
    ) -> None:
        self.device = device
        self.tok = AutoTokenizer.from_pretrained(model_id, trust_remote_code=True)
        self.proc = IndicProcessor(inference=True)
        dtype = torch.float16 if (use_fp16 and device == "cuda") else torch.float32
        self.model = AutoModelForSeq2SeqLM.from_pretrained(
            model_id, trust_remote_code=True, torch_dtype=dtype
        ).to(device).eval()
        if adapter_dir:
            from peft import PeftModel

            self.model = PeftModel.from_pretrained(self.model, adapter_dir)
            self.model = self.model.merge_and_unload()
        self.model = self.model.to(device).eval()

    @torch.inference_mode()
    def translate(
        self,
        texts: List[str],
        src_lang: str = "hin_Deva",
        tgt_lang: str = "sat_Olck",
        batch_size: int = 8,
        max_new_tokens: int = 128,
    ) -> List[str]:
        out: List[str] = []
        for i in range(0, len(texts), batch_size):
            chunk = texts[i : i + batch_size]
            prefixed = self.proc.preprocess_batch(
                chunk, src_lang=src_lang, tgt_lang=tgt_lang, is_target=False
            )
            enc = self.tok(
                prefixed, return_tensors="pt", padding=True, truncation=True
            ).to(self.device)
            gen = self.model.generate(
                **enc,
                num_beams=1,
                do_sample=False,
                max_new_tokens=max_new_tokens,
                repetition_penalty=1.2,
                no_repeat_ngram_size=3,
                early_stopping=True,
            )
            with self.tok.as_target_tokenizer():
                dec = self.tok.batch_decode(gen, skip_special_tokens=True)
            out.extend(self.proc.postprocess_batch(dec, lang=tgt_lang))
        return out

    def translate_hi_sat(self, hi: Iterable[str]) -> List[str]:
        return self.translate(list(hi))


def main() -> int:
    ap = argparse.ArgumentParser(description="Translate Hindi -> Santali with IndicTrans2")
    ap.add_argument("--model_id", default="ai4bharat/indictrans2-indic-indic-dist-320M")
    ap.add_argument("--adapter_dir", default=None)
    ap.add_argument("--device", default="cuda")
    ap.add_argument("--inp", help="input .txt, one Hindi sentence per line")
    ap.add_argument("--out", help="output .txt of Santali sentences")
    ap.add_argument("--src_lang", default="hin_Deva")
    ap.add_argument("--tgt_lang", default="sat_Olck")
    args = ap.parse_args()

    tr = IndicTrans2Translator(args.model_id, args.adapter_dir, args.device)
    if args.inp:
        texts = [l.strip() for l in open(args.inp, encoding="utf-8") if l.strip()]
        out = tr.translate(texts, args.src_lang, args.tgt_lang)
        if args.out:
            open(args.out, "w", encoding="utf-8").write("\n".join(out) + "\n")
        else:
            for h, o in zip(texts, out):
                print(f"HI : {h}\nSAT: {o}")
    else:
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue
            print(tr.translate([line], args.src_lang, args.tgt_lang)[0])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
