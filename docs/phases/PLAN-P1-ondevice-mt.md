# PLAN — P1: On-device Machine Translation (IndicTrans2)

**Goal:** Replace `MockTranslationEngine` with a real IndicTrans2 distilled+quantized ONNX
running in `:ml` via ONNX Runtime Mobile. Hin → Mundari (Ol Chiki).

**Context:**
- Verified Python baseline exists: `ml/translation/mundari/it2_ct2_baseline.py`
  (`नमस्ते। → ନମସ୍କାରଂ।`), CTranslate2 int8. That proves the *model*, not the APK path.
- AGENTS.md: MT budget 100–180 MB; runtime = ONNX Runtime Mobile; sequential pipeline
  (ASR → MT → TTS); never train on IN22 test split.
- License: IndicTrans2 (AI4Bharat) — record in THIRD_PARTY_NOTICES before merge.

**Tasks:**
1. Export IndicTrans2 distilled 320M → ONNX (int8 quant) for `arm64-v8a`, ≤180 MB.
2. Place model in `assets/vachak_models/mt/` (or language pack, P5).
3. Add `com.vachak.ml.adapter.IndicTrans2Adapter` implementing the app `TranslationEngine`
   interface (Hindi tokenize → infer → Ol Chiki decode).
4. Point `EngineProvider.real(context)` at it (drop `MockTranslationEngine`).
5. Unit test: fixture Hindi sentence → non-empty Mundari string; assert **zero** network calls.

**Verify:** offline `assembleDebug` green; APK MT slice within budget; Translate yields real
Mundari text; on-device latency ≤0.5 s.

**Acceptance:** Tapping "Translate lesson" shows precomputed Mundari output from the real model,
not `[DEV-FIXTURE-mund]`.
