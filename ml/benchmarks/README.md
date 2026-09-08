# ml/benchmarks — Offline Benchmark Harness (PHASE 11)

Measures, never fabricates. Every metric is computed or explicitly marked
**PENDING REAL-DEVICE MEASUREMENT**.

## Categories
- **Translation**: BLEU, chrF, human, terminology coverage
- **ASR**: WER, CER
- **TTS**: MOS, intelligibility, first-audio latency
- **Runtime**: RAM, CPU, model size, cold start, warm/cold inference, battery
- **Offline**: airplane-mode test, network-request audit
- **Voice pipeline**: T0→T4 end-to-end

## Device matrix (`device_matrix.py`)
Target 2GB Android 9 · modern 4GB Android 13 · dev machine. Dev-machine latency
is **never** reported as Android latency — `forbid_dev_as_android` fails the
report if a latency metric is mislabeled.

## Pure-python metrics (`harness.py`)
`chrf`, `wer`, `cer` implemented with no external deps so quality math is
reproducible offline. `network_request_audit()` statically proves the pipeline
imports no `socket/urllib/requests/http`.

## Run
```bash
python -m ml.benchmarks.run --device android2gb --out report.json
# on real hardware only:
python -m ml.benchmarks.run --device android2gb --real-android --out report.json
```
Device choices: `android2gb | android_modern | dev`. `--real-android` is ONLY
valid on the device; otherwise latency/RAM stay PENDING.

## Tests
`tests/test_harness.py` — metric correctness, offline audit, PENDING-on-Android,
and the mislabel guard.
```bash
python ml/benchmarks/tests/test_harness.py
```

## Sample data
`sample_data/gold_dev.tsv` — gold-style Hindi↔Santali pairs for quality eval
(inputs only; not metrics).

## Known limitations
- No model is wired in this environment, so BLEU/WER/MOS/latency are PENDING.
- chrF/WER/CER are real computations but need a hypothesis from a real model.
- Battery/CPU/RAM require on-device instrumentation not present on the dev box.
