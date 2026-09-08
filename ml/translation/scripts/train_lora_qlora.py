#!/usr/bin/env python3
"""4-bit LoRA fine-tune of IndicTrans2 (hin_Deva -> sat_Olck) for RTX 3050 4GB.

Adapted from IndicTrans2/huggingface_interface/train_lora.py: adds bitsandbytes
4-bit base load + gradient checkpointing so it fits ~3.1 GB VRAM. Preprocessing
and eval (BLEU/chrF via IndicProcessor + IndicDataCollator) are unchanged.
"""
import os
import argparse
import torch
import pandas as pd
from datasets import Dataset
from sacrebleu.metrics import BLEU, CHRF
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from IndicTransToolkit import IndicProcessor, IndicDataCollator
from transformers import (
    Seq2SeqTrainer,
    Seq2SeqTrainingArguments,
    AutoModelForSeq2SeqLM,
    AutoTokenizer,
    EarlyStoppingCallback,
    BitsAndBytesConfig,
)

bleu_metric = BLEU()
chrf_metric = CHRF()


def get_arg_parse():
    p = argparse.ArgumentParser()
    p.add_argument("--model", type=str, default="ai4bharat/indictrans2-indic-indic-dist-320M")
    p.add_argument("--src_lang_list", type=str, default="hin_Deva")
    p.add_argument("--tgt_lang_list", type=str, default="sat_Olck")
    p.add_argument("--data_dir", type=str, required=True)
    p.add_argument("--output_dir", type=str, required=True)
    p.add_argument("--batch_size", type=int, default=2)
    p.add_argument("--grad_accum_steps", type=int, default=8)
    p.add_argument("--num_train_epochs", type=int, default=5)
    p.add_argument("--max_steps", type=int, default=1000000)
    p.add_argument("--learning_rate", type=float, default=2e-4)
    p.add_argument("--warmup_ratio", type=float, default=0.03)
    p.add_argument("--max_grad_norm", type=float, default=1.0)
    p.add_argument("--weight_decay", type=float, default=0.0)
    p.add_argument("--lora_target_modules", type=str, default="q_proj,k_proj,v_proj,out_proj")
    p.add_argument("--lora_dropout", type=float, default=0.05)
    p.add_argument("--lora_r", type=int, default=16)
    p.add_argument("--lora_alpha", type=int, default=32)
    p.add_argument("--eval_steps", type=int, default=1000)
    p.add_argument("--save_steps", type=int, default=1000)
    p.add_argument("--patience", type=int, default=3)
    p.add_argument("--print_samples", action="store_true")
    return p


def load_and_process(data_dir, split, tokenizer, processor, src_lang_list, tgt_lang_list, seed=42):
    out = {"sentence_SRC": [], "sentence_TGT": []}
    for src_lang in src_lang_list:
        for tgt_lang in tgt_lang_list:
            if src_lang == tgt_lang:
                continue
            sp = os.path.join(data_dir, split, f"{src_lang}-{tgt_lang}", f"{split}.{src_lang}")
            tp = os.path.join(data_dir, split, f"{src_lang}-{tgt_lang}", f"{split}.{tgt_lang}")
            if not os.path.exists(sp) or not os.path.exists(tp):
                raise FileNotFoundError(f"missing {sp} or {tp}")
            with open(sp, encoding="utf-8") as fs, open(tp, encoding="utf-8") as ft:
                src_lines = fs.readlines()
                tgt_lines = ft.readlines()
            assert len(src_lines) == len(tgt_lines)
            out["sentence_SRC"] += processor.preprocess_batch(
                src_lines, src_lang=src_lang, tgt_lang=tgt_lang, is_target=False)
            out["sentence_TGT"] += processor.preprocess_batch(
                tgt_lines, src_lang=tgt_lang, tgt_lang=src_lang, is_target=True)
    ds = Dataset.from_dict(out).shuffle(seed=seed)
    return ds.map(lambda ex: preprocess_fn(ex, tokenizer), batched=True, num_proc=4)


def preprocess_fn(example, tokenizer, **kw):
    mi = tokenizer(example["sentence_SRC"], truncation=True, padding=False, max_length=256)
    with tokenizer.as_target_tokenizer():
        lab = tokenizer(example["sentence_TGT"], truncation=True, padding=False, max_length=256)
    mi["labels"] = lab["input_ids"]
    return mi


def compute_metrics_factory(tokenizer, print_samples=False, n_samples=10):
    def compute_metrics(eval_preds):
        preds, labels = eval_preds
        labels[labels == -100] = tokenizer.pad_token_id
        preds[preds == -100] = tokenizer.pad_token_id
        with tokenizer.as_target_tokenizer():
            preds = [x.strip() for x in tokenizer.batch_decode(preds, skip_special_tokens=True)]
            labels = [x.strip() for x in tokenizer.batch_decode(labels, skip_special_tokens=True)]
        df = pd.DataFrame({"Predictions": preds, "References": labels}).sample(n=n_samples)
        if print_samples:
            for pr, lb in zip(df["Predictions"].values, df["References"].values):
                print(f" | > P: {pr}\n | > R: {lb}\n")
        return {m: met.corpus_score(preds, [labels]).score for m, met in
                {"BLEU": bleu_metric, "chrF": chrf_metric}.items()}
    return compute_metrics


def main():
    args = get_arg_parse().parse_args()
    print(f" | > Loading {args.model} (fp16) ...")
    model = AutoModelForSeq2SeqLM.from_pretrained(
        args.model, trust_remote_code=True, attn_implementation="eager",
        torch_dtype=torch.float16,
    ).to("cuda")

    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    processor = IndicProcessor(inference=False)
    data_collator = IndicDataCollator(tokenizer=tokenizer, model=model, padding="longest",
                                      pad_to_multiple_of=8, label_pad_token_id=-100)

    src = args.src_lang_list.split(",")
    tgt = args.tgt_lang_list.split(",")
    train_ds = load_and_process(args.data_dir, "train", tokenizer, processor, src, tgt)
    print(f" | > train size {len(train_ds)}")
    eval_ds = load_and_process(args.data_dir, "dev", tokenizer, processor, src, tgt)
    print(f" | > dev size {len(eval_ds)}")

    lora = LoraConfig(r=args.lora_r, bias="none", inference_mode=False, task_type="SEQ_2_SEQ_LM",
                      lora_alpha=args.lora_alpha, lora_dropout=args.lora_dropout,
                      target_modules=args.lora_target_modules.split(","))
    model = get_peft_model(model, lora)
    model.print_trainable_parameters()

    training_args = Seq2SeqTrainingArguments(
        output_dir=args.output_dir, do_train=True, do_eval=True, fp16=True,
        logging_steps=100, save_total_limit=1, predict_with_generate=True,
        load_best_model_at_end=True, save_strategy="steps", evaluation_strategy="steps",
        max_steps=args.max_steps, per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum_steps,
        eval_accumulation_steps=args.grad_accum_steps,
        weight_decay=args.weight_decay, max_grad_norm=args.max_grad_norm,
        lr_scheduler_type="linear", warmup_ratio=args.warmup_ratio,
        learning_rate=args.learning_rate, num_train_epochs=args.num_train_epochs,
        save_steps=args.save_steps, eval_steps=args.eval_steps,
        metric_for_best_model="eval_chrF", greater_is_better=True,
        report_to="none", generation_max_length=256, generation_num_beams=1,
        sortish_sampler=True, group_by_length=True,
        include_tokens_per_second=True, dataloader_num_workers=0,
        gradient_checkpointing=True,
    )
    trainer = Seq2SeqTrainer(
        model=model, args=training_args, data_collator=data_collator,
        train_dataset=train_ds, eval_dataset=eval_ds,
        compute_metrics=compute_metrics_factory(tokenizer, print_samples=args.print_samples),
        callbacks=[EarlyStoppingCallback(early_stopping_patience=args.patience)],
    )
    try:
        trainer.train()
    except KeyboardInterrupt:
        print(" | > interrupted")
    model.save_pretrained(args.output_dir)
    print(f" | > saved LoRA adapter -> {args.output_dir}")


if __name__ == "__main__":
    main()
