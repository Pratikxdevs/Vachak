# docs/benchmarks — Benchmark Stubs

The `BenchmarkRunner` contract (`shared/schemas/engines.py`) measures the
sequential ASR→MT→TTS pipeline against the <3s budget and reports per-stage
latency + memory. This directory holds DEV FIXTURE benchmark scaffolding only.

Real benchmarks must run on the target 2GB Android 9 device. Never benchmark
with network. See `docs/architecture/ARCHITECTURE.md` for the latency budget.

> No fabricated metrics are recorded here. Replace stubs with on-device runs.
