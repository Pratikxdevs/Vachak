# INTERIM_VOICE.md — audible Santali voice (Hindi-VITS bridge)

## What this is

A REAL, audible Santali voice available TODAY while the native-speaker VITS
fine-tune trains: Piper `hi_IN-pratham-medium` (male Hindi VITS, 61 MB, 22050 Hz)
run through sherpa-onnx `OfflineTts` (Piper path), fed with Santali text
transliterated Ol Chiki -> Devanagari (`ol_to_dev` in
`ml/tts/dataset/transliterate.py`).

- Model: `modelpacks/piper-hi-base/hi-sat-interim.onnx` (+ `tokens.txt` from
  the voice's `phoneme_id_map`; ONNX metadata patched: `sample_rate=22050`,
  `n_speakers=1`, `language=hi`, `comment=piper`, `voice=hi` — the last two
  are REQUIRED by sherpa-onnx: `comment=piper` selects the Piper inference
  path, `voice` selects the espeak-ng voice).
- Frontend: espeak-ng `hi` via `/usr/share/espeak-ng-data` (dev machine only).
- Chain: MT Ol Chiki -> `normalize_mt` -> `ol_to_dev` -> espeak-hi phonemes
  -> VITS -> 22050 Hz mono PCM.

## Verified (2026-09-11, RTX 3050 host, sherpa-onnx 1.13.8, CPU)

| Input (Ol Chiki) | Speech | Wall time |
|---|---|---|
| ᱥᱟᱱᱛᱟᱲ ᱦᱚᱲ ᱫᱚ … (train line 1) | 4.31 s, rms 0.14 | 0.55 s |
| ᱪᱮᱫ ᱱᱚᱣᱟ ᱫᱤᱱ ᱱᱟᱯᱟᱭ ᱠᱟᱱᱟ? (MT output) | 1.60 s, rms 0.14 | 0.21 s |
| ᱡᱚᱦᱟᱨ (demo greeting) | 0.63 s, rms 0.17 | 0.11 s |

TTS slice of the <3 s sequential budget (ASR→MT→TTS): **met** (all ≤0.6 s).
WAVs: `/tmp/opencode/sat_chain_*.wav` (dev machine).

## What it is NOT

- NOT the final native voice: Hindi-accented male speaker, not the Santali
  speaker; checked consonants render as plain+approximations; C+ᱚ always
  surfaces as explicit ो (e.g. उनकिन→उनोकिन accent). Intelligible, not authentic.
- NOT the Android target: needs espeak-ng-data + phoneme tokens at runtime.
  The APK keeps Ol Chiki **char tokens** (`dataDir=''`) — that is the native
  fine-tune's export contract (`ml/tts/train.py`, `models/vits-sat.onnx`).

## Replacement plan

1. Fine-tune VITS (char tokens, Ol Chiki) on `ml/tts/dataset/metadata_train.csv`
   (4,557 utt / 7.46 h @22050 Hz) warm-started from LJSpeech/Hindi VITS.
2. Export real `models/vits-sat.onnx` (opset 17, 20–80 MB) + extended
   `tokens.txt` (Ol Chiki + punct) + `lexicon.txt`; verify with
   `ml/tts/audible_verification.py`.
3. Swap Android assets; keep this interim pack as fallback for Hindi-dominant
   classrooms. Update VOICE_CONSENT.md to `granted` before release.

## Native training run (live since 2026-09-11, tmux `satvits`)

- Env: `ml/tts/.venv-tts` (py3.11, torch 2.14 cu130, coqui-tts 0.27.5,
  transformers 4.53.3 — pinned, see install saga in session notes).
- Recipe: `ml/tts/finetune_sat.py --epochs 500 --batch 4` (batch 8 OOMs on
  3.7 GB usable VRAM; `expandable_segments:True` set).
- Data: `ml/tts/dataset/wavs_22050/` + `coqui_train/dev.csv` (derived from
  canonical `metadata_*.csv`; `wavs/` is a symlink — all git-ignored).
- Monitor: `tmux attach -t satvits`, log
  `ml/tts/runs/santali_vits_native/training.log`, checkpoints in run dir
  every 2000 steps (~0.55 s/step, ~10 min/epoch, ~3.6 d for 500 epochs).
- First listen test: any checkpoint ≥ ~50–100 epochs — synthesize the
  `test_sentences` (ᱡᱚᱦᱟᱨ …) via Coqui `synthesize.py`, keep machine awake.
- Keep this file + `VOICE_CONSENT.md` in sync when the native voice lands.
