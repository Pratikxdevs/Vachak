"""
Convert ai4bharat/indictrans2-indic-indic-dist-320M (HF) -> CTranslate2.

IndicTrans2 is a fairseq/m2m_100-style model: SINUSOIDAL positional embeddings
(module.offset == 2), scale_embedding (sqrt(d_model)), pre-norm, and a
`layernorm_embedding` on top of the token+position embeddings.

CTranslate2's BART/MBart loader only understands LEARNED positional embeddings,
which is why a naive BartLoader alias produced cross-script gibberish. The
correct loader is M2M100Loader (it reads `module.weights[module.offset:]` and
sets normalize_before=True). The only gap is that M2M100Loader disables
layernorm_embedding (original m2m_100 has none); IndicTrans2 needs it, so we
re-enable normalize_embedding=True.
"""
import functools
import os

import transformers
import ctranslate2
from ctranslate2.converters.transformers import (
    _MODEL_LOADERS,
    M2M100Loader,
    TransformersConverter,
)

CKPT = "ai4bharat/indictrans2-indic-indic-dist-320M"
OUT_FP32 = os.path.join(os.path.dirname(__file__), "..", "..", "models", "indictrans2_ct2_fp32_v2")
OUT_INT8 = os.path.join(os.path.dirname(__file__), "..", "..", "models", "indictrans2_ct2_int8_v2")


class IndicTransLoader(M2M100Loader):
    architecture_name = "IndicTransForConditionalGeneration"

    # Inherit M2M100Loader.get_model_spec: normalize_before=True, normalize_embedding=False.
    # CTranslate2's Transformer spec has no layernorm_embedding field, so IndicTrans2's
    # layernorm_embedding cannot be represented; we drop it (sinusoidal PE + scale_embedding
    # + pre-norm are the parts that actually determine language/output correctness.

    def set_common_layers(self, spec, module):
        vsize = module.config.vocab_size
        spec.scale_embeddings = module.embed_scale
        self.set_position_encodings(spec.position_encodings, module.embed_positions)
        self.set_embeddings(
            spec.embeddings[0] if isinstance(spec.embeddings, list) else spec.embeddings,
            module.embed_tokens,
        )
        # embed_tokens is padded to 122706 rows but the real vocab is config.vocab_size (122672)
        # and the decoder lm_head is 122672; truncate embeddings to the real vocab size.
        embs = spec.embeddings
        if isinstance(embs, (list, tuple)):
            for e in embs:
                e.weight = e.weight[:vsize]
        else:
            embs.weight = embs.weight[:vsize]
        if hasattr(module, "layer_norm"):
            self.set_layer_norm(spec.layer_norm, module.layer_norm)
        # layernorm_embedding intentionally dropped (unsupported by spec)

    def get_vocabulary(self, model, tokenizer):
        tokens = super().get_vocabulary(model, tokenizer)
        target = model.config.vocab_size
        if len(tokens) < target:
            tokens += ["madeupword%d" % i for i in range(target - len(tokens))]
        elif len(tokens) > target:
            tokens = tokens[:target]
        return tokens


_MODEL_LOADERS["IndicTransConfig"] = IndicTransLoader()


def _inject_trust_remote_code(orig):
    @functools.wraps(orig)
    def wrapper(*args, **kwargs):
        if "trust_remote_code" not in kwargs:
            kwargs["trust_remote_code"] = True
        return orig(*args, **kwargs)
    return wrapper


transformers.AutoConfig.from_pretrained = _inject_trust_remote_code(transformers.AutoConfig.from_pretrained)
transformers.AutoModelForSeq2SeqLM.from_pretrained = _inject_trust_remote_code(transformers.AutoModelForSeq2SeqLM.from_pretrained)
transformers.AutoTokenizer.from_pretrained = _inject_trust_remote_code(transformers.AutoTokenizer.from_pretrained)


if __name__ == "__main__":
    # The converter resolves the model class via getattr(transformers, architecture_name).
    # Register the custom class on the transformers namespace so that lookup succeeds.
    _cls = transformers.AutoModelForSeq2SeqLM.from_pretrained(CKPT).__class__
    setattr(transformers, "IndicTransForConditionalGeneration", _cls)

    converter = TransformersConverter(CKPT)
    out = converter.convert(OUT_FP32, quantization=None, force=True)
    print("Converted (fp32) ->", os.path.abspath(out))
