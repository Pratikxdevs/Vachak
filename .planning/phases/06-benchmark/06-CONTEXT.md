# Phase 6: Budget & Latency Proof — Context

**Gathered:** 2026-08-29
**Status:** Ready for planning
**Source:** AGENTS.md latency budget ASR≤1s MT≤0.5s TTS≤1s total<3s, model budget ~500MB, 2GB RAM

<domain>
## Phase Boundary

Prove sequential pipeline on 2GB device within budgets; enforce one model resident at a time; reproducible benchmark harness under benchmarks/. Does not include demo run (P7).
</domain>

<decisions>
- Sequential only (RAM limit), numThreads=1, free native handles between legs
- LatencyTracker is source of truth, surfaced in Diagnostics UI
- benchmarks/ scripts capture repeat runs (10 fixtures, p50/p95)

</decisions>

<canonical_refs>
- AGENTS.md Budget table, Latency budget
- benchmarks/ + docs/benchmarks/BENCHMARK_REPORT.md
- android/ml/LatencyTracker
</canonical_refs>
