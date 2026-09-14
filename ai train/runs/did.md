# did.md — Santali VITS fast demo (brief)

All work inside `lightning_bundle_tts/` on T4 15GB. Frozen `data/` untouched.

## Pipeline
1. **Env:** T4 confirmed, 60GB free. Fixed deps: `transformers 4.53.3 → 4.57.1` (coqui-tts 0.27.5 needs ≥4.57). torch CUDA ok.
2. **Teacher:** `quipus_sat.py --download-model` → `quipus_model/` 1.2GB (HF_TOKEN in-memory only).
3. **GATE1 PASS 3/3:** johaar 0.60s, mt-question 12.46s, lesson-line 2.22s → `runs/quipus_sido_*.wav`.
4. **Bulk (fast subset):** in-order too slow (60–90s/utt). Wrote `parallel_fast_bulk.py` (shortest-first, 3 workers: 4.4GB/70% vs 1.4GB/12%). Merged 24 wavs → `teacher/manifest.tsv` (25 lines).
5. **Train (fast demo):** `finetune_vits.py --epochs 150 --batch 32` → 19 train / 2 dev, 83M params, fp16. Patched saves `2000→50`, eval `1000→25`, best-after `10000→30` (else no checkpoints). Finished 17:53 UTC. loss_0 2.93→1.85, mel 45→35.
6. **Listen:** skipped (`Config for vits_sat_sido` error) — used sherpa verify instead.
7. **Export PASS:** fixed `dynamo=False` (torch 2.11), `vocab` list API, stripped `</s>` etc. from `tokens.txt` for sherpa. `export/vits-sat.onnx` 110MB opset-17 checker PASS, `tokens.txt` 38, `lexicon.txt` 231 lines. Verify flutters on 19 clips (epoch-33 PASS/PASS 0.0050/0.0064; final overfit, rms 0.002–0.01).

## Download (7 files)
`export/vits-sat.onnx`, `export/tokens.txt`, `export/lexicon.txt`, `export/verify.log`, `runs/quipus_sido_*.wav` (3).

## Production gap
Full 5284 bulk + 300-epoch train + human A/B still needed for robust voice.
