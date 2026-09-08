#!/usr/bin/env python3
"""Export IndicTrans2 (indic-indic-dist-320M, or a finetuned variant) to a 3-graph
ONNX bundle: encoder_model.onnx, decoder_model.onnx, decoder_with_past_model.onnx.

Uses the naklitechie/indictrans2-onnx-export I/O contract (see it2_innx_wrappers.py)
so the greedy-decode inference in it2_inference.py works unchanged. Encoder weights
>100 MB are externalized to encoder_model.onnx.data so the proto stays small.
"""
from __future__ import annotations

import argparse
import shutil
import logging
from pathlib import Path

import torch
import onnx

from it2_onnx_wrappers import (
    IndicTransDecoderWithPastWrapper,
    IndicTransDecoderWrapper,
    IndicTransEncoderWrapper,
    past_input_names,
    present_output_names,
    weights_are_tied,
)
from huggingface_hub import hf_hub_download

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger(__name__)

BATCH, ENC_SEQ, DEC_SEQ, NUM_HEADS, HEAD_DIM = 1, 64, 1, 8, 64
EXTERNALIZE_THRESHOLD_MB = 100


def _export_encoder(encoder, out, opset):
    w = IndicTransEncoderWrapper(encoder).eval()
    ids = torch.ones(BATCH, ENC_SEQ, dtype=torch.long)
    mask = torch.ones(BATCH, ENC_SEQ, dtype=torch.long)
    p = out / "encoder_model.onnx"
    logger.info("export encoder -> %s", p.name)
    torch.onnx.export(w, (ids, mask), str(p),
                      input_names=["input_ids", "attention_mask"],
                      output_names=["last_hidden_state"],
                      dynamic_axes={"input_ids": {0: "b", 1: "s"},
                                    "attention_mask": {0: "b", 1: "s"},
                                    "last_hidden_state": {0: "b", 1: "s"}},
                      opset_version=opset, do_constant_folding=True, dynamo=False)
    return p


def _export_decoder(decoder, lm_head, num_layers, embed_dim, out, opset):
    w = IndicTransDecoderWrapper(decoder, lm_head).eval()
    ids = torch.ones(BATCH, DEC_SEQ, dtype=torch.long)
    amask = torch.ones(BATCH, ENC_SEQ, dtype=torch.long)
    hs = torch.randn(BATCH, ENC_SEQ, embed_dim)
    out_names = ["logits", *present_output_names(num_layers)]
    p = out / "decoder_model.onnx"
    logger.info("export decoder -> %s", p.name)
    torch.onnx.export(w, (ids, amask, hs), str(p),
                      input_names=["input_ids", "encoder_attention_mask", "encoder_hidden_states"],
                      output_names=out_names,
                      dynamic_axes={"input_ids": {0: "b", 1: "d"},
                                    "encoder_attention_mask": {0: "b", 1: "s"},
                                    "encoder_hidden_states": {0: "b", 1: "s"},
                                    "logits": {0: "b", 1: "d"}},
                      opset_version=opset, do_constant_folding=True, dynamo=False)
    return p


def _export_decoder_with_past(decoder, lm_head, num_layers, out, opset):
    w = IndicTransDecoderWithPastWrapper(decoder, lm_head, num_layers).eval()
    ids = torch.ones(BATCH, DEC_SEQ, dtype=torch.long)
    amask = torch.ones(BATCH, ENC_SEQ, dtype=torch.long)
    past = []
    for _ in range(num_layers):
        past += [torch.randn(BATCH, NUM_HEADS, 1, HEAD_DIM),
                 torch.randn(BATCH, NUM_HEADS, 1, HEAD_DIM),
                 torch.randn(BATCH, NUM_HEADS, ENC_SEQ, HEAD_DIM),
                 torch.randn(BATCH, NUM_HEADS, ENC_SEQ, HEAD_DIM)]
    in_names = ["input_ids", "encoder_attention_mask", *past_input_names(num_layers)]
    out_names = ["logits", *present_output_names(num_layers)]
    p = out / "decoder_with_past_model.onnx"
    logger.info("export decoder_with_past -> %s", p.name)
    dyn: dict = {"input_ids": {0: "b", 1: "d"},
                 "encoder_attention_mask": {0: "b", 1: "s"},
                 "logits": {0: "b", 1: "d"}}
    for i in range(num_layers):
        dyn[f"past_key_values.{i}.decoder.key"] = {0: "b", 2: "pk"}
        dyn[f"past_key_values.{i}.decoder.value"] = {0: "b", 2: "pk"}
        dyn[f"past_key_values.{i}.encoder.key"] = {0: "b", 2: "s"}
        dyn[f"past_key_values.{i}.encoder.value"] = {0: "b", 2: "s"}
        dyn[f"present.{i}.decoder.key"] = {0: "b", 2: "pk1"}
        dyn[f"present.{i}.decoder.value"] = {0: "b", 2: "pk1"}
        dyn[f"present.{i}.encoder.key"] = {0: "b", 2: "s"}
        dyn[f"present.{i}.encoder.value"] = {0: "b", 2: "s"}
    torch.onnx.export(w, (ids, amask, *past), str(p),
                      input_names=in_names, output_names=out_names,
                      dynamic_axes=dyn, opset_version=opset,
                      do_constant_folding=True, dynamo=False)
    return p


def _externalize(path: Path):
    m = onnx.load(str(path))
    onnx.save(m, str(path), save_as_external_data=True,
              all_tensors_to_one_file=True, location=path.name + ".data",
              size_threshold=1024 * 1024 * EXTERNALIZE_THRESHOLD_MB)
    logger.info("externalized %s", path.name)


def _copy_artifacts(model_id, out):
    for fn in ("config.json", "generation_config.json", "dict.SRC.json",
               "dict.TGT.json", "model.SRC", "model.TGT",
               "tokenization_indictrans.py", "tokenizer_config.json",
               "special_tokens_map.json"):
        try:
            p = hf_hub_download(repo_id=model_id, filename=fn)
            shutil.copy2(p, out / fn)
            logger.info("copied %s", fn)
        except Exception:
            logger.debug("skip %s", fn)


def export(model_id, out: Path, opset=17, finetuned_dir=None):
    out.mkdir(parents=True, exist_ok=True)
    logger.info("loading %s", model_id)
    from transformers import AutoModelForSeq2SeqLM
    model = AutoModelForSeq2SeqLM.from_pretrained(
        finetuned_dir or model_id, trust_remote_code=True).eval()
    num_layers = getattr(model.config, "decoder_layers", None) or model.config.num_hidden_layers
    embed_dim = getattr(model.config, "decoder_embed_dim", None) or model.config.d_model
    logger.info("tied=%s layers=%d embed=%d", weights_are_tied(model.model.decoder, model.lm_head), num_layers, embed_dim)

    _export_encoder(model.model.encoder, out, opset)
    _export_decoder(model.model.decoder, model.lm_head, num_layers, embed_dim, out, opset)
    _export_decoder_with_past(model.model.decoder, model.lm_head, num_layers, out, opset)
    for f in ("encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx"):
        _externalize(out / f)
    _copy_artifacts(model_id, out)

    total = sum(f.stat().st_size for f in out.iterdir() if f.suffix in (".onnx", ".data")) / 1e6
    logger.info("DONE fp32 bundle ~%.0f MB at %s", total, out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="ai4bharat/indictrans2-indic-indic-dist-320M")
    ap.add_argument("--finetuned_dir", default=None, help="load a merged finetuned model dir instead")
    ap.add_argument("--output", required=True, type=Path)
    ap.add_argument("--opset", type=int, default=17)
    a = ap.parse_args()
    export(a.model, a.output, a.opset, a.finetuned_dir)


if __name__ == "__main__":
    main()
