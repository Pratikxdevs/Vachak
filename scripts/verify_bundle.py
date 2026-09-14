#!/usr/bin/env python3
"""Verify an assembled MT bundle: full hi->sat greedy translation in Python,
mirroring OnnxIndicTrans2Adapter (repetition_penalty=1.2, no_repeat_trigram=3).
Fails loud on loops, blanks, or non-Ol-Chiki output.
"""
import re
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

OL_CHIKI = re.compile(r"[\u1c50-\u1c7f]")


def main(bundle: str):
    b = Path(bundle)
    sess_opts = ort.SessionOptions()
    sess_opts.intra_op_num_threads = 4
    enc = ort.InferenceSession(str(b / "encoder_model.onnx"), sess_opts, providers=["CPUExecutionProvider"])
    dec = ort.InferenceSession(str(b / "decoder_model.onnx"), sess_opts, providers=["CPUExecutionProvider"])
    past = ort.InferenceSession(str(b / "decoder_with_past_model.onnx"), sess_opts, providers=["CPUExecutionProvider"])
    print("enc in:", [i.name for i in enc.get_inputs()], "dec out0:", dec.get_outputs()[0].name, "past outs:", len(past.get_outputs()))
    src_tok = Tokenizer.from_file(str(b / "tokenizer_src.json"))
    tgt_tok = Tokenizer.from_file(str(b / "tokenizer_tgt.json"))
    print("src vocab:", src_tok.get_vocab_size(), "tgt vocab:", tgt_tok.get_vocab_size())

    # Preprocess mirrors IndicProcessorPort: lang tags around Hindi.
    LANG_SRC, LANG_TGT = "hin_Deva", "sat_Olck"

    def translate(text, max_len=64):
        pre = f"{LANG_SRC} {text} {LANG_TGT}"
        ids = src_tok.encode(pre).ids + [2]
        n = len(ids)
        ids_np = np.array([ids], dtype=np.int64)
        mask = np.ones((1, n), dtype=np.int64)
        hidden = enc.run(None, {"input_ids": ids_np, "attention_mask": mask})[0]
        out_ids = []
        past_vals = None
        next_id = 2
        for _ in range(max_len):
            if past_vals is None:
                res = dec.run(None, {"input_ids": np.array([[next_id]], dtype=np.int64),
                                     "encoder_attention_mask": mask,
                                     "encoder_hidden_states": hidden.astype(np.float32)})
            else:
                feed = {"input_ids": np.array([[next_id]], dtype=np.int64),
                        "encoder_attention_mask": mask}
                for o, v in zip(past.get_inputs()[2:], past_vals):
                    feed[o.name] = v
                res = past.run(None, feed)
            logits = res[0][0][0]
            # repetition_penalty=1.2
            for gid in set(out_ids):
                if gid < len(logits):
                    logits[gid] = logits[gid] / 1.2 if logits[gid] > 0 else logits[gid] * 1.2
            # no_repeat_trigram ban
            if len(out_ids) >= 2:
                tail = out_ids[-2:]
                for i in range(len(out_ids) - 2):
                    if out_ids[i:i + 2] == tail:
                        banned = out_ids[i + 2]
                        if banned < len(logits):
                            logits[banned] = -np.inf
            next_id = int(np.argmax(logits))
            past_vals = res[1:]
            if next_id == 2:
                break
            out_ids.append(next_id)
        text_out = tgt_tok.decode(out_ids).replace("▁", " ").strip()
        return text_out, len(out_ids)

    tests = [
        "नमस्कार क्या हाल",
        "नमस्कार आपका क्या नाम है",
        "मेरा नाम क्या है",
        "आज हम क्या पढ़ेंगे",
        "छः हाथी",
    ]
    ok = True
    seen = set()
    for t in tests:
        try:
            out, steps = translate(t)
        except Exception as e:
            print(f"FAIL translate {t!r}: {e}")
            ok = False
            continue
        has_ol = bool(OL_CHIKI.search(out))
        words = out.split()
        loop = len(words) >= 6 and len(set(words[-6:])) <= 2
        dup = out in seen
        seen.add(out)
        flag = []
        if not out:
            flag.append("BLANK")
        if not has_ol:
            flag.append("NO-OLCHIKI")
        if loop:
            flag.append("LOOP")
        if dup:
            flag.append("DUP-OF-EARLIER")
        if steps >= 64:
            flag.append("HIT-MAXLEN")
        print(f"[{' '.join(flag) or 'OK'}] {t!r} -> {out!r} ({steps} steps)")
        if flag:
            ok = False
    print("VERIFY " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
