#!/usr/bin/env python3
"""Merge the trained LoRA adapter into the hub base and export CT2 INT8.

Run on the Studio (hub model cached, T4). Produces a ct2/ dir + tokenizer/
subdir to download and drop into the repo as modelpacks/sat_bidi_ct2_int8/.

  pip install ctranslate2
  python merge_export_ct2.py --adapter adapter_sat_bidi/best_adapter
  python merge_export_ct2.py --sanity   # after export: 4 classroom sentences

Sanity compares CT2 output vs the PyTorch eval path — they must agree
closely (same greedy decoding). If conversion fails, paste the traceback.
"""
from __future__ import annotations
import argparse, os, shutil, sys

HUB = "ai4bharat/indictrans2-indic-indic-dist-320M"
TOK_FILES = ["tokenization_indictrans.py", "tokenizer_config.json",
             "special_tokens_map.json", "model.SRC", "model.TGT",
             "dict.SRC.json", "dict.TGT.json", "generation_config.json",
             "config.json"]


def do_merge(adapter, model=HUB, out="merged_sat_bidi"):
    import torch
    from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
    from peft import PeftModel
    print(f"[export] loading base {model} (fp16)", flush=True)
    base = AutoModelForSeq2SeqLM.from_pretrained(
        model, trust_remote_code=True, torch_dtype=torch.float16)
    tok = AutoTokenizer.from_pretrained(model, trust_remote_code=True)
    print(f"[export] loading adapter {adapter}", flush=True)
    merged = PeftModel.from_pretrained(base, adapter).merge_and_unload()
    os.makedirs(out, exist_ok=True)
    merged.save_pretrained(out)
    tok.save_pretrained(out)
    try:
        src = os.path.join(
            os.path.expanduser("~"), ".cache", "huggingface", "hub")
        print(f"[export] merged -> {out}", flush=True)
    except Exception:
        pass
    # copy SPM/dict assets from the hub snapshot into the merged dir so the
    # export is self-contained (tokenizer needs model.SRC/TGT + dicts)
    from huggingface_hub import snapshot_download
    snap = snapshot_download(model)
    for f in TOK_FILES:
        s, d = os.path.join(snap, f), os.path.join(out, f)
        if os.path.exists(s) and not os.path.exists(d):
            shutil.copy2(s, d)
            print(f"[export] copied {f}", flush=True)
    _compat_tokenizer_py(os.path.join(out, "tokenization_indictrans.py"))
    return out


def _compat_tokenizer_py(path):
    """transformers>=4.47 duplicate-kwarg compat (same fix as repo
    merge_lora_to_ct2._compat_tokenizer_py). Harmless on older versions."""
    try:
        src = open(path, encoding="utf-8").read()
    except Exception as e:
        print(f"[export] tokenizer patch skipped ({e})", flush=True)
        return
    anchor = "        super().__init__(\n"
    if anchor not in src or "_TOK47_POP" in src:
        print("[export] tokenizer patch not needed", flush=True)
        return
    src = src.replace(
        anchor,
        "        for _k in (\"src_vocab_file\", \"tgt_vocab_file\"):\n"
        "            kwargs.pop(_k, None)  # _TOK47_POP: 4.47 duplicate-kwarg compat\n"
        + anchor, 1)
    open(path, "w", encoding="utf-8").write(src)
    print("[export] applied 4.47 tokenizer compat", flush=True)


def do_convert(merged="merged_sat_bidi", out="ct2_sat_bidi_int8",
               quant="int8"):
    """Convert via a custom IndicTransLoader (ported from the repo's
    ml/translation/scripts/merge_lora_to_ct2.py): stock ctranslate2 knows
    BartConfig/MBartConfig but NOT IndicTransConfig, so we alias the
    custom class to Bart and register split SRC/TGT vocabularies."""
    import torch
    import transformers
    from transformers import AutoModelForSeq2SeqLM, AutoConfig, AutoTokenizer
    from ctranslate2.converters import TransformersConverter
    from ctranslate2.converters.transformers import BartLoader, _MODEL_LOADERS
    import inspect

    print("[export] merged dir:", sorted(os.listdir(merged)), flush=True)
    for need in ("dict.SRC.json", "dict.TGT.json"):
        if not os.path.exists(os.path.join(merged, need)):
            raise RuntimeError(
                f"{need} missing in {merged} — cannot register split vocabs. "
                f"Dir listing above; paste it back for diagnosis.")

    def _inject(kwargs):
        kwargs.setdefault("trust_remote_code", True)
        kwargs.setdefault("attn_implementation", "eager")
        return kwargs

    _orig_auto = AutoModelForSeq2SeqLM.from_pretrained

    @classmethod
    def _patched_auto(cls, p, *a, **k):
        return _orig_auto.__func__(cls, p, *a, **_inject(k))

    AutoModelForSeq2SeqLM.from_pretrained = _patched_auto
    _tmp = AutoModelForSeq2SeqLM.from_pretrained(merged)
    IndicClass = _tmp.__class__
    del _tmp
    transformers.BartForConditionalGeneration = IndicClass
    _orig_indic = IndicClass.from_pretrained

    @classmethod
    def _patched_indic(cls, p, *a, **k):
        return _orig_indic.__func__(cls, p, *a, **_inject(k))

    IndicClass.from_pretrained = _patched_indic

    class IndicTransLoader(BartLoader):
        @property
        def architecture_name(self):
            return "BartForConditionalGeneration"

        def set_vocabulary(self, spec, tokens):
            import json as _json
            with open(os.path.join(merged, "dict.SRC.json"),
                      encoding="utf-8") as f:
                s = _json.load(f)
            with open(os.path.join(merged, "dict.TGT.json"),
                      encoding="utf-8") as f:
                t = _json.load(f)
            src_tokens = [w for w, _ in sorted(s.items(),
                                               key=lambda kv: kv[1])]
            tgt_tokens = [w for w, _ in sorted(t.items(),
                                               key=lambda kv: kv[1])]
            while len(tgt_tokens) < len(src_tokens):
                tgt_tokens.append("madeupword%04d" % len(tgt_tokens))
            spec.register_source_vocabulary(src_tokens)
            spec.register_target_vocabulary(tgt_tokens)
            print(f"[export] split vocabs src={len(src_tokens)} "
                  f"tgt={len(tgt_tokens)}", flush=True)

        def get_model_spec(self, model):
            k = model.lm_head.weight.shape[0]
            for sub in (model.model.encoder.embed_tokens,
                        model.model.decoder.embed_tokens):
                if sub.weight.shape[0] != k:
                    with torch.no_grad():
                        sub.weight = torch.nn.Parameter(
                            sub.weight[:k].clone())
            if (hasattr(model.model, "shared")
                    and model.model.shared.weight.shape[0] != k):
                with torch.no_grad():
                    model.model.shared.weight = torch.nn.Parameter(
                        model.model.shared.weight[:k].clone())
            model.config.vocab_size = int(k)
            enc = model.model.encoder
            model.config.normalize_before = bool(
                hasattr(enc, "layer_norm"))
            model.config.normalize_embedding = bool(
                hasattr(enc, "layernorm_embedding"))
            return super().get_model_spec(model)

    _MODEL_LOADERS["IndicTransConfig"] = IndicTransLoader()
    _MODEL_LOADERS["MBartConfig"] = IndicTransLoader()

    _orig_cfg = AutoConfig.from_pretrained

    @classmethod
    def _patched_cfg(cls, p, *a, **k):
        return _orig_cfg.__func__(cls, p, *a, **_inject(k))

    AutoConfig.from_pretrained = _patched_cfg
    _orig_tok = AutoTokenizer.from_pretrained

    @classmethod
    def _patched_tok(cls, p, *a, **k):
        return _orig_tok.__func__(cls, p, *a, **_inject(k))

    AutoTokenizer.from_pretrained = _patched_tok

    kw = {}
    if "trust_remote_code" in inspect.signature(
            TransformersConverter.__init__).parameters:
        kw["trust_remote_code"] = True
    os.environ["CT2_VOCAB_DIR"] = os.path.abspath(merged)
    conv = TransformersConverter(merged, load_as_float16=False,
                                 copy_files=[], **kw)
    res = conv.convert(out, quantization=quant if quant != "none" else None,
                       force=True)
    print(f"[export] CT2 -> {os.path.abspath(res)}", flush=True)
    total = sum(os.path.getsize(os.path.join(dp, f))
                for dp, _, fs in os.walk(res) for f in fs)
    print(f"[export] CT2 size: {total/1e6:.0f} MB", flush=True)
    return res


def do_sanity(ct2="ct2_sat_bidi_int8", merged="merged_sat_bidi"):
    import ctranslate2
    from transformers import AutoTokenizer
    from IndicTransToolkit import IndicProcessor
    tok = AutoTokenizer.from_pretrained(merged, trust_remote_code=True)
    proc = IndicProcessor(inference=True)
    tr = ctranslate2.Translator(ct2, device="cuda")
    sents = ["बच्चों, अपना होमवर्क दिखाओ।", "तुम्हारा नाम क्या है?",
             "कल स्कूल क्यों नहीं आए?", "पाँच तक गिनो।"]
    batch = proc.preprocess_batch(sents, src_lang="hin_Deva",
                                  tgt_lang="sat_Olck")
    enc = tok(batch, return_tensors="pt", padding=True, truncation=True,
              max_length=128)
    pieces = [tok.convert_ids_to_tokens(ids)
              for ids in enc.input_ids.tolist()]
    for h, r in zip(sents, tr.translate_batch(
            pieces, max_decoding_length=128, beam_size=1)):
        # Hypotheses are target-vocab SP pieces: join directly. Never
        # round-trip through convert_tokens_to_ids+batch_decode (SRC/TGT
        # dict orderings differ -> script salad).
        hyp = [p for p in r.hypotheses[0]
               if p not in ("</s>", "<s>", "<pad>", "<unk>")]
        txt = "".join(hyp).replace("▁", " ").strip()
        print("HIN:", h)
        print("SAT:", proc.postprocess_batch([txt], lang="sat_Olck")[0])
        print("---")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--adapter", default="adapter_sat_bidi/best_adapter")
    ap.add_argument("--model", default=HUB)
    ap.add_argument("--merged", default="merged_sat_bidi")
    ap.add_argument("--out", default="ct2_sat_bidi_int8")
    ap.add_argument("--quant", default="int8")
    ap.add_argument("--sanity", action="store_true")
    ap.add_argument("--skip_merge", action="store_true")
    a = ap.parse_args()
    if a.sanity:
        return do_sanity(a.out, a.merged)
    if not a.skip_merge:
        do_merge(a.adapter, a.model, a.merged)
    do_convert(a.merged, a.out, a.quant)
    print("[export] now run: python merge_export_ct2.py --sanity",
          flush=True)


if __name__ == "__main__":
    main()
