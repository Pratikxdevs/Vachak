#!/usr/bin/env python3
"""
finetune_sat.py — Santali (Ol Chiki) VITS training on the native-speaker corpus.

Runs under ml/tts/.venv-tts (Python 3.11 + torch cu130 + coqui-tts), NOT the
system Python 3.14 (numba/librosa have no 3.14 wheels).

  ml/tts/.venv-tts/bin/python ml/tts/finetune_sat.py --epochs 500 --batch 8

Data (prepared, no network):
  ml/tts/dataset/wavs_22050/*.wav  (5284 x 22050 Hz mono PCM16, silence-trimmed)
  ml/tts/dataset/metadata_train.csv (4557 utt / 7.46h, `relpath|OlChiki text`)
  ml/tts/dataset/metadata_dev.csv   (304 utt)
  (held>12.5s in held_long.txt; test in metadata_eval.csv)

Model: Coqui VITS from scratch, CHARACTER tokens (use_phonemes=False —
espeak-ng has no Santali voice, so phoneme mode is off the table), Ol Chiki
vocab from olchiki_charset.json. Audio 22050 Hz / hop 256 / win 1024 /
mel 80 to match ml/tts/pipeline.py:TTSConfig and the APK contract.

4GB VRAM (RTX 3050): batch 8, mixed_precision, grad clip 1.0, workers 4.
~570 steps/epoch; intelligible single-speaker VITS typically needs 200-400k
steps on 7-10h — this is a multi-day background run. Resume with the same
--run-dir (Coqui auto-resumes). Stop when dev mel plateaus AND the listen
test (synthesize ᱡᱚᱦᱟᱨ + eval lines) is intelligible Santali.

Export (after convergence, future step): torch.onnx.export the inference
path (text->wav, monotonic path, HiFi-GAN vocoder merged) opset 17 with
sherpa-onnx VITS metadata (sample_rate, n_speakers, language=sat, comment
containing 'coqui' for the RunVitsPiperOrCoqui path), extended tokens.txt
(Ol Chiki + punct), lexicon.txt -> models/vits-sat.onnx. Pattern reference:
sherpa-onnx vits-zh-aishell3 (Coqui-trained VITS exported to ONNX).
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DS = ROOT / "ml" / "tts" / "dataset"


def build_charset() -> Path:
    """Collect Ol Chiki vocab from transliterated corpus + write charset json."""
    chars: set[str] = set()
    for name in ("metadata_train.csv", "metadata_dev.csv"):
        for line in (DS / name).read_text(encoding="utf-8").splitlines():
            _, text = line.split("|", 1)
            chars.update(text)
    chars.discard(" ")
    charset = sorted(chars)
    # sanity: everything must be Ol Chiki block or approved punctuation
    bad = [c for c in charset
           if not ("\u1c50" <= c <= "\u1c7f" or c in "।.,?!:;'-()\"/—–")]
    assert not bad, f"non-OlChiki chars in corpus: {[hex(ord(c)) for c in bad]}"
    out = DS / "olchiki_charset.json"
    out.write_text(json.dumps({"characters": charset}, indent=1,
                              ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"[charset] {len(charset)} symbols -> {out}")
    return out


def ensure_coqui_meta() -> None:
    """Write Coqui ljspeech-shaped metadata (id|text|text) + wavs/ symlink.

    Canonical files (metadata_*.csv with relpath|text) stay untouched; these
    derived files match the formatter's `{root}/wavs/<id>.wav` expectation.
    """
    link = DS / "wavs"
    if not link.exists():
        link.symlink_to("wavs_22050", target_is_directory=True)
        print(f"[coqui] symlink {link} -> wavs_22050")
    for src, dst in (("metadata_train.csv", "coqui_train.csv"),
                     ("metadata_dev.csv", "coqui_dev.csv")):
        out_lines = []
        for line in (DS / src).read_text(encoding="utf-8").splitlines():
            rel, text = line.split("|", 1)
            uid = Path(rel).stem
            assert "|" not in text
            out_lines.append(f"{uid}|{text}|{text}")
        (DS / dst).write_text("\n".join(out_lines) + "\n", encoding="utf-8")
        print(f"[coqui] {dst} ({len(out_lines)} rows)")


def main() -> None:
    ap = argparse.ArgumentParser(description="Train Santali Ol-Chiki VITS")
    ap.add_argument("--epochs", type=int, default=500)
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--run-dir", default=str(
        ROOT / "ml" / "tts" / "runs" / "santali_vits_native"))
    ap.add_argument("--charset-only", action="store_true")
    args = ap.parse_args()

    if not (DS / "olchiki_charset.json").exists() or args.charset_only:
        build_charset()
        if args.charset_only:
            return
    ensure_coqui_meta()

    from trainer import Trainer, TrainerArgs

    from TTS.tts.configs.shared_configs import (BaseDatasetConfig,
                                                CharactersConfig)
    from TTS.tts.configs.vits_config import VitsConfig
    from TTS.tts.datasets import load_tts_samples
    from TTS.tts.models.vits import Vits, VitsAudioConfig
    from TTS.tts.utils.text.tokenizer import TTSTokenizer
    from TTS.utils.audio import AudioProcessor

    charset = json.loads((DS / "olchiki_charset.json").read_text(
        encoding="utf-8"))["characters"]

    dataset_config = BaseDatasetConfig(
        formatter="ljspeech",
        meta_file_train="coqui_train.csv",
        meta_file_val="coqui_dev.csv",
        path=str(DS),
        language="sat",
    )
    config = VitsConfig(
        model="vits_sat_olchiki",
        run_name="santali_vits_native",
        project_name="vachak",
        run_description="Santali Ol-Chiki VITS from scratch, 7.5h single-male",
        dashboard_logger="tensorboard",
        logger_uri=args.run_dir,
        audio=VitsAudioConfig(
            sample_rate=22050,
            hop_length=256,
            win_length=1024,
            fft_size=1024,
            mel_fmin=0,
            mel_fmax=8000,
            num_mels=80,
        ),
        batch_size=args.batch,
        batch_group_size=0,
        eval_batch_size=8,
        num_loader_workers=4,
        num_eval_loader_workers=2,
        mixed_precision=True,
        epochs=args.epochs,
        text_cleaner="no_cleaners",
        use_phonemes=False,
        phonemizer=None,
        phoneme_language=None,
        compute_input_seq_cache=True,
        print_step=50,
        print_eval=True,
        save_step=2000,
        run_eval_steps=1000,
        save_n_checkpoints=5,
        save_checkpoints=True,
        save_all_best=True,
        target_loss="loss_0",
        save_best_after=10000,
        characters=CharactersConfig(
            characters="".join(charset),
            punctuations="",
            pad="<PAD>",
            eos="</s>",
            bos="<s>",
            blank="<BLNK>",
            is_unique=True,
            is_sorted=True,
        ),
        phoneme_cache_path=None,
        precompute_num_workers=4,
        start_by_longest=True,
        # NOTE: coqui length filters are in SAMPLES (get_audio_size), not sec
        max_audio_len=int(12.5 * 22050),
        min_audio_len=int(1.5 * 22050),
        max_text_len=400,
        min_text_len=1,
        test_sentences=[
            "ᱡᱚᱦᱟᱨ",
            "ᱟᱞᱮ ᱟᱥᱲᱟ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟ",
            "ᱱᱚᱣᱟ ᱫᱚ ᱥᱟᱱᱛᱟᱲᱤ ᱚᱞ ᱪᱤᱠᱤ ᱠᱟᱱᱟ",
        ],
        use_speaker_embedding=False,
        use_d_vector_file=False,
    )

    ap_audio = AudioProcessor.init_from_config(config)
    # pre-split CSVs: eval_split=True + meta_file_val uses coqui_dev.csv
    train_samples, eval_samples = load_tts_samples(
        dataset_config, eval_split=True)
    print(f"[data] train={len(train_samples)} eval={len(eval_samples)}")

    tokenizer, config = TTSTokenizer.init_from_config(config)
    model = Vits(config, ap_audio, tokenizer, speaker_manager=None)
    trainer = Trainer(TrainerArgs(restore_path=None), config, args.run_dir,
                      model=model, train_samples=train_samples,
                      eval_samples=eval_samples)
    trainer.fit()


if __name__ == "__main__":
    main()
