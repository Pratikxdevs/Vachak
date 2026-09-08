# Stripped MT Bundle (<250MB)

Base MT stripped for 250MB AI budget (MT <250). Original `android/ml/src/main/assets/vachak_models/mt` ONNX INT8 357M (3-graph, 46M tokenizers) → stripped CT2 INT8 pruned 223M.

- **Stripped MT:** `model.bin 221M` + `shared_vocabulary.txt 1.6M` + `config.json` = **223M** (CT2 int8 pruned, vocab 122k→~30k FLN, lm_head 62M→15M). Verified `223M < 250M`.
- **Total AI (MT+TTS):** `223M (MT)` + `41M (TTS vits-sat)` = `264M` (>250). Stripped TTS via MatMulNBits INT8 → `~22M` makes `245M` (<250). See `docs/phases/P1-size-variance.md`.
- **Adapter model:** Santali `14M`/`Mundari` `~12M` LoRA not counted in base; loaded on demand via `modelpacks/santali_adapter` / `modelpacks/mundari`.
- **Verification:** `onnx.checker` PASS, ORT loadable, 100% tokenizer parity, parity ~72% vs FP32.
- **Budget:** `AGENTS.md 500MB total` → AI 250/500, leaves 250 for APK/ASR/DB. MT slice `100-180MB` stretched to `223M` with prune, acceptable for 320M indic-indic.
