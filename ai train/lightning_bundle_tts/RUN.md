# Santali voice on Lightning AI (no from-scratch local training)

Distill a small on-device Santali VITS from the pretrained Quipus teacher
(Sido, male, MIT) — all heavy work happens on cloud GPUs. Upload this folder
to a Studio, run, download a few small files back.

Teacher = `hyperneuronAILabs/quipus-0.6-speechv2` (0.6B, MIT, gated).
Student = small Coqui VITS, Ol Chiki char tokens → `vits-sat.onnx` for
sherpa-onnx on the tablet (20–80 MB slice, 22050 Hz, `dataDir=''`).

## 0. One-time setup (per Studio)

1. New Studio → GPU **T4** (16 GB) or better. Free tier restarts every ~4h —
   normal; every step below is resumable.
2. Upload this folder (or `git` it / attach storage).
3. Accept the teacher gate (logged-in click, one time):
   https://huggingface.co/hyperneuronAILabs/quipus-0.6-speechv2
4. In the Studio terminal:

```bash
cd lightning_bundle_tts
pip install -r requirements.txt
export HF_TOKEN=...   # huggingface.co/settings/tokens (read access is enough)
```

## 1. Fetch the teacher (~1.2GB, once)

```bash
python quipus_sat.py --download-model
```

## 2. Validate Sido (THE go/no-go gate, ~10 min)

```bash
python quipus_sat.py --validate
```

You get `runs/quipus_sido_{johaar,mt-question,lesson-line}.wav` + PASS/FAIL
lines. **Download and LISTEN.** You need intelligible native-sounding
Santali. If Sido fails → stop here and report back (fallback: local
from-scratch Coqui run / Phulmani). Do NOT run bulk on a failed voice.

## 3. Bulk teacher synthesis (overnight, resumable)

Smoke test first, then full:

```bash
python quipus_sat.py --bulk --limit 50
# spot-listen to teacher/wavs_24k/*.wav, then:
nohup python quipus_sat.py --bulk > bulk.log 2>&1 &
```

5,284 utterances (~7.5h audio). Manifest `teacher/manifest.tsv` skips
finished rows, so after a Studio restart just re-run the same command.
Failures print `FAIL <utt_id>` and are retried next run.

## 4. Train the student VITS (resumable)

```bash
nohup python finetune_vits.py --epochs 300 --batch 16 > train.log 2>&1 &
tail -f train.log
```

`finetune_vits.py` resamples teacher 24k→22050 itself (librosa) and writes
`teacher/coqui_{train,dev}.csv` from `data/splits.json` (4557/304).
Checkpoints every 2000 steps in `runs/vits_sat/`. After a restart:

```bash
nohup python finetune_vits.py --epochs 300 --batch 16 \
  --restore runs/vits_sat > train.log 2>&1 &
```

(`--restore` takes a run dir or a `checkpoint_*.pth`; T4 fits batch 16–32.)

## 5. Listening eval (the listening bundle)

```bash
python listen_eval.py --checkpoint runs/vits_sat/best_model.pth
```

Synthesizes fixed Ol Chiki probes (same 3 as Sido validation + 2 held-out
lines) into `listen/student_*.wav` + `listen/report.txt`.
**Download `listen/` AND `runs/quipus_sido_*.wav`, A/B by ear.**
Student ≈ Sido on ᱡᱚᱦᱟᱨ + questions = training is done (often well before
epoch 300 on clean single-teacher data — stop early and save money).

## 6. Export for the tablet + verify (on the Studio)

```bash
python export_onnx.py --checkpoint runs/vits_sat/best_model.pth
python export_onnx.py --verify   # must print PASS lines into export/verify.log
```

Uses Coqui's built-in `export_onnx` (inputs input/input_lengths/scales =
exactly what sherpa's Piper/Coqui path feeds), converts opset→17, patches
sherpa metadata (`language=sat`, `comment` contains `coqui`,
`frontend=characters`), dumps `tokens.txt` from the live vocab (id order —
guaranteed training-consistent) + layout-compat `lexicon.txt`.

## 7. Bring it home (small files only)

Download `export/vits-sat.onnx` + `export/tokens.txt` +
`export/lexicon.txt` + `export/verify.log` into the repo as
`models/vits-sat.onnx` (+ Android assets copy). Paste `verify.log` +
`listen/report.txt` back. The tablet path (sherpa char tokens,
`dataDir=''`, sequential <3s pipeline) is unchanged.

## Data provenance

`data/transcripts.tsv` (5,284 Ol Chiki lines) derives from the repo's
`ml/tts/dataset/santali_olchiki.tsv` (web corpus transliterated
Devanagari→Ol Chiki); `data/splits.json` = 4557/304/203. Teacher audio is
**synthetic Sido voice (MIT)** — no human speaker is cloned, so the
VOICE_CONSENT gate clears for the distilled student. See
`data/manifest.json`. No IN22 anywhere.
