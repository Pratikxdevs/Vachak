# Santali VITS Sprint — Report

Date: 2026-09-13. Runtime: Colab T4 16GB. Framework: coqui-tts 0.27.5 (train from scratch, no base model).

## Data (untouched)
- `wavs/`: 1000 verified 22050 Hz mono clips (Ol Chiki)
- `coqui_train.csv`: 900 rows, `coqui_dev.csv`: 100 rows, `charset.json`: 38 symbols
- No data file was modified at any point.

## Training
- Config: VITS, batch 32, mixed precision, `train_sprint.py --epochs 400`
- Run 1 (00:20–03:22 UTC): epochs 0–169, steps 0–4500. loss_mel 43 → 30.4, loss_kl 3.1 → ~1.3, loss_0 oscillating ~2.3–2.6.
- Probe at step 3500: export + verify PASS PASS (rms ~0.18). Kept training (losses still falling).
- Run 1 stopped at epoch 169 (loss_0 plateaued ~2.4); final export from its `best_model.pth` verified PASS.
- Run 2 (05:20–05:47 UTC): resumed `--restore checkpoint_4500.pth`, reached epoch ~15.
  Note: checkpoints store `epoch` but the resumed run restarts its epoch counter at 0, so
  `--epochs N` on a resumed run means N *more* epochs, not N total.
- Incident: plain `kill` did not stop run 1 (still held 11.6 GB VRAM) and OOM'd the next
  start. Fixed with `kill -9`, VRAM verified at 0 before restarting. No checkpoint was lost.
- Run 3 (capped, 05:50–07:53 UTC): `--epochs 125 --restore <run2 checkpoint_500>`,
  auto-finished at 125/125. Grand total ≈ 300 epochs (~8.5k steps).
- Final losses: loss_mel ~26.6, loss_kl ~0.9, loss_0 ~2.4–2.6 (GAN oscillation, expected).

## Export fixes (`export_sprint.py` only)
1. `pick_checkpoint` searches `runs/vits_sprint/` recursively — the trainer writes into a
   timestamped subdir, the old path found nothing.
2. Force legacy TorchScript ONNX exporter (`dynamo=False`) — torch 2.11 defaults to the
   dynamo exporter, which crashes on VITS `rational_quadratic_spline`.
3. Vocab dump uses installed API (`id_to_char`/`vocab`/`num_chars`; `char_to_id` is a
   method in 0.27.5, not a dict) and writes single-character tokens only — sherpa's
   character frontend aborts on multi-char specials (`<PAD>`, `<s>`, `</s>`, `<BLNK>`,
   "size: 4" error). Specials remain in ONNX metadata (pad 0 / eos 1 / bos 2 / blank 3).
4. Verify passes `lexicon.txt` (word boundaries) instead of `lexicon=""`; both paths PASS.

## Final verification (shipped checkpoint: run-3 `checkpoint_3000.pth`)
- `vits-sat.onnx`: 110 MB, opset 17, checker PASS. Inputs `input`/`input_lengths`/`scales`.
- Metadata: sample_rate 22050, language `sat`, frontend `characters`.
- `tokens.txt`: 38 symbols (ids 4–41). `lexicon.txt`: 200 top words + single-char fallback.
- `verify.log`: PASS `ᱡᱚᱦᱟᱨ` 17664 samples rms 0.1827 / PASS `ᱟᱞᱮ ᱟᱥᱲᱟ ᱥᱮᱱᱚᱜ ᱠᱟᱱᱟ`
  41216 samples rms 0.1891 (bar: PASS PASS + rms ≥ 0.05).
- Runner-up run-3 `best_model.pth` also verified PASS (rms 0.1501/0.1732); shipped the
  newer checkpoint on higher rms + lower mel loss.
- External cross-check (second machine, sherpa 1.13.8): 3/3 utterances PASS, peaks ~0.84,
  durations scale with text. Rough-demo grade, as calibrated for the budget.

## Artifacts
- Ship: `export/vits-sat.onnx`, `export/tokens.txt`, `export/lexicon.txt`, `export/verify.log`
- Backup: `/content/drive/MyDrive/sprint4h_backup/` (best + latest checkpoints, `config.json`,
  `export_final/` copy of ship files). Resume any time with
  `train_sprint.py --epochs N --batch 32 --restore <checkpoint.pth>`.
