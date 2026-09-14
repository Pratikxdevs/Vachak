# STUDIO_AGENT_FULLRUN.md — paste the Session Goal into a fresh Studio agent run

Context: last session did a 19-clip demo (overfit, silent vocoder, verify
FAIL FAIL). This run does the FULL production voice. Workspace already has:
`quipus_model/` (1.2GB, keep), `teacher/` (24 wavs + manifest, keep —
bulk appends), `parallel_fast_bulk.py` (keep using), `runs/vits_sat/`
(19-clip overfit — PRESERVE, do not train into it).

---

## Session Goal (paste from here)

Mission: full Santali VITS voice. Quipus Sido teacher → bulk-synthesize ALL
5284 transcripts → fresh full training → listening gate → verified ONNX export.

**Hard rules:**
1. `HF_TOKEN`/tokens from environment only. Never write secrets to files,
   never print them, never commit.
2. NEVER modify `lightning_bundle_tts/data/` (frozen contract).
3. NEVER train into `runs/vits_sat/` (overfit demo). New run goes to
   `runs/vits_sat_full/`. Preserve the old dir (rename, don't delete).
4. Every long step resumes after Studio restarts — re-run the same command.
5. Do NOT proceed past a failed gate. `df -h .` first: need ~8GB free.

**Step 0 — Env (deps DIFFER from requirements.txt — use this exact line):**
```bash
pip install -q "coqui-tts[codec]==0.27.5" transformers==4.57.1 snac soundfile librosa huggingface_hub tensorboard sherpa-onnx onnx
nvidia-smi  # must show GPU
```
(coqui 0.27.5 needs transformers ≥4.57; 4.53.3 breaks. torch 2.11 pairs fine.)

**Step 1 — Bulk teacher to 5284 (resumable):**
Reuse `parallel_fast_bulk.py` (shortest-first, 3 workers — proven: 4.4GB/70%).
It must APPEND to `teacher/manifest.tsv` in format
`utt_id<TAB>wavs_24k/<id>.wav<TAB>ol_text<TAB>Sido<TAB>dur`.
Done = manifest has ~5284 data rows (header + 5284). Spot-listen 3 random
new wavs. If parallel script is missing, fallback: `python quipus_sat.py
--bulk` (slower, same manifest).

**Step 2 — Fresh full train (NOT a resume):**
```bash
mv runs/vits_sat runs/vits_sat_19clip_demo   # preserve demo, train clean
nohup python finetune_vits.py --epochs 300 --batch 32 --run-dir runs/vits_sat_full > train_full.log 2>&1 &
```
If `finetune_vits.py` lacks `--run-dir`, add it (run dir must be
`runs/vits_sat_full`, run_name `santali_vits_sido_full`). Keep default save
steps (2000/1000/best-after-10000) — the demo's 50/25/30 patch was for the
tiny run only. Watch `tail -f train_full.log`: loss_0 must fall (~6 → <2 in
first 1000 steps). OOM → `--batch 16`. After ANY restart, re-run WITH
`--restore runs/vits_sat_full`.

**Step 3 — GATE (listening, the only human step):**
```bash
python listen_eval.py --checkpoint runs/vits_sat_full/best_model.pth
```
If `listen_eval.py` hits the `Config for vits_sat_sido` Synthesizer error
again: debug it (config.json sits next to the checkpoint; Synthesizer needs
`tts_config_path` pointed there) — fallback is direct-model synthesis, do
not skip listening. Tell the user to download `listen/` +
`runs/quipus_sido_*.wav` and A/B by ear. Student ≈ Sido = done (may stop
well before epoch 300). Unintelligible after ~100 epochs → report train
tail + report, do not export.

**Step 4 — Export + verify (reuse the demo's working fixes):**
`dynamo=False` on the torch export call (torch 2.11), vocab-as-list API,
strip `</s>`-style specials from `tokens.txt` for sherpa, opset→17,
metadata (`sample_rate=22050 n_speakers=1 language=sat comment contains
coqui frontend=characters` + live bos/eos/blank/pad ids).
```bash
python export_onnx.py --checkpoint runs/vits_sat_full/best_model.pth
python export_onnx.py --verify
```
REQUIRED: `export/verify.log` shows PASS PASS **with rms ≥ 0.05**
(healthy is ~0.1+; the demo's 0.004 = silence, reject). vits-sat.onnx
should be ~40-80MB.

**Report back (paste into chat):**
1. `teacher/manifest.tsv` data-row count + 3 spot-listen filenames
2. `listen/report.txt` (full) + last 15 lines of `train_full.log` + epochs reached
3. `export/verify.log` (full) + `ls -lh export/`
4. Anything changed vs this brief and why

Bring-home set (user downloads): `export/vits-sat.onnx`, `export/tokens.txt`,
`export/lexicon.txt`, `export/verify.log`, `listen/report.txt`,
`runs/quipus_sido_*.wav` (refs). Do not delete `runs/`, `teacher/`,
`quipus_model/` until told.

---

## Notes for the human (not the agent)

- Upload: nothing new needed if the Studio workspace persists (bundle +
  brief travel together). If fresh Studio: upload `lightning_bundle_tts/`
  + this file.
- Your two ear-gates: (1) spot-listen bulk samples, (2) student-vs-Sido A/B.
- The 19-clip demo proved the pipeline end-to-end; this run is the same
  pipeline at full scale. Only new risk is wall time: bulk ~5-15h + train
  to intelligible ~8-20h on T4 — free-tier restarts are handled by resume.
- When `verify.log` shows PASS PASS with rms ≥ 0.05, send the 4 export
  files + `listen/report.txt` to the local session for APK wiring
  (~470-490MB projected, still under the 500MB budget).
