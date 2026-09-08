# PLAN — P6: Latency + 2 GB Budget Benchmark

**Goal:** Prove E2E pipeline (ASR → MT → TTS) runs <3 s total on a 2 GB-RAM device within
the ~500 MB model budget, sequential execution only.

**Context:**
- AGENTS.md latency budget: ASR ≤1 s, MT ≤0.5 s, TTS ≤1 s, total <3 s. RAM 2 GB, storage ~500 MB.
- Sequential execution is mandatory (RAM limit) — never parallelize the three models.
- `LatencyTracker` already logs per-leg timings; we need device numbers + memory caps.

**Tasks:**
1. Instrument E2E timing across ASR→MT→TTS using `LatencyTracker`; surface in Diagnostics UI.
2. Enforce sequential execution (one model resident at a time); free native handles between legs.
3. Measure on-device: per-leg ms, peak RSS, APK+pack size vs budget.
4. If over budget: quantize further / prune / offload least-used model to pack (P5).
5. Benchmark script under `benchmarks/` capturing repeat runs.

**Verify:** total <3 s, peak RAM ≤2 GB, storage ≤500 MB on target device.

**Acceptance:** Diagnostics shows green <3 s with no OOM; reproducible benchmark report.
