#!/usr/bin/env python3
"""
finetune_vits.py — small Santali VITS distilled from Quipus Sido teacher audio.

STANDALONE (Lightning Studio). Pipeline:
  teacher/wavs/*.wav (24kHz Sido, from quipus_sat.py --bulk)
    -> resample 22050 mono (librosa, resumable) -> teacher/wavs_22050/
    -> coqui_train.csv / coqui_dev.csv (id|text|text, splits.json)
    -> Coqui VITS char-mode training (Ol Chiki vocab, no espeak)

  python finetune_vits.py --epochs 300 --batch 16
  python finetune_vits.py --epochs 300 --batch 16 --restore runs/vits_sat  # resume

T4 16GB: batch 16-32 fits. ~143 steps/epoch at batch 32. Checkpoints every
2000 steps; stop when listen_eval.py samples are intelligible (often well
before 300 epochs on clean single-teacher data).
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = HERE / "data"
TEACHER = HERE / "teacher"
RUNS_DEFAULT = HERE / "runs" / "vits_sat"


def load_splits():
    return json.loads((DATA / "splits.json").read_text(encoding="utf-8"))


def load_transcripts():
    out = {}
    for line in (DATA / "transcripts.tsv").read_text(
            encoding="utf-8").splitlines():
        if "\t" in line:
            uid, txt = line.split("\t", 1)
            out[uid.strip()] = txt.strip()
    return out


def build_charset() -> Path:
    texts = load_transcripts()
    chars: set[str] = set()
    for t in texts.values():
        chars.update(t)
    chars.discard(" ")
    charset = sorted(chars)
    bad = [c for c in charset
           if not ("\u1c50" <= c <= "\u1c7f" or c in "।.,?!:;'-()\"/—–")]
    assert not bad, f"non-OlChiki chars: {[hex(ord(c)) for c in bad]}"
    out = DATA / "charset.json"
    out.write_text(json.dumps({"characters": charset}, indent=1,
                              ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"[charset] {len(charset)} symbols -> {out}")
    return out


def prep_teacher_audio() -> None:
    """Resample teacher 24k -> 22050 mono + write coqui CSVs (resumable)."""
    import librosa
    import soundfile as sf

    manifest = TEACHER / "manifest.tsv"
    if not manifest.exists():
        raise SystemExit("missing teacher/manifest.tsv — run quipus_sat.py --bulk first")
    have = {}
    for line in manifest.read_text(encoding="utf-8").splitlines()[1:]:
        if line.strip():
            p = line.split("\t")
            have[p[0]] = p[2]
    print(f"[prep] teacher utterances: {len(have)}")
    out_dir = TEACHER / "wavs"  # formatter resolves <path>/wavs/<id>.wav
    out_dir.mkdir(parents=True, exist_ok=True)
    texts = load_transcripts()
    todo = [u for u in have if not (out_dir / f"{u}.wav").exists()]
    print(f"[prep] resampling {len(todo)} (cached {len(have) - len(todo)}) ...")
    for n, uid in enumerate(todo):
        src = TEACHER / "wavs_24k" / f"{uid}.wav"
        y, _ = librosa.load(str(src), sr=22050, mono=True)
        sf.write(str(out_dir / f"{uid}.wav"), y, 22050)
        if (n + 1) % 500 == 0:
            print(f"[prep] {n + 1}/{len(todo)}")
    splits = load_splits()
    for split, csv in (("train", "coqui_train.csv"), ("dev", "coqui_dev.csv")):
        lines = []
        for uid in splits[split]:
            if uid in have and (out_dir / f"{uid}.wav").exists():
                t = texts[uid]
                assert "|" not in t
                lines.append(f"{uid}|{t}|{t}")
        (TEACHER / csv).write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"[prep] {csv}: {len(lines)} rows "
              f"(split wanted {len(splits[split])})")


def main() -> None:
    ap = argparse.ArgumentParser(description="Distill Santali VITS from Sido")
    ap.add_argument("--epochs", type=int, default=300)
    ap.add_argument("--batch", type=int, default=16)
    ap.add_argument("--run-dir", default=str(RUNS_DEFAULT))
    ap.add_argument("--restore", default=None,
                    help="checkpoint dir or .pth to resume after restart")
    args = ap.parse_args()

    build_charset()
    prep_teacher_audio()

    from trainer import Trainer, TrainerArgs

    from TTS.tts.configs.shared_configs import (BaseDatasetConfig,
                                                CharactersConfig)
    from TTS.tts.configs.vits_config import VitsConfig
    from TTS.tts.datasets import load_tts_samples
    from TTS.tts.models.vits import Vits, VitsAudioConfig
    from TTS.tts.utils.text.tokenizer import TTSTokenizer
    from TTS.utils.audio import AudioProcessor

    charset = json.loads((DATA / "charset.json").read_text(
        encoding="utf-8"))["characters"]
    dataset_config = BaseDatasetConfig(
        formatter="ljspeech",
        meta_file_train="coqui_train.csv",
        meta_file_val="coqui_dev.csv",
        path=str(TEACHER),
        language="sat",
    )
    # NOTE: formatter resolves audio at <path>/wavs/<id>.wav (= 22050 set above)
    config = VitsConfig(
        model="vits_sat_sido",
        run_name="santali_vits_sido",
        project_name="vachak",
        run_description="Santali Ol-Chiki VITS distilled from Quipus Sido",
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
        max_audio_len=int(12.5 * 22050),  # coqui lengths are SAMPLES
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
    train_samples, eval_samples = load_tts_samples(
        dataset_config, eval_split=True)
    print(f"[data] train={len(train_samples)} eval={len(eval_samples)}")
    tokenizer, config = TTSTokenizer.init_from_config(config)
    model = Vits(config, ap_audio, tokenizer, speaker_manager=None)
    trainer = Trainer(TrainerArgs(restore_path=args.restore), config,
                      args.run_dir, model=model,
                      train_samples=train_samples,
                      eval_samples=eval_samples)
    trainer.fit()


if __name__ == "__main__":
    main()
