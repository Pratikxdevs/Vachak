#!/usr/bin/env python3
"""
export_onnx.py — distilled VITS checkpoint -> sherpa-onnx Android voice.

Run ON THE STUDIO after listen_eval.py sounds right:

  python export_onnx.py --checkpoint runs/vits_sat/best_model.pth
  python export_onnx.py --verify   # needs pip install sherpa-onnx

Steps:
  1. Load checkpoint into Coqui VITS (config.json + weights from run dir).
  2. model.export_onnx() (Coqui built-in: inputs input/input_lengths/scales,
     exactly what sherpa's RunVitsPiperOrCoqui feeds) -> opset 15 -> convert
     to opset 17 (repo convention).
  3. Patch ONNX metadata for sherpa-onnx OfflineTts VITS:
       sample_rate=22050, n_speakers=1, language=sat,
       comment='coqui-santali-sido' (contains 'coqui' -> is_coqui path),
       frontend='characters' (char tokens, dataDir='' on Android),
       bos/eos/blank/pad ids + add_blank/use_eos_bos from the live config.
  4. Dump tokens.txt from the LIVE vocab (id order — guaranteed consistent
     with training) + lexicon.txt (asset-layout compat; char path ignores it).
  5. --verify loads export/ via sherpa_onnx and asserts 'ᱡᱚᱦᱟᱨ' > 200ms.

Bring home: export/vits-sat.onnx + tokens.txt + lexicon.txt + verify.log
  -> repo as models/vits-sat.onnx (+ Android assets via ml/tts/train.py-style
  copy). ~20-60MB expected.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPORT = HERE / "export"


def load_model(ckpt: str):
    import torch
    from TTS.tts.configs.vits_config import VitsConfig
    from TTS.tts.models.vits import Vits
    from TTS.tts.utils.text.tokenizer import TTSTokenizer
    from TTS.utils.audio import AudioProcessor

    run_dir = Path(ckpt).parent
    cfg_path = run_dir / "config.json"
    config = VitsConfig()
    config.load_json(str(cfg_path))
    ap = AudioProcessor.init_from_config(config)
    tokenizer, config = TTSTokenizer.init_from_config(config)
    model = Vits(config, ap, tokenizer, speaker_manager=None)
    state = torch.load(ckpt, map_location="cpu", weights_only=False)
    model.load_state_dict(state.get("model", state))
    model.eval()
    print(f"[export] loaded {ckpt}")
    return model, tokenizer, config


def vocab_list(tokenizer) -> list[str]:
    """Chars in Coqui id order (index 0..N-1)."""
    chars = tokenizer.characters
    inv = {v: k for k, v in chars.char_to_id.items()}
    return [inv[i] for i in range(len(inv))]


def cmd_export(args) -> None:
    import onnx

    EXPORT.mkdir(parents=True, exist_ok=True)
    model, tokenizer, config = load_model(args.checkpoint)
    tmp = EXPORT / "vits-sat-opset15.onnx"
    model.export_onnx(str(tmp), verbose=False)
    m = onnx.load(str(tmp))
    try:
        m = onnx.version_converter.convert_version(m, 17)
        print("[export] opset -> 17")
    except Exception as e:
        print(f"[export] opset convert skipped ({e})")

    chars = tokenizer.characters
    meta = {
        "sample_rate": "22050",
        "n_speakers": "1",
        "language": "sat",
        "comment": "coqui-santali-sido-distilled",
        "frontend": "characters",
        "bos_id": str(chars.bos_id),
        "eos_id": str(chars.eos_id),
        "blank_id": str(chars.blank_id),
        "pad_id": str(chars.pad_id),
        "add_blank": "1" if getattr(config, "add_blank", True) else "0",
        "use_eos_bos": "1",
        "voice": "santali-sido-distilled",
    }
    keys = {p.key for p in m.metadata_props}
    for k, v in meta.items():
        if k in keys:
            for p in m.metadata_props:
                if p.key == k:
                    p.value = v
        else:
            p = m.metadata_props.add()
            p.key, p.value = k, v
    out = EXPORT / "vits-sat.onnx"
    onnx.save(m, str(out))
    onnx.checker.check_model(str(out))
    print(f"[export] {out} ({out.stat().st_size / 1e6:.1f} MB, checker PASS)")

    vocab = vocab_list(tokenizer)
    with open(EXPORT / "tokens.txt", "w", encoding="utf-8") as f:
        for i, ch in enumerate(vocab):
            f.write(f"{ch} {i}\n")
    print(f"[export] tokens.txt ({len(vocab)} symbols, "
          f"pad={chars.pad_id} bos={chars.bos_id} "
          f"eos={chars.eos_id} blank={chars.blank_id})")
    freq: dict[str, int] = {}
    for line in (HERE / "data" / "transcripts.tsv").read_text(
            encoding="utf-8").splitlines():
        if "\t" in line:
            for w in line.split("\t", 1)[1].split():
                freq[w] = freq.get(w, 0) + 1
    top = sorted(freq, key=freq.get, reverse=True)[:200]
    with open(EXPORT / "lexicon.txt", "w", encoding="utf-8") as f:
        for w in top:
            f.write(f"{w}  {' '.join(c for c in w)}\n")
        for ch in vocab:
            if len(ch) == 1 and "\u1c50" <= ch <= "\u1c7f":
                f.write(f"{ch}  {ch}\n")
    print("[export] lexicon.txt (layout compat; char path ignores it)")


def cmd_verify(args) -> int:
    import sherpa_onnx

    onnx_path = EXPORT / "vits-sat.onnx"
    assert onnx_path.exists(), "run --checkpoint first"
    v = sherpa_onnx.OfflineTtsVitsModelConfig(
        model=str(onnx_path), lexicon="", tokens=str(EXPORT / "tokens.txt"),
        data_dir="")
    mc = sherpa_onnx.OfflineTtsModelConfig(vits=v, num_threads=1,
                                           provider="cpu")
    tts = sherpa_onnx.OfflineTts(sherpa_onnx.OfflineTtsConfig(model=mc))
    lines = [f"export verify sr={tts.sample_rate}"]
    ok_all = True
    for text in ["ᱡᱚᱦᱟᱨ", "ᱟᱞᱮ ᱟᱥᱲᱟ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟ"]:
        audio = tts.generate(text, sid=0, speed=1.0)
        import numpy as np
        a = np.array(audio.samples)
        rms = float((a ** 2).mean() ** 0.5)
        ok = len(audio.samples) > 0.2 * tts.sample_rate and rms > 0.005
        ok_all &= ok
        lines.append(f"[verify] {'PASS' if ok else 'FAIL'} {text[:20]}: "
                     f"{len(audio.samples)} samples, rms {rms:.4f}")
    (EXPORT / "verify.log").write_text("\n".join(lines) + "\n",
                                       encoding="utf-8")
    print("\n".join(lines))
    return 0 if ok_all else 1


def main() -> None:
    ap = argparse.ArgumentParser(description="Export distilled VITS to ONNX")
    ap.add_argument("--checkpoint", default=None)
    ap.add_argument("--verify", action="store_true")
    args = ap.parse_args()
    if args.verify:
        raise SystemExit(cmd_verify(args))
    if not args.checkpoint:
        raise SystemExit("pass --checkpoint runs/vits_sat/best_model.pth")
    cmd_export(args)


if __name__ == "__main__":
    main()
