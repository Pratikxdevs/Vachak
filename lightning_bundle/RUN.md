# Santali bidirectional fine-tune on Lightning AI (T4, free tier)

Upload this whole folder to a Lightning Studio, run, download one file back.

## 0. One-time setup (per Studio)

1. New Studio → GPU **T4** (16 GB). Free tier restarts every ~4h — normal,
   the training resumes (step 4).
2. Upload this folder (or `git` it / attach cloud storage).
3. Request access to the base model (gated, usually auto-approved):
   https://huggingface.co/ai4bharat/indictrans2-indic-indic-dist-320M
4. In the Studio terminal:

```bash
cd lightning_bundle
pip install -r requirements.txt
huggingface-cli login   # paste a token from huggingface.co/settings/tokens
```

## 1. Launch training (fresh)

```bash
nohup python train.py --data_dir data --output_dir adapter_sat_bidi \
  > train.log 2>&1 &
tail -f train.log
```

Expected: ~10–15 min/epoch, 12 epochs ≈ 2–3h. Every epoch saves
`adapter_sat_bidi/` (latest) and `adapter_sat_bidi/best_adapter/`
(best dev loss). Watch for `NEW BEST` lines.

## 2. After a Studio restart (resume, loses nothing)

Re-run setup if the machine is fresh, then:

```bash
cd lightning_bundle
nohup python train.py --data_dir data --output_dir adapter_sat_bidi \
  --resume adapter_sat_bidi > train.log 2>&1 &
```

It reads `training_state.json` and continues the epoch count.

## 3. Score it (held-out tests, never trained on)

```bash
python eval.py --adapter adapter_sat_bidi/best_adapter --data_dir data
```

You get BLEU/chrF for `test_classroom` (short + school-word Hindi rows)
and `test_general`, in both directions. **The classroom column is the
number that matters** for student↔teacher use.

## 4. Bring it home

Download `adapter_sat_bidi/best_adapter/` (2 files, ~50–100 MB) into the
repo at `ml/finetune/` and wire it into the app's MT slot. On-device
inference is unchanged (same offline <3s pipeline).

## 5. Export for the app (merge + CT2 INT8, on the Studio)

Copy `merge_export_ct2.py` to the Studio bundle dir, then:

```bash
cd ~/lightning_bundle
pip install ctranslate2
python merge_export_ct2.py --adapter adapter_sat_bidi/best_adapter
python merge_export_ct2.py --sanity   # must print real Ol Chiki, not "." or repeats
```

Paste the sanity output + the reported CT2 size. Then download the
`ct2_sat_bidi_int8/` dir into the repo as `modelpacks/sat_bidi_ct2_int8/`.
Local wiring (`TRANSLATION_BACKEND=satfinal`) is already in place and tested.

## Data provenance

`data/` was rebuilt from `/cz/Vachak/material` (10,623 kept pairs after
dropping 15 trash rows: 1 missing Ol Chiki script, 10 duplicates,
4 overlong). Splits: 9,423 train / 600 dev / 300 classroom-test /
300 general-test. See `data/manifest.json`. Tests are held out.
