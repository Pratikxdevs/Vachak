"""Real Hindi->Santali baseline via IndicTrans2 CTranslate2 INT8 (AI4Bharat dist-320M).

This module reuses, minimally, the cloned IndicTrans2 pipeline:
  * tokenizer: IndicTransTokenizer (tokenization_indictrans.py + SRC/TGT spm + dict),
    loaded from the re-saved BART weights dir (models/indictrans2_bart).
  * runtime: ctranslate2.Translator over the exported INT8 checkpoint
    (models/indictrans2_ct2_int8).

IMPORTANT: this is a LABELED STAND-IN for the Mundari target. It produces Santali
(sat_Olck), NOT Mundari. Never present its output as approved Mundari pedagogy.

Attribution / license: IndicTrans2 is MIT-licensed (AI4Bharat). See repo LICENSE and
the project THIRD_PARTY_NOTICES.md. The exported INT8 weights are derived from
`ai4bharat/indictrans2-indic-indic-dist-320M` (MIT) via ctranslate2's TransformersConverter.

Heavy imports (ctranslate2, transformers, sentencepiece, IndicTransToolkit) are deferred
to load() so unit tests that use the mock adapter stay dependency-free.
"""

from __future__ import annotations

import logging
import os
import time
from pathlib import Path
from typing import List, Optional

LOG = logging.getLogger("Vachak-MT")

_DEFAULT_ROOT = Path(__file__).resolve().parent.parent.parent.parent
DEFAULT_MODEL_DIR = os.environ.get(
    "INDICTRANS2_MODEL_DIR",
    str(_DEFAULT_ROOT / "models" / "indictrans2_ct2_int8"),
)
DEFAULT_TOKENIZER_DIR = os.environ.get(
    "INDICTRANS2_TOKENIZER_DIR",
    str(_DEFAULT_ROOT / "models" / "indictrans2_bart"),
)
_INDIC_RESOURCES = str(_DEFAULT_ROOT / "IndicTrans2" / "indic_nlp_resources")

# Flashlight-style normalization of user-facing language codes to IndicTrans2 Flores codes.
# Bidirectional hin<->sat (the fine-tuned adapter trains both directions).
_SRC_FLORES = {
    "hin": "hin_Deva", "hi": "hin_Deva", "hindi": "hin_Deva",
    "sat": "sat_Olck", "sat_olck": "sat_Olck", "sat_Olck": "sat_Olck",
    "olck": "sat_Olck", "santali": "sat_Olck",
}
_TGT_FLORES = {
    "sat": "sat_Olck",
    "sat_olck": "sat_Olck",
    "sat_Olck": "sat_Olck",
    "olck": "sat_Olck",
    "santali": "sat_Olck",
    "hin": "hin_Deva", "hi": "hin_Deva", "hindi": "hin_Deva",
    # The Mundari target does NOT exist yet; the baseline can only emit Santali.
    "mun": "sat_Olck",
    "mundari": "sat_Olck",
}


class IndicTrans2CT2Baseline:
    """Thin, real inference wrapper around the IndicTrans2 CT2 INT8 checkpoint."""

    def __init__(self, model_dir: str = DEFAULT_MODEL_DIR, tokenizer_dir: str = DEFAULT_TOKENIZER_DIR):
        self.model_dir = model_dir or DEFAULT_MODEL_DIR
        self.tokenizer_dir = tokenizer_dir or DEFAULT_TOKENIZER_DIR
        self._translator = None
        self._tok = None
        self._proc = None
        self._loaded = False

    def load(self) -> None:
        """Load model + tokenizer. Raises RuntimeError if the artifact is missing."""
        if self._loaded:
            return
        t0 = time.time()
        LOG.debug("[Vachak-MT] load: importing heavy deps (ctranslate2/transformers/IndicTransToolkit)")
        import ctranslate2  # noqa: F401
        from transformers import AutoTokenizer
        from IndicTransToolkit import IndicProcessor

        if not os.path.isdir(self.model_dir):
            raise RuntimeError(f"CT2 model dir missing: {self.model_dir}")
        if not os.path.isdir(self.tokenizer_dir):
            raise RuntimeError(f"tokenizer dir missing: {self.tokenizer_dir}")

        os.environ.setdefault("INDIC_RESOURCES_PATH", _INDIC_RESOURCES)

        LOG.debug("[Vachak-MT] load: model_dir=%s tokenizer_dir=%s", self.model_dir, self.tokenizer_dir)
        self._tok = AutoTokenizer.from_pretrained(self.tokenizer_dir, trust_remote_code=True)
        self._proc = IndicProcessor(inference=True)
        self._translator = ctranslate2.Translator(self.model_dir, device="cpu")
        self._loaded = True
        LOG.debug("[Vachak-MT] load: ready in %.1fms", (time.time() - t0) * 1000)

    @staticmethod
    def _flores(source: str, target: str):
        s = _SRC_FLORES.get((source or "").lower())
        t = _TGT_FLORES.get((target or "").lower())
        if not s or not t:
            raise ValueError(
                f"unsupported pair {source}->{target}; engine supports hin/hi <-> sat/sat_Olck (NOT Mundari)"
            )
        if s == t:
            raise ValueError(f"source and target are the same language ({s})")
        return s, t

    def translate(
        self, texts: List[str], source: str = "hin", target: str = "sat"
    ) -> List[str]:
        self.load()
        src_lang, tgt_lang = self._flores(source, target)
        LOG.debug("[Vachak-MT] input: source=%s target=%s n=%d", src_lang, tgt_lang, len(texts))

        t0 = time.time()
        batch = self._proc.preprocess_batch(texts, src_lang=src_lang, tgt_lang=tgt_lang)
        LOG.debug("[Vachak-MT] tokenize: prefixed_sample=%r", batch[:1])

        enc = self._tok(batch, return_tensors="pt", padding=True, truncation=True, max_length=256)
        src_tokens = [self._tok.convert_ids_to_tokens(ids) for ids in enc.input_ids.tolist()]
        LOG.debug("[Vachak-MT] tokenize: src_tokens[0]=%r", src_tokens[:1])
        t_tok = time.time()

        results = self._translator.translate_batch(src_tokens, max_decoding_length=256, beam_size=4)
        t_dec = time.time()

        hypos = [r.hypotheses[0] for r in results]
        # Hypotheses are target-vocab SentencePiece pieces — join directly.
        # Do NOT round-trip through the HF tokenizer (convert_tokens_to_ids
        # + batch_decode): the SRC/TGT dicts have different orderings, so
        # re-indexing target pieces in SRC-vocab order scrambles scripts.
        _SKIP = {"</s>", "<s>", "<pad>", "<unk>"}
        text = [ "".join(p for p in h if p not in _SKIP).replace("▁", " ").strip()
                 for h in hypos ]
        out = self._proc.postprocess_batch(text, lang=tgt_lang)

        LOG.debug("[Vachak-MT] decode: latency=%.1fms", (t_dec - t_tok) * 1000)
        LOG.debug("[Vachak-MT] output: %r", out[:1])
        LOG.debug("[Vachak-MT] total_latency=%.1fms", (time.time() - t0) * 1000)
        return out


# Ensure DEBUG logs tagged "Vachak-MT" are emitted whenever the engine is loaded.
if not LOG.handlers:
    _h = logging.StreamHandler()
    _h.setFormatter(logging.Formatter("%(levelname)s %(name)s %(message)s"))
    LOG.addHandler(_h)
LOG.setLevel(logging.DEBUG)
