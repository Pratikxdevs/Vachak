# Vice-Versa — Santali→Hindi Finetune

**Goal:** Make `sat_Olck → hin_Deva` (Ol Chiki → pure Hindi) as good as current `hin_Deva → sat_Olck` (~90% classroom accuracy, no loops). Current build at `android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt:52` only supports `hi→sat`; reverse returns `UNSUPPORTED_LANGUAGE` and base `dist-320M` hallucinates on `sat→hi` (`process.md:42`).

**Result of this doc:** One `sat→hi` LoRA (14M) or one bidirectional LoRA (14M) that gives pure Hindi, eval BLEU `~55-65` vs current `~21`, shipped via same ONNX `1.24.3` pipeline.

---

## 1. Why it will work

- `ai4bharat/indictrans2-indic-indic-dist-320M` is encoder-decoder with lang tags `__hin_Deva__`, `__sat_Olck__` (see `android/app/src/main/assets/vachak_models/mt/config.json:37` `max_source_positions 256`). It already handles `hin→sat` after our `653`-pair finetune (`ml/finetune/it2_goldnum_lora` `BLEU 21.4→64.7`).
- Reversing the pairs reuses **same gold** Santali Ol Chiki `U+1C50–U+1C7F` + pure Hindi Devanagari — no new collection. The model just needs to see `sat_Olck hin_Deva` examples with correct tags.
- Our robust path is `ml/translation/scripts/finetune_simple.py` (custom fp16 AMP LoRA `q/k/v/out`) — `train_lora_qlora.py` is broken (`pad_without_fast_tokenizer_warning NameError`, beam deadlock) per `process.md:95`.

---

## 2. Data — reverse what you have

```bash
# forward 653 = goldverified 408 + numbers 225 + boosters 20
# files at ml/finetune/data/it2_goldnum_train.tsv, it2_goldverified_dev.tsv:45, numbers_hi_sat.tsv:225

# 1) Reverse goldverified + numbers
awk -F'\t' '{print $2"\t"$1}' ml/finetune/data/it2_goldnum_train.tsv > ml/finetune/data/it2_sat_hi_train.tsv
awk -F'\t' '{print $2"\t"$1}' ml/finetune/data/it2_goldverified_dev.tsv > ml/finetune/data/it2_sat_hi_dev.tsv
awk -F'\t' '{print $2"\t"$1}' ml/finetune/data/numbers_hi_sat.tsv > ml/finetune/data/numbers_sat_hi.tsv
awk -F'\t' '{print $2"\t"$1}' ml/finetune/data/numbers_boost.tsv > ml/finetune/data/numbers_boost_sat_hi.tsv 2>/dev/null || true
awk -F'\t' '{print $2"\t"$1}' ml/finetune/data/numbers_boost2.tsv > ml/finetune/data/numbers_boost2_sat_hi.tsv 2>/dev/null || true

# 2) Combine (same 653 count, now sat→hi)
cat ml/finetune/data/it2_sat_hi_train.tsv > ml/finetune/data/sat_hi_all_train.tsv
# optional: keep numbers boosters if they matter reverse (59↔69 etc. still relevant)
cat ml/finetune/data/numbers_sat_hi.tsv >> ml/finetune/data/sat_hi_all_train.tsv

wc -l ml/finetune/data/sat_hi_all_train.tsv ml/finetune/data/it2_sat_hi_dev.tsv
head ml/finetune/data/sat_hi_all_train.tsv
# should be: ᱡᱚᱦᱟᱨ<TAB>नमस्ते  (Ol Chiki → Hindi)

# 3) Never train on IN22
# datasets/hin_sat/corpus.gold_verified.tsv is reserved; IN22-Gen/Conv is eval-only (see .planning/REQUIREMENTS.md)
```

*If you want one bidirectional adapter instead, just concat both directions:*

```bash
cat ml/finetune/data/it2_goldnum_train.tsv ml/finetune/data/it2_sat_hi_train.tsv > ml/finetune/data/bidir_train.tsv
cat ml/finetune/data/it2_goldverified_dev.tsv ml/finetune/data/it2_sat_hi_dev.tsv > ml/finetune/data/bidir_dev.tsv
# 1306 pairs total
```

Sanity: `grep -P "[\x{1C50}-\x{1C7F}]" ml/finetune/data/sat_hi_all_train.tsv | head` must show Ol Chiki in col1, `grep -P "[\x{0900}-\x{097F}]" ... | head` Hindi in col2.

---

## 3. Train

### Option A — Separate reverse LoRA (recommended, keeps forward 64.7 BLEU intact)

```bash
python ml/translation/scripts/finetune_simple.py \
  --tsv ml/finetune/data/sat_hi_all_train.tsv \
  --dev_tsv ml/finetune/data/it2_sat_hi_dev.tsv \
  --out ml/finetune/it2_sat_hi_lora \
  --epochs 8 --batch-size 8 --lr 1e-4

# expect: train_loss 2.5→0.6 dev 2.5→1.4, same as forward goldverified run
ls -lh ml/finetune/it2_sat_hi_lora/ # adapter_model.safetensors ~14M + adapter_config.json
```

### Option B — One bidirectional LoRA (14M total, slight dilution)

```bash
python ml/translation/scripts/finetune_simple.py \
  --tsv ml/finetune/data/bidir_train.tsv \
  --dev_tsv ml/finetune/data/bidir_dev.tsv \
  --out ml/finetune/it2_bidir_lora \
  --epochs 8
```

`finetune_simple.py:146` does: `AutoModelForSeq2SeqLM + PeftModel LoRA r=8 alpha=16 on q/k/v/out`, `fp16 AMP`, manual `IndicTransTokenizer` tokenization (avoids broken `IndicTransToolkit/collator.py`).

---

## 4. Eval (same scripts as forward)

```bash
# BLEU / chrF vs gold dev (45 pairs reversed)
python ml/translation/scripts/eval_gv.py \
  --tsv ml/finetune/data/it2_sat_hi_dev.tsv \
  --adapter_dir ml/finetune/it2_sat_hi_lora

# numeral accuracy if you reversed numbers
python ml/translation/scripts/num_eval.py \
  --tsv ml/finetune/data/it2_num_eval.tsv  # reverse it first if needed

# quick infer
echo -e "ᱡᱚᱦᱟᱨ\nᱥᱟᱹᱜᱩᱱ ᱥᱮᱛᱟᱜ" > /tmp/sat.txt
python ml/translation/scripts/infer_it2.py --adapter_dir ml/finetune/it2_sat_hi_lora --inp /tmp/sat.txt --out /tmp/hi.txt
cat /tmp/hi.txt
# expect: नमस्ते / सुप्रभात (not Ol Chiki)
```

Target: `sat→hi` `BLEU 21→~55-60 chrF 47→~67` like forward `56.1/67.0 → 64.7/70.5`, `0/45` loops, numerals `32/36 89%` if you include numbers.

---

## 5. Merge + ONNX export (ship to Android)

Forward adapters are **not yet in APK** (`android/app/src/main/assets/vachak_models/mt` is base 320M INT8 357M). After reverse training, merge before export:

```bash
# merge LoRA into base
python -c "
from transformers import AutoModelForSeq2SeqLM
from peft import PeftModel
base = AutoModelForSeq2SeqLM.from_pretrained('ai4bharat/indictrans2-indic-indic-dist-320M', trust_remote_code=True)
peft = PeftModel.from_pretrained(base, 'ml/finetune/it2_sat_hi_lora')
peft = peft.merge_and_unload()
peft.save_pretrained('ml/models/it2_sat_hi_merged')
"

# export 3-graph opset17 (same as P1: BATCH1 ENC 64 DEC 1, externalize >100M)
python ml/translation/scripts/export_onnx_it2.py \
  --model_dir ml/models/it2_sat_hi_merged \
  --out_dir ml/models/it2_sat_hi_onnx

python -m onnx.checker ml/models/it2_sat_hi_onnx/encoder_model.onnx
# quantize per_channel INT8 → ~357M (see ml/translation/scripts/quantize.py)

# copy to Android assets (or pack)
cp -r ml/models/it2_sat_hi_onnx/* android/app/src/main/assets/vachak_models/mt_sat_hi/
# or add to .planning pack builder packages/build_pack.py manifest
```

*For Option A (2 adapters):* keep both `it2_goldnum_lora` (forward) + `it2_sat_hi_lora` (reverse) → `EngineProvider.kt:48` holds 2 `TranslationEngine`s, `LiveScreen` picks by `LanguagePair`. Total `28M` (APK `540M → 568M`, still ~500M pack via zip).

*For Option B (1 bidir):* single `it2_bidir_lora` merged → one `mt` bundle `357M` (no size increase).

In both cases keep `ORT Mobile 1.24.3` (`android/ml/build.gradle.kts:37` — was 1.20.0 vs sherpa `VERS_1.24.3` mismatch that crashed `Vad`). Sherpa `1.13.0.aar` already at `1.24.3`.

---

## 6. Code wiring (one-line gate)

```kotlin
// android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt:52
srcOk = src in setOf("hi","hin","hindi","hin_deva","sat","sat_olck") // add sat as source
tgtOk = tgt in setOf("sat","sat_olck","hi","hin","hindi") // add hi as target
// and handle sat_Olck -> hin_Deva in curatedMap reverse or remove curated short-circuit for reverse
```

```kotlin
// android/app/src/main/java/com/vachak/engine/EngineProvider.kt:48
translationForward = IndicTrans2Adapter(context, modelDir="mt") // hi→sat
translationReverse = IndicTrans2Adapter(context, modelDir="mt_sat_hi") // sat→hi
// or single bidir adapter if Option B
```

Add `LanguagePair("sat_Olck","hin_Deva")` handling in `LiveScreen.kt` / `ToolsScreen` as needed. Keep `ReentrantLock` sequential, `max_source_positions 256` truncation already at `IndicTrans2Adapter.kt:116` (fixes Gather OOB `idx=258`).

---

## 7. Verify on device

```bash
JAVA_HOME=/tmp/jdk17 ./gradlew -p android assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Vachak-MT:V Vachak-ASR:V Vachak-VAD:V
# Type Ol Chiki: ᱡᱚᱦᱟᱨ → should show Hindi नमस्ते (not Ol Chiki)
# Try long: 30× "मेरा नाम..." reverse equivalent, ensure truncate 273→255 no Gather crash
```

Check `docs/benchmarks/BENCHMARK_REPORT.md` — add `sat→hi` row.

---

## 8. When to do which

- **Now:** Option A separate reverse LoRA — safest, keeps forward `64.7 BLEU` untouched.
- **Later:** If APK size matters, distill to Option B bidir `1306 pairs` and re-eval; expect `~2-3 BLEU` dilution but `14M` vs `28M`.
- **Never:** `sat→hi` with base `dist-320M` alone — it hallucinates (wrong language) as seen in `process.md:42`.

---

## Files

- `ml/translation/scripts/finetune_simple.py:146` — use this (not `train_lora_qlora.py`)
- `ml/translation/scripts/eval_gv.py`, `num_eval.py`, `infer_it2.py` (greedy `num_beams=1 + repetition_penalty 1.2 + no_repeat 3`)
- `ml/translation/scripts/export_onnx_it2.py:157` — 3-graph, `config.json:37` `max_source_positions 256` (weight `[258,512]`)
- `android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt:52,116` — gate + truncation
- `android/app/src/main/assets/vachak_models/mt/{config,tokenizer_*,encoder,decoder*}`
- `ml/finetune/data/{it2_goldnum_train.tsv:653, it2_goldverified_dev.tsv:45, numbers_hi_sat.tsv:225}`
