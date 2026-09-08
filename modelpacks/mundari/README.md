# Mundari Primary Pack (mun_Deva)

Primary language pack for Jharkhand - Mundari (Devanagari). Wired as primary via `modelpacks/stripped_mt` base.

- Base: `modelpacks/stripped_mt/model.bin 221M` (CT2 int8 pruned, 223M total, <250)
- Mundari Adapter: `ml/finetune/it2_mundari_lora` (future, 12M LoRA, `hin_Deva->mun_Deva`, Karya 17k gated)
- Current: synthetic `datasets/hin_sat/classroom_10k.jsonl` retargeted to `hin->mun_Deva` via template swap (10k classroom, silver)
- Usage: `get_adapter("final")` when licensed, else `baseline` (Santali stand-in) or `mock`
- Size: base 223M + adapter 12M = 235M (<250 MT). TTS `vits-mun` 40M separate.
