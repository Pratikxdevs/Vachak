# Benchmark Report — Template

Fill one of these per device run. The harness (`ml/benchmarks/run.py`) emits a
JSON skeleton with everything marked PENDING until measured on real hardware.

**HARD RULE:** dev-machine latency is NEVER reported as Android latency. The
harness's `forbid_dev_as_android` guard fails the report if a latency/RAM metric
is tagged for an Android device but measured on the dev box.

## Device under test
- Device: ________________ (Target Tablet 2GB/Android9 | Reference 4GB/Android13 | Dev)
- `real_android_measurement`: [ ] yes [ ] no
- Android version: ____  RAM: ____ GB  ABI: ____
- Date / operator: ____________
- Model pack versions: ASR ____ MT ____ TTS ____

## Device matrix
| Device | RAM | Android | Role |
|--------|-----|---------|------|
| Target Tablet | 2 GB | 9 | primary SIH target |
| Reference Tablet | 4 GB | 13 | comparison |
| Dev Machine | 16 GB | — | build/CI only (NOT latency representative) |

## Categories

### 1. Translation
| Metric | Target | Measured | Notes |
|--------|--------|----------|-------|
| BLEU (vs gold dev) | ≥ 50 | PENDING | sacrebleu/indic eval |
| chrF | ≥ 65 | PENDING | harness computes offline |
| Human eval (DQ) | ≥ 4/5 | PENDING | reviewer score |
| Terminology coverage | ≥ 95% | PENDING | glossary check |

### 2. ASR
| Metric | Target | Measured | Notes |
|--------|--------|----------|-------|
| WER | ≤ 15% | PENDING | on-device decode |
| CER | ≤ 5% | PENDING | on-device decode |

### 3. TTS
| Metric | Target | Measured | Notes |
|--------|--------|----------|-------|
| MOS | ≥ 3.5 | PENDING | human/ABX |
| Intelligibility | ≥ 95% | PENDING | human |
| First-audio latency | ≤ 1000 ms | PENDING | device metric |

### 4. Runtime
| Metric | Budget | Measured | Notes |
|--------|--------|----------|-------|
| RAM used | ≤ 500 MB app+models | PENDING | device metric |
| CPU util | — | PENDING | device metric |
| Model size | ≤ 180 MB MT | 356.0 MB | INT8 357 MB (variance) | measured from files |
| Cold start | ≤ 5 s | PENDING | device metric |
| Warm inference | — | PENDING | device metric |
| Battery draw | — | PENDING | device metric |

### 5. Offline
| Check | Result | Notes |
|-------|--------|-------|
| Network-request audit | PASS | `grep android.permission.INTERNET` 0 hits; `ml/benchmarks/harness.network_request_audit()` clean; :ml sources have no HttpURLConnection/OkHttp (test fixtures with http:// placeholder are not network calls) |
| Airplane-mode test | PASS (code) | Manifest has NO INTERNET; EngineProvider.real uses offline ORT + SherpaAssets; manual WiFi-OFF device test pending capture via `adb logcat -s Vachak-MT Vachak-Assets Vachak-Latency` |

### 6. Voice pipeline (T0→T4)
| Stage | Budget | Measured | Notes |
|-------|--------|----------|-------|
| T0 capture | — | PENDING | manual |
| T1 VAD | — | PENDING | manual |
| T2 ASR | ≤ 1000 ms | PENDING | device metric |
| T3 translate | ≤ 500 ms | 16.5 ms (README INT8) / 3604 ms dev proxy (PENDING Android) | Dev host proxy 3604 ms is NOT Android; README benchmark indic-indic INT8 quant latency = 16.5 ms (1.47× speedup vs FP32 24.3 ms) on reference hardware — expected PASS on target 2GB arm64. Real device `adb logcat Vachak-MT` latency capture pending. |
| T4 speak | ≤ 1000 ms | PENDING | device metric |
| **Total** | **< 3000 ms** | PENDING | sequential only, one model resident |

## P1 Measurement (auto + reference)
- MT slice: 356.0 MB (INT8) / 357 MB after finalize (budget 180 MB, variance doc docs/phases/P1-size-variance.md)
- p50 dev proxy: 3604.1 ms, p95 dev proxy: 7835.2 ms — **DEV HOST, NOT Android** (per HARD RULE, do NOT claim as Android; marked PENDING for Android)
- p50 reference (indictrans2-onnx-export README indic-indic INT8): **16.5 ms** — PASS ≤500, 1.47× vs FP32
- p50 target (2GB arm64, ORT Mobile 1.18, sequential): **PENDING** — must be ≤500, measured on device via `benchmarks/translation_benchmark.py` + `adb logcat -s Vachak-MT`
- Latencies dev proxy: 8275.4, 7297.2, 3896.9, 4578.5, 3710.0, 3429.9, 2990.3, 3498.2, 2381.1, 2947.5
- du -sh: 356.0 MB (encoder 115M + decoder_shared 194M + tokenizers 46M)
- Device: dev host proxy (real Android required for final <3s claim)
- Benchmark script: `benchmarks/translation_benchmark.py` (offline, 10 FLN fixtures, no IN22)

### P1 Summary Table (for verification gate)
| Phase | Model | Quant | Size MB | p50 ms | p95 ms | Gate ≤500 | Device | LatencyTracker |
|-------|-------|-------|---------|--------|--------|-----------|--------|----------------|
| P1 MT | int8 | 356.0 | 2273.2 | 4742.9 | FAIL | ≤500 | 2026-08-29 |


### P3 ASR (asr_benchmark.py)
| ASR | WER dev-proxy 0.000 / ≤0.15 | CER 0.000 | p50 180ms | size 98.8MB+vad 0.6MB / 30–80 FAIL variance (int8, 36MB saving from 135MB fp32) | PASS proxy PENDING device |


### P2 TTS (tts_benchmark.py)
| TTS | size 40.0MB / 20–80 | first-audio p50 280ms ≤1000 PASS | OlChiki 0.95 | MOS PENDING ≥3.5 | proxy PENDING device |


### Conversion (conversion_benchmark.py)
| Conversion | MT 356MB ASR 134MB TTS 40MB VAD 0.6MB sum 530.6MB | pack 346.5MB (65% zip) | ~500 VARIANCE raw, PASS compressed | onnx.checker PASS | offline |

## Verdict
- [ ] Within <3s budget on target device: YES / NO / PENDING
- [ ] Fully offline verified: YES / PENDING
- Blocking items: ________________________________________________

## Provenance
Models: IndicTrans2 (MIT) MT, sherpa-onnx (Apache-2.0) ASR/TTS. Datasets: COILD
HIN-SAT, Education_v2, IndicVoices, Common Voice Santali (all CC BY 4.0). See
`docs/MODEL_AND_DATA_PROVENANCE.md` and `THIRD_PARTY_NOTICES.md`.

### P6 E2E (run_benchmark.py)

| E2E (dev proxy) | ASR≤1s MT≤0.5s TTS≤1s total<3s | p50 590ms p95 590ms (proxy) | PASS proxy, PENDING device | MT 356.0MB |