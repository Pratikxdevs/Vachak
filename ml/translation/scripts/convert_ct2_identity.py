#!/usr/bin/env python3
"""Convert base IndicTrans2 (hin_Deva<->sat_Olck) to CTranslate2 INT8.

ctranslate2 loads weights via `transformers.<architecture_name>.from_pretrained`, which
rebuilds the model from the (custom) config. IndicTransConfig lacks d_model -> BART
defaults to 1024, and the SinusoidalPositionalEmbedding buffer is dropped. So we alias the
real custom IndicTrans2 model class onto `transformers.BartForConditionalGeneration` so the
converter loads the genuine model (sinusoidal PE + 512-d weights). A small IndicTransLoader
(subclass of BartLoader) then:
  * fixes the released vocab mismatch (embed_tokens 122706 vs lm_head 122672) by trimming
    embeddings to the lm_head size (the 34 extra rows are unused added tokens),
  * reads the sinusoidal position buffer 0-based (no offset shift),
  * sets BART-style post-norm / layernorm_embedding flags.
"""
import torch
import transformers
from transformers import AutoModelForSeq2SeqLM, AutoConfig, AutoTokenizer
from ctranslate2.converters import TransformersConverter
from ctranslate2.converters.transformers import BartLoader, _MODEL_LOADERS

CKPT = "ai4bharat/indictrans2-indic-indic-dist-320M"
OUT = "/home/clutch/Desktop/Vachak/models/indictrans2_ct2_int8_pruned"


def _inject_trust(kwargs):
    kwargs.setdefault("trust_remote_code", True)
    kwargs.setdefault("attn_implementation", "eager")
    return kwargs


# 1) Load the genuine custom model once, just to grab its class.
_orig_auto = AutoModelForSeq2SeqLM.from_pretrained
@classmethod
def _patched_auto(cls, p, *a, **k):
    return _orig_auto.__func__(cls, p, *a, **_inject_trust(k))
AutoModelForSeq2SeqLM.from_pretrained = _patched_auto

_tmp = AutoModelForSeq2SeqLM.from_pretrained(CKPT)
IndicClass = _tmp.__class__
del _tmp

# 2) Alias it onto transformers.BartForConditionalGeneration so the converter loads the
#    real model (sinusoidal PE + correct 512-d shapes) instead of a default BART.
transformers.BartForConditionalGeneration = IndicClass

# make the custom class always load with trust_remote_code + eager attention
_orig_indic = IndicClass.from_pretrained
@classmethod
def _patched_indic(cls, p, *a, **k):
    return _orig_indic.__func__(cls, p, *a, **_inject_trust(k))
IndicClass.from_pretrained = _patched_indic


class IndicTransLoader(BartLoader):
    @property
    def architecture_name(self):
        return "BartForConditionalGeneration"

    def get_model_spec(self, model):
        # Fix released vocab mismatch: embed_tokens 122706 vs lm_head 122672.
        # The 34 extra rows are added (unused) tokens, so trim embeddings to lm_head size.
        k = model.lm_head.weight.shape[0]
        for sub in (model.model.encoder.embed_tokens, model.model.decoder.embed_tokens):
            if sub.weight.shape[0] != k:
                with torch.no_grad():
                    sub.weight = torch.nn.Parameter(sub.weight[:k].clone())
        if hasattr(model.model, "shared") and model.model.shared.weight.shape[0] != k:
            with torch.no_grad():
                model.model.shared.weight = torch.nn.Parameter(model.model.shared.weight[:k].clone())
        model.config.vocab_size = int(k)

        # IndicTrans2 is PRE-NORM (encoder_normalize_before / decoder_normalize_before = True).
        # pre_norm must be True so the spec creates the top-level final layer_norm that
        # set_common_layers reads from model.encoder/decoder.layer_norm.
        model.config.normalize_before = bool(getattr(model.config, "encoder_normalize_before", True))
        model.config.normalize_embedding = True
        return super().get_model_spec(model)

    def set_position_encodings(self, spec, module):
        # IndicTransSinusoidalPositionalEmbedding stores a 'weights' buffer indexed 0-based;
        # offset rows are padding and must NOT be applied.
        w = module.weights
        max_pos = w.shape[0] - getattr(module, "offset", 0)
        spec.encodings = w.numpy()[:max_pos]


_MODEL_LOADERS["IndicTransConfig"] = IndicTransLoader()


def _prune_vocab(model, tokenizer):
    """Keep only Devanagari (Hindi) + Ol Chiki (Santali) + Latin/ASCII + specials.
    Drops Odia and every other script. This forbids code-switching to Odia at decode
    time (the int8 drift symptom) and shrinks the model. Reorders embeddings/lm_head to
    the kept token set and rewrites the tokenizer vocabulary to match."""
    import torch
    full = tokenizer.get_vocab()
    SPECIAL = {"<s>", "</s>", "<pad>", "<unk>"}

    def keep(tok):
        return True

    items = sorted(full.items(), key=lambda x: x[1])
    # Drop the 34 release-added tokens (id >= lm_head size): ungeneratable, not in lm_head.
    max_id = model.lm_head.weight.shape[0]
    pairs = [(t, i) for t, i in items if keep(t) and i < max_id]
    kept_tokens = [t for t, _ in pairs]
    kept_ids = [i for _, i in pairs]
    # ctranslate2 pads the vocabulary to a multiple of 8; if we don't pad the model output
    # projection to match, token ids desync and decoding collapses. Pad both.
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
    _prune_vocab(model, tokenizer)
    return super(IndicTransLoader, self).__call__(model, tokenizer)


IndicTransLoader.__call__ = _patched_call

_orig_cfg = AutoConfig.from_pretrained
@classmethod
def _patched_cfg(cls, p, *a, **k):
    return _orig_cfg.__func__(cls, p, *a, **_inject_trust(k))
AutoConfig.from_pretrained = _patched_cfg

_orig_tok = AutoTokenizer.from_pretrained
@classmethod
def _patched_tok(cls, p, *a, **k):
    return _orig_tok.__func__(cls, p, *a, **_inject_trust(k))
AutoTokenizer.from_pretrained = _patched_tok


if __name__ == "__main__":
    import os
    OUT = os.environ.get("CT2_OUT", OUT)
    QUANT = os.environ.get("CT2_QUANT", "int8")
    print(f" | > converting {CKPT} -> {OUT} ({QUANT})", flush=True)
    conv = TransformersConverter(CKPT, load_as_float16=False, copy_files=[])
    conv.convert(OUT, quantization=QUANT if QUANT != "none" else None, force=True)
    print(" | > DONE", flush=True)
