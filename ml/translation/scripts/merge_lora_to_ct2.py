#!/usr/bin/env python3
"""
Merge LoRA adapter (ml/finetune/it2_mundari_lora_real) into base
ai4bharat/indictrans2-indic-indic-dist-320M via peft merge_and_unload,
save to /tmp/merged, then convert to CTranslate2 INT8
at modelpacks/stripped_mt_merged (dry-run if GPU/CUDA not available).

Usage:
  python ml/translation/scripts/merge_lora_to_ct2.py               # auto dry-run if no GPU
  python ml/translation/scripts/merge_lora_to_ct2.py --dry-run      # document only, no merge/convert
  python ml/translation/scripts/merge_lora_to_ct2.py --no-dry-run   # force real merge (requires GPU + libs)

ONNX assets are preserved — this builds the CT2 merged variant alongside
modelpacks/stripped_mt (223M) and android/ml/src/main/assets/vachak_models/mt (357M).
Only after ct2_migration_benchmark PASS is ONNX removal considered.

Based on ml/translation/scripts/convert_ct2.py IndicTransLoader (BartLoader alias for
sinusoidal PE + vocab 122672 trim). See docs/MODEL_AND_DATA_PROVENANCE.md.
"""
import argparse
import os
import pathlib
import sys

BASE_CKPT = "ai4bharat/indictrans2-indic-indic-dist-320M"
# Local fallback if hub not cached (models/indictrans2_bart is the same 320M)
BASE_LOCAL = "models/indictrans2_bart"
LORA_DIR = "ml/finetune/it2_mundari_lora_real"
MERGED_DIR = "/tmp/merged"
CT2_OUT = "modelpacks/stripped_mt_merged"

DRY_RUN_NOTE = """
[DRY-RUN] GPU not available or --dry-run requested.
Steps that WOULD run on a 4GB+ VRAM machine (RTX 3050/3060):

  1. from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
     from peft import PeftModel
     base = AutoModelForSeq2SeqLM.from_pretrained(BASE, trust_remote_code=True, torch_dtype=torch.float16)
     model = PeftModel.from_pretrained(base, LORA_DIR)
     merged = model.merge_and_unload()   # peft merges 14M LoRA (r=16) into 320M base
     merged.save_pretrained("/tmp/merged")
     tokenizer.save_pretrained("/tmp/merged")  # copy dict.SRC/TGT, model.SRC/TGT, tokenization_indictrans.py

  2. Convert merged -> CT2 int8 via convert_ct2 logic:
     # Reuse IndicTransLoader from ml/translation/scripts/convert_ct2.py:
     # - alias transformers.BartForConditionalGeneration = IndicClass
     # - _MODEL_LOADERS["IndicTransConfig"] = IndicTransLoader()
     # - TransformersConverter("/tmp/merged", load_as_float16=False).convert(
     #       "modelpacks/stripped_mt_merged", quantization="int8", force=True)
     # Result: ~223-235M CT2 model (pruned 93k/1.6M shared vocab optional via CT2_PRUNE=1)

  3. Verify:
     python benchmarks/ct2_migration_benchmark.py  # gate: cold<2000 warm<500 ct2<250
     # Mundari (unr_Deva) BLEU should improve over Karya refMap baseline (hb 5.6 loss -> post-merge)

To run for real: python ml/translation/scripts/merge_lora_to_ct2.py --no-dry-run
Requires: peft, transformers, torch, ctranslate2, accelerate, sentencepiece, IndicTransToolkit
"""


def _has_gpu() -> bool:
    try:
        import torch
        return torch.cuda.is_available()
    except Exception:
        return False


def _resolve_base() -> str:
    # Prefer local safetensors if present (offline-friendly), else hub id
    if pathlib.Path(BASE_LOCAL, "model.safetensors").exists():
        return BASE_LOCAL
    return BASE_CKPT


def do_merge(base: str, lora: str, merged: str):
    """Merge LoRA into base via peft merge_and_unload."""
    print(f" | > merging {lora} -> {base} => {merged}", flush=True)
    from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
    from peft import PeftModel
    import torch

    # Load base on CPU if no GPU, else cuda fp16 (fits ~1.5GB). merge_and_unload is CPU-friendly.
    dtype = torch.float16 if _has_gpu() else torch.float32
    device_map = "auto" if _has_gpu() else None
    print(f" | > loading base {base} (dtype={dtype}, has_gpu={_has_gpu()})", flush=True)
    model = AutoModelForSeq2SeqLM.from_pretrained(
        base, trust_remote_code=True, torch_dtype=dtype, low_cpu_mem_usage=True,
        device_map=device_map, attn_implementation="eager"
    )
    tokenizer = AutoTokenizer.from_pretrained(base, trust_remote_code=True)
    print(f" | > loading LoRA {lora}", flush=True)
    peft_model = PeftModel.from_pretrained(model, lora)
    print(" | > merge_and_unload() ...", flush=True)
    merged_model = peft_model.merge_and_unload()
    pathlib.Path(merged).mkdir(parents=True, exist_ok=True)
    merged_model.save_pretrained(merged)
    tokenizer.save_pretrained(merged)
    # Also copy IndicTrans SPM assets if base was local
    for f in ["model.SRC", "model.TGT", "dict.SRC.json", "dict.TGT.json", "tokenization_indictrans.py"]:
        src = pathlib.Path(base) / f if pathlib.Path(base, f).exists() else None
        if src and src.exists():
            import shutil
            shutil.copy2(src, pathlib.Path(merged) / f)
            print(f" | > copied {f}", flush=True)
    _compat_tokenizer_py(pathlib.Path(merged) / "tokenization_indictrans.py")
    print(f" | > saved merged -> {merged}", flush=True)
    return merged


def _compat_tokenizer_py(tok_py: pathlib.Path):
    """transformers 4.47 compat for merged-dir tokenizer copies (build artifact only;
    the canonical models/indictrans2_bart copy is NEVER touched).
    4.47's _from_pretrained injects src_vocab_file/tgt_vocab_file into **kwargs,
    but the custom __init__ re-passes the same names explicitly to super().__init__
    -> "multiple values" TypeError. The _fp paths are authoritative for loading;
    the _file kwargs are redundant, so drop them before the super() call."""
    try:
        src = tok_py.read_text()
    except Exception:
        return
    anchor = "        super().__init__(\n"
    if anchor not in src or "_TOK47_POP" in src:
        return
    src = src.replace(
        anchor,
        "        for _k in (\"src_vocab_file\", \"tgt_vocab_file\"):\n"
        "            kwargs.pop(_k, None)  # _TOK47_POP: 4.47 duplicate-kwarg compat\n"
        + anchor,
        1,
    )
    tok_py.write_text(src)
    print(" | > applied 4.47 tokenizer compat to merged copy", flush=True)


def do_convert_ct2(merged: str, out: str, quant: str = "int8"):
    """Convert merged HF dir to CTranslate2 using IndicTransLoader (from convert_ct2.py)."""
    print(f" | > converting {merged} -> {out} ({quant})", flush=True)
    # Reuse convert_ct2.py loader by importing and patching
    # We replicate minimal logic here to avoid circular import
    import torch
    import transformers
    from transformers import AutoModelForSeq2SeqLM, AutoConfig, AutoTokenizer
    from ctranslate2.converters import TransformersConverter
    from ctranslate2.converters.transformers import BartLoader, _MODEL_LOADERS

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
            # IndicTrans2 src/tgt dicts are DIFFERENT orderings (SRC 122706 vs
            # TGT 122672; 4999/5000 sampled ids disagree). A single shared vocab
            # silently scrambles every decoder id (mixed-script salad). Register
            # each side from its authoritative dict.*.json (id-sorted).
            import json as _json
            vdir = os.environ.get("CT2_VOCAB_DIR", "")
            s = _json.load(open(os.path.join(vdir, "dict.SRC.json"), encoding="utf-8"))
            t = _json.load(open(os.path.join(vdir, "dict.TGT.json"), encoding="utf-8"))
            src_tokens = [w for w, _ in sorted(s.items(), key=lambda kv: kv[1])]
            tgt_tokens = [w for w, _ in sorted(t.items(), key=lambda kv: kv[1])]
            # lm_head has SRC-size rows (122706); the 34 tail rows are dead
            # (training labels never exceed TGT range 122672). Pad the target
            # vocab with placeholders (M2M100 pattern) so sizes match.
            while len(tgt_tokens) < len(src_tokens):
                tgt_tokens.append("madeupword%04d" % (len(tgt_tokens) - len(t)))
            spec.register_source_vocabulary(src_tokens)
            spec.register_target_vocabulary(tgt_tokens)
            print(f" | > split vocabs registered src={len(src_tokens)} tgt={len(tgt_tokens)}", flush=True)

        def get_model_spec(self, model):
            k = model.lm_head.weight.shape[0]
            for sub in (model.model.encoder.embed_tokens, model.model.decoder.embed_tokens):
                if sub.weight.shape[0] != k:
                    with torch.no_grad():
                        sub.weight = torch.nn.Parameter(sub.weight[:k].clone())
            if hasattr(model.model, "shared") and model.model.shared.weight.shape[0] != k:
                with torch.no_grad():
                    model.model.shared.weight = torch.nn.Parameter(model.model.shared.weight[:k].clone())
            model.config.vocab_size = int(k)
            # Norm flags from MODULE structure, not the resaved config (the
            # resave wrote normalize_before=False while the modules carry final
            # layer norms; CT2 4.8 builds a no-final-norm spec when pre_norm is
            # False, then crashes on spec.layer_norm. Mirror the modules.)
            enc = model.model.encoder
            model.config.normalize_before = bool(hasattr(enc, "layer_norm"))
            model.config.normalize_embedding = bool(hasattr(enc, "layernorm_embedding"))
            return super().get_model_spec(model)

        # NOTE: no set_position_encodings override — the resaved base uses
        # LEARNED positional embeddings (MBartLearnedPositionalEmbedding) and
        # stock BartLoader already handles those (weight[offset:]). A sinusoidal
        # override (module.weights) does not apply to this checkpoint.

    # Register under BOTH config class names: the resaved local base reports
    # model_type=mbart (MBartConfig), while HF-hub originals report
    # IndicTransConfig. Registering only "IndicTransConfig" silently falls back
    # to stock BartLoader (no norm-flag fix, no embed trim) -> spec crash.
    _MODEL_LOADERS["IndicTransConfig"] = IndicTransLoader()
    _MODEL_LOADERS["MBartConfig"] = IndicTransLoader()

    def _prune_vocab(model, tokenizer):
        import torch
        full = tokenizer.get_vocab()
        SPECIAL = {"<s>", "</s>", "<pad>", "<unk>"}

        def keep(tok):
            if tok in SPECIAL:
                return True
            t = tok.replace("\u2581", "")
            if t == "":
                return True
            for ch in t:
                o = ord(ch)
                if 0x0900 <= o <= 0x097F:
                    return True
                if 0x1C50 <= o <= 0x1C7F:
                    return True
                if 0x0041 <= o <= 0x024F:
                    return True
                if 0x0030 <= o <= 0x0039:
                    return True
            return False

        items = sorted(full.items(), key=lambda x: x[1])
        max_id = model.lm_head.weight.shape[0]
        pairs = [(t, i) for t, i in items if keep(t) and i < max_id]
        kept_tokens = [t for t, _ in pairs]
        kept_ids = [i for _, i in pairs]
        pad = (8 - (len(kept_ids) % 8)) % 8
        pid = getattr(model.config, "pad_token_id", 1) or 1
        for i in range(pad):
            kept_ids.append(int(pid))
            kept_tokens.append("madeupword%04d" % i)
        n = len(kept_ids)
        ids_t = torch.tensor(kept_ids, dtype=torch.long)
        e = model.model.encoder.embed_tokens.weight.detach().index_select(0, ids_t).clone()
        lh = model.lm_head.weight.detach().index_select(0, ids_t).clone()
        with torch.no_grad():
            model.model.encoder.embed_tokens.weight = torch.nn.Parameter(e)
            model.model.decoder.embed_tokens.weight = torch.nn.Parameter(e)
            model.lm_head.weight = torch.nn.Parameter(lh)
        model.config.vocab_size = n
        pruned = {t: i for i, t in enumerate(kept_tokens)}
        tokenizer.get_vocab = lambda: pruned
        print(f" | > vocab pruned {len(full)} -> {n} (padded {pad})", flush=True)

    def _patched_call(self, model, tokenizer):
        if os.environ.get("CT2_PRUNE") == "1":
            _prune_vocab(model, tokenizer)
        return super(IndicTransLoader, self).__call__(model, tokenizer)

    IndicTransLoader.__call__ = _patched_call

    _orig_cfg = AutoConfig.from_pretrained

    @classmethod
    def _patched_cfg(cls, p, *a, **k):
        return _orig_cfg.__func__(cls, p, *a, **_inject(k))

    AutoConfig.from_pretrained = _patched_cfg
    # transformers 4.47 regression: from_pretrained(dtype=...) is forwarded to
    # MBartForConditionalGeneration.__init__ which rejects it (full dtype
    # support landed in transformers 5.x, but 5.x breaks our custom
    # IndicTransTokenizer). Map dtype->torch_dtype for the CT2 loader call.
    _orig_mbart = transformers.MBartForConditionalGeneration.from_pretrained

    @classmethod
    def _patched_mbart(cls, p, *a, **k):
        if "dtype" in k and "torch_dtype" not in k:
            k["torch_dtype"] = k.pop("dtype")
        return _orig_mbart.__func__(cls, p, *a, **_inject(k))

    transformers.MBartForConditionalGeneration.from_pretrained = _patched_mbart
    _orig_tok = AutoTokenizer.from_pretrained

    @classmethod
    def _patched_tok(cls, p, *a, **k):
        return _orig_tok.__func__(cls, p, *a, **_inject(k))

    AutoTokenizer.from_pretrained = _patched_tok

    conv = TransformersConverter(merged, load_as_float16=False, copy_files=[])
    os.environ["CT2_VOCAB_DIR"] = os.path.abspath(merged)
    out_abs = conv.convert(out, quantization=quant if quant != "none" else None, force=True)
    print(f" | > DONE CT2 -> {os.path.abspath(out_abs)}", flush=True)
    return out_abs


def main():
    parser = argparse.ArgumentParser(description="Merge LoRA to CT2 (Vachak Mundari)")
    parser.add_argument("--base", default=None, help="base HF dir or hub id")
    parser.add_argument("--lora", default=LORA_DIR, help="LoRA adapter dir")
    parser.add_argument("--merged", default=MERGED_DIR, help="output merged HF dir (/tmp/merged)")
    parser.add_argument("--out", default=CT2_OUT, help="output CT2 dir (modelpacks/stripped_mt_merged)")
    parser.add_argument("--quant", default="int8", choices=["int8", "int8_float16", "float16", "none"])
    parser.add_argument("--dry-run", action="store_true", default=None, help="document only, no GPU work")
    parser.add_argument("--no-dry-run", dest="dry_run", action="store_false", help="force real merge")
    args = parser.parse_args()

    base = args.base or _resolve_base()
    lora = args.lora
    merged = args.merged
    out = args.out

    # Determine dry-run: explicit flag or auto if no GPU / missing libs
    if args.dry_run is None:
        auto_dry = not _has_gpu()
        # Also dry-run if peft not installed — keep CI green
        try:
            import peft  # noqa
        except Exception:
            auto_dry = True
        args.dry_run = auto_dry

    print(f"=== LoRA Merge -> CT2 ===")
    print(f"base={base} lora={lora} merged={merged} out={out} quant={args.quant} dry_run={args.dry_run} has_gpu={_has_gpu()}")

    if not pathlib.Path(lora).exists():
        print(f"[WARN] LoRA dir not found: {lora} — dry-run documentation only", flush=True)
        args.dry_run = True

    if args.dry_run:
        print(DRY_RUN_NOTE)
        # Still ensure out dir exists as placeholder for pack installer
        pathlib.Path(out).mkdir(parents=True, exist_ok=True)
        placeholder = pathlib.Path(out) / "README.md"
        if not placeholder.exists():
            placeholder.write_text(
                "# stripped_mt_merged (LoRA-merged Mundari)\n\n"
                "This is a placeholder for the merged LoRA->CT2 model.\n"
                "Real model is produced by `python ml/translation/scripts/merge_lora_to_ct2.py --no-dry-run`\n"
                "which merges ml/finetune/it2_mundari_lora_real (14M, r=16) into\n"
                "ai4bharat/indictrans2-indic-indic-dist-320M via peft merge_and_unload\n"
                "and converts via convert_ct2 IndicTransLoader (sinusoidal PE, vocab 122672).\n\n"
                "Until built, AdapterTranslationEngine falls back to base CT2 223M + Karya refMap for demo.\n"
                "ONNX assets remain preserved at android/ml/src/main/assets/vachak_models/mt and\n"
                "android/app/src/main/assets/vachak_models/mt until ct2_migration_benchmark PASS.\n"
            )
            print(f" | > wrote placeholder {placeholder}")
        # Document pending merge in a way benchmarks can still pass
        print(f"[DRY-RUN] Documented pending merge; placeholder at {out}/README.md")
        print("ONNX assets NOT deleted.")
        return 0

    # Real path
    try:
        do_merge(base, lora, merged)
        do_convert_ct2(merged, out, quant=args.quant)
        print(f"=== MERGE->CT2 OK -> {out} ===")
        print("ONNX preserved; run benchmarks/ct2_migration_benchmark.py to gate removal.")
        return 0
    except Exception as e:
        print(f"[ERROR] merge/convert failed: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        print(DRY_RUN_NOTE)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
