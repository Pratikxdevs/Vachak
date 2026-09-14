#!/usr/bin/env python3
"""Assemble the on-device MT bundle from a quantized export.

Reproduces the shipped layout:
  encoder_model.onnx (+ .data) | decoder_model.onnx | decoder_with_past_model.onnx
  decoder_shared.onnx.data (ONE copy of the decoder weights, referenced by BOTH
  decoder protos) | tokenizer_src/tgt.json + meta + configs + gold.tsv

The two decoder graphs contain identical decoder weights (same export), so the
with-past proto's external tensors are remapped by content-hash into the shared
file instead of shipping a second 200MB copy. Covers initializers AND Constant
node values (embed_positions lives in a Constant). Verified by an ORT forward
of all three graphs afterwards (run verify_bundle.py).
"""
import shutil
import sys
from pathlib import Path

import onnx
from onnx import AttributeProto

MODELS = ("encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx")


def all_tensors(model):
    """Every TensorProto that may reference external data: initializers AND
    Constant-node values."""
    for t in model.graph.initializer:
        yield t
    for n in model.graph.node:
        if n.op_type != "Constant":
            continue
        for a in n.attribute:
            if a.type == AttributeProto.TENSOR:
                yield a.t


def tensor_bytes(data_path: Path, tensor) -> bytes:
    ext = {e.key: e.value for e in tensor.external_data}
    with open(data_path, "rb") as f:
        f.seek(int(ext["offset"]))
        return f.read(int(ext["length"]))


def point_shared(t, off, ln):
    del t.external_data[:]
    e = t.external_data.add()
    e.key, e.value = "location", "decoder_shared.onnx.data"
    e = t.external_data.add()
    e.key, e.value = "offset", str(off)
    e = t.external_data.add()
    e.key, e.value = "length", str(ln)


def main(src: str, dst: str):
    src, dst = Path(src), Path(dst)
    dst.mkdir(parents=True, exist_ok=True)
    # Encoder: copy as-is (own .data).
    shutil.copy2(src / "encoder_model.onnx", dst / "encoder_model.onnx")
    shutil.copy2(src / "encoder_model.onnx.data", dst / "encoder_model.onnx.data")
    # Canonical shared file = decoder .data.
    shutil.copy2(src / "decoder_model.onnx.data", dst / "decoder_shared.onnx.data")
    shared = dst / "decoder_shared.onnx.data"
    print("indexing shared data...", flush=True)
    shared_bytes = bytearray(shared.read_bytes())
    print(f"shared size {len(shared_bytes)}", flush=True)
    # Index decoder tensors by (name, len) for the same-name fast path.
    dec = onnx.load(str(src / "decoder_model.onnx"), load_external_data=False)
    dec_index = {}
    for t in all_tensors(dec):
        ext = {e.key: e.value for e in t.external_data}
        if "offset" in ext:
            dec_index[(t.name, int(ext["length"]))] = (int(ext["offset"]), int(ext["length"]))
    print(f"decoder external tensors: {len(dec_index)}", flush=True)

    appended_total = 0
    with open(shared, "ab") as out:
        for name in ("decoder_model.onnx", "decoder_with_past_model.onnx"):
            m = onnx.load(str(src / name), load_external_data=False)
            is_decoder = (name == "decoder_model.onnx")
            data_path = src / (name + ".data")
            changed = False
            for t in all_tensors(m):
                ext = {e.key: e.value for e in t.external_data}
                if "location" not in ext:
                    continue
                if is_decoder:
                    point_shared(t, ext["offset"], ext["length"])
                else:
                    raw = tensor_bytes(data_path, t)
                    key = (t.name, len(raw))
                    hit = dec_index.get(key)
                    if hit is not None and bytes(shared_bytes[hit[0]:hit[0] + hit[1]]) == raw:
                        point_shared(t, *hit)
                    else:
                        pos = bytes(shared_bytes).find(raw)
                        if pos != -1:
                            point_shared(t, pos, len(raw))
                        else:
                            off = len(shared_bytes)
                            out.write(raw)
                            out.flush()
                            shared_bytes += raw
                            point_shared(t, off, len(raw))
                            appended_total += len(raw)
                changed = True
            if changed:
                onnx.save(m, str(dst / name))
                print(f"remapped {name}", flush=True)
    print("DONE appended-unique bytes:", appended_total)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
