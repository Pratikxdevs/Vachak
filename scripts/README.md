# scripts — Demo & Packaging (PHASE 12)

## demo_flow.py
Narrated 90–120s judge flow (PHASE 12). Runs the orchestrator with DEV-FIXTURE
mocks so the *flow* is demonstrable without models. Offline audit is real.
```bash
python scripts/demo_flow.py            # narrated, runs pipeline once
python scripts/demo_flow.py --script   # print the timed script only
```
All timings are labeled DEV FIXTURE and are NOT Android latency. For the live
run, swap mocks for sherpa-onnx / IndicTrans2 via the EngineProvider on the 2GB
tablet with WiFi OFF.

## pack.py
Builds an offline content pack (zip + manifest) for side-load install — never a
network download.
```bash
python scripts/pack.py --lessons scripts/sample_data --packId fln_hi_mund_v1
```
Output: `packages/<packId>.zip` (with `manifest.json`).

## Sample data
`scripts/sample_data/lesson_sample.json` — one precomputed FLN lesson (Hi↔Mundari).

## See also
- `docs/DEMO.md` — full demo script + acceptance criteria.
- `docs/benchmarks/BENCHMARK_REPORT.md` — report template.
- `shared/orchestrator` — pipeline driven by the demo.
- `ml/benchmarks` — performance measurement (PENDING on hardware).

## Known limitations
- Mocks produce clearly-fake `[DEV-FIXTURE …]` output.
- Real on-device latency/battery remain PENDING until measured.
