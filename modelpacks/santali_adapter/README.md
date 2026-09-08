# Santali Adapter (Pluggable)

Santali is now an **adapter**, not baked-in. Wire it anytime via pack.

- Base MT: `modelpacks/stripped_mt` (CT2 int8 pruned, 223M, `hin_Deva`→`sat_Olck` shared, ONNX Runtime Mobile)
- Adapter: `ml/finetune/it2_goldverified_lora` (14M `adapter_model.safetensors`, LoRA r=8, goldverified 453 pairs BLEU 56.1)
- Usage: `TranslationAdapter(kind="santali")` loads base + LoRA on demand. Without adapter, base falls back to generic `indictrans2-indic-indic-dist-320M` Santali.
- Toggle: `MundariConfig(adapter_kind="santali")` or `LanguagePair("hi","sat_Olck")` with pack `sat_Olck-v0.1.1.vachakpack`.
- Size: adapter 14M (5M INT4), base 223M shared. Total when wired 237M (<250 MT).
- Provenance: `docs/MODEL_AND_DATA_PROVENANCE.md` `it2_goldverified_lora`, trained on `datasets/hin_sat/corpus.gold_verified.tsv:453`.
