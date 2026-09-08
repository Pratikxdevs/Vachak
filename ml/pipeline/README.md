# ml/pipeline — Speech Pipeline (Phase 5)

Streaming audio → VAD → ASR → TTS wiring with latency instrumentation (5A–5D).
All adapters are **swappable**; no final Hindi ASR / Mundari TTS model is bundled.

## Modules
| File | Phase | Purpose |
|------|-------|---------|
| `vad_stream.py` | 5A | Mic → 10–100ms chunks → VAD → segments. `SherpaOnnxVadStream` (reference) + `MockVadStream` (DEV FIXTURE). |
| `asr_adapter.py` | 5B | `AsrAdapter` interface + `MockHindiAsrAdapter` (fixture) + `IndicConformerAsrAdapter` (stub, ONNX Runtime Mobile reference). |
| `tts_adapter.py` | 5C | `TtsAdapter` + `SherpaOnnxTtsAdapter` (reference) + `EspeakFallbackTtsAdapter` (dev-only, labeled NOT final Mundari) + `MockTtsAdapter`. |
| `latency.py` | 5D | `LatencyTracker` (T0–T4) + `LatencyRecorder` (JSONL). |
| `pipeline.py` | — | Orchestrator: VAD→ASR→translate→TTS, injects a `translator` callable. |
| `sample_data/` | — | DEV FIXTURE strings (not approved pedagogy). |

## Run locally (DEV FIXTURE, no model/mic)
```bash
python3 -c "import sys; sys.path.insert(0,'ml'); from ml.pipeline import pipeline; pipeline.demo_local()"
```
Latency printed is **fixture-only** — not a device measurement.

## Tests
```bash
python3 -m unittest discover -s ml/pipeline/tests -p "test_*.py"
```

## Swap adapters (no caller change)
```python
from ml.pipeline.asr_adapter import IndicConformerAsrAdapter, MockHindiAsrAdapter
asr = MockHindiAsrAdapter()            # today
asr = IndicConformerAsrAdapter("hi.onnx")  # when model is trained
```

## Known limitations
- Final Hindi ASR, Mundari TTS, Mundari MT models do **not** exist yet (task constraint).
- `MockVadStream` is energy-gated, not a neural VAD.
- eSpeak NG fallback is **not** the final Mundari voice; labeled everywhere.
- Latency numbers are real measurements of whatever backend is wired, but fixtures are not representative. Measure on device.

## License / provenance
- sherpa-onnx: Apache-2.0 (referenced, not modified). See repo `/home/clutch/Desktop/Vachak/sherpa-onnx`.
- eSpeak NG fallback: GPL/LGPL — dev-only, handle deliberately.
- No LICENSE/NOTICE files were stripped or modified.
