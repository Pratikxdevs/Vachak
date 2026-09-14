# STUDIO_AGENT.md — paste this (or its Session Goal section) into a fresh
# OpenCode session INSIDE Lightning Studio, with lightning_bundle_tts/ uploaded.

---

## Session Goal (paste from here)

You are working in `lightning_bundle_tts/`, a self-contained Santali TTS
distillation bundle. No repo access, no local machine — everything happens here.

**Mission:** produce `export/vits-sat.onnx` + `tokens.txt` + `lexicon.txt`
(a small Santali VITS voice for an offline Android tablet) WITHOUT training
from scratch: synthesize teacher audio with pretrained Quipus (Sido voice),
then distill a small Coqui VITS on it.

**Hard rules:**
1. `HF_TOKEN` comes from the environment ONLY (`echo $HF_TOKEN` to confirm
   set; if empty, STOP and ask the user). Never write it to any file, never
   print it, never commit anything.
2. NEVER modify `data/transcripts.tsv`, `data/splits.json`,
   `data/manifest.json`. They are the frozen contract (5284 lines / 4557-304-203).
3. Every long step must be resumable AND resumed after any Studio restart —
   re-run the same command, never start over.
4. Do NOT proceed past a failed gate. Gates are marked GATE below.
5. Keep all work inside this folder. Disk needed: ~5GB free
   (1.2GB teacher + ~1.5GB teacher wavs + checkpoints).

**Execute in order. Full detail per step is in RUN.md — follow it literally:**

- **Step 0 — Env:** `nvidia-smi` (must show a GPU, T4+), `pip install -r
  requirements.txt`, confirm `HF_TOKEN` set, `df -h .` (~5GB free).
- **Step 1 — Teacher:** `python quipus_sat.py --download-model`
- **Step 2 — GATE 1 (Sido validation):** `python quipus_sat.py --validate`.
  Requires 3/3 PASS lines + 3 wavs in `runs/`. Report the 3 lines back.
  If any FAIL → STOP, do not continue, report output.
- **Step 3 — Bulk teacher:** `python quipus_sat.py --bulk --limit 50`,
  report; then full `nohup python quipus_sat.py --bulk > bulk.log 2>&1 &`.
  Done = `teacher/manifest.tsv` has ~5284 data rows. Re-run same command
  after restarts (it skips finished rows; `FAIL <utt>` lines retry next run).
- **Step 4 — Train:** `nohup python finetune_vits.py --epochs 300 --batch 16
  > train.log 2>&1 &`. Watch `tail -f train.log`: loss_0 must fall
  (starts ~6, should drop below ~2 within first 1000 steps). OOM →
  retry `--batch 8`. After ANY restart resume with
  `--restore runs/vits_sat` appended. Checkpoints land in `runs/vits_sat/`
  every 2000 steps.
- **Step 5 — GATE 2 (listening):** `python listen_eval.py --checkpoint
  runs/vits_sat/best_model.pth` (else latest `checkpoint_*.pth`).
  Requires all PASS in `listen/report.txt`. Then tell the user to download
  `listen/` + `runs/quipus_sido_*.wav` and LISTEN. Student ≈ Sido =
  training done — stop early even at epoch ~50-100, do NOT burn all 300.
  If unintelligible after ~100 epochs → report `train.log` tail + report,
  do not export.
- **Step 6 — Export + verify:** `python export_onnx.py --checkpoint
  runs/vits_sat/best_model.pth` then `python export_onnx.py --verify`.
  Requires PASS lines in `export/verify.log` + `vits-sat.onnx` ~20-80MB.

**When done, report back (paste into chat):**
1. `listen/report.txt` (full)
2. `export/verify.log` (full)
3. `ls -lh export/ teacher/manifest.tsv | wc -l` (sizes + teacher row count)
4. Last 15 lines of `train.log` + epochs/steps reached
5. Anything you changed vs RUN.md and why

The user will say which files to download home. Do not delete `runs/`,
`teacher/`, or `quipus_model/` until told.

## Notes for the human (not the agent)

- The agent needs GPU Studio (T4 16GB+), the folder uploaded, and
  `HF_TOKEN` set. Paste everything under "Session Goal".
- The two GATEs are the only places requiring human ears; everything else
  the agent can grind through restarts alone.
- Expected wall time: validate ~10 min, bulk ~5-15h, train to
  intelligible ~4-10h on T4, export ~10 min.
- Bring-home set (you download): `export/vits-sat.onnx`, `export/tokens.txt`,
  `export/lexicon.txt`, `export/verify.log`, `listen/report.txt`.
  Paste those two logs back to the local session for app wiring.
