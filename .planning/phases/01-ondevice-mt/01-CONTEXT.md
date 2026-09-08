# Phase 1: On-device MT — Context

**Gathered:** 2026-08-29
**Status:** Ready for planning
**Source:** docs/phases/PLAN-P1-ondevice-mt.md + AGENTS.md + verified Python baseline

<domain>
## Phase Boundary

Replace MockTranslationEngine with a real IndicTrans2 distilled+quantized ONNX running in :ml via ONNX Runtime Mobile. Hin→Mundari (Ol Chiki). Python CTranslate2 int8 baseline (नमस्ते→ନମସ୍କାରଂ) proves the model; P1 proves the APK path. Covers export, quantization, :ml adapter, EngineProvider wiring, offline verification. Does NOT include Mundari voice (P2), curriculum (P4), or pack system (P5).
</domain>

<decisions>
## Implementation Decisions

### MT Runtime
- ONNX Runtime Mobile in :ml module; sequential pipeline (ASR→MT→TTS) — never parallel (RAM limit)
- No runtime network calls — strictly offline after install

### Model Choice
- IndicTrans2 distilled 320M as base; fine-tune is out-of-scope for P1 if distilled already handles Hin→sat; otherwise single LoRA on COILD+Education_v2 (never IN22 test)
- Quantize to int8 ONNX; budget 100–180MB; arm64-v8a first; verify 500MB total stays green

### Adapter Contract
- New com.vachak.ml.adapter.IndicTrans2Adapter implements app TranslationEngine (tokenize Hin → infer → decode Ol Chiki)
- EngineProvider.real(context) points at real adapter; drop MockTranslationEngine

### Assets
- Model lives in assets/vachak_models/mt/ for P1 (migrates to language-pack path in P5)
- Tokenizer + sentencepiece assets bundled; no download

### Claude's Discretion
- Exact quantization tool (onnxruntime quant vs optimum) and calibration set
- Tokenizer handling (IndicTrans2 SP model integration details)
- Fallback if int8 degrades Ol Chiki quality below threshold
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### MT & Pipeline
- `AGENTS.md` — hard rules: offline, sequential, no IN22 training, never ship MACHINE_TRANSLATED as approved pedagogy
- `docs/phases/PLAN-P1-ondevice-mt.md` — authoritative P1 scope (this phase)
- `ml/translation/mundari/it2_ct2_baseline.py` — verified Python baseline (CTranslate2 int8)
- `indictrans2-onnx-export/` — prior ONNX export attempts (reference, do not assume working)
- `ml/translation/README.md` — translation pipeline notes

### App Integration
- `android/ml/src/main/java/com/vachak/ml/` — existing adapters (SherpaAsrAdapter, SherpaOnnxTtsAdapter, SherpaAssets)
- `android/app/src/main/java/com/vachak/engine/EngineProvider.kt` — wiring point
- `android/app/src/main/java/com/vachak/engine/MockTranslationEngine.kt` — fixture to replace
- `THIRD_PARTY_NOTICES.md` — license tracking pattern

### Budgets & Provenance
- `docs/MODEL_AND_DATA_PROVENANCE.md` — where to record model/dataset licenses
- `benchmarks/` + `docs/benchmarks/BENCHMARK_REPORT.md` — latency/budget verification style
</canonical_refs>

<specifics>
## Specific Ideas

- Verified: Python baseline hits real Mundari; reuse its sentencepiece + tokenizer assets for ONNX path
- Quantized distilled 320M should fit 100–180MB; if oversize, consider int8 dynamic vs static quantization tradeoff
- Acceptance: "Translate lesson" must show real Mundari text, not [DEV-FIXTURE-mund]
</specifics>

<deferred>
## Deferred Ideas

- Fine-tuned Mundari voice (P2) — not in P1
- Pack-based model distribution (P5) — P1 bundles directly in APK assets
- IN22 eval harness — benchmarking only, separate from P1 delivery
</deferred>

---
*Phase: 01-ondevice-mt*
*Context gathered: 2026-08-29 via docs/phases synthesis*
