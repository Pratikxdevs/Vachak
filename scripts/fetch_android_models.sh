#!/usr/bin/env bash
#
# fetch_android_models.sh — download DEV-FIXTURE sherpa-onnx models for the Vachak Android app.
#
# These are PUBLIC models from k2-fsa/sherpa-onnx (Apache-2.0). They are DEV FIXTURES used to
# prove the real sherpa-onnx runtime is wired end-to-end. They are NOT the final Mundari voice
# or the production Hindi ASR. See THIRD_PARTY_NOTICES.md + docs/MODEL_AND_DATA_PROVENANCE.md.
#
# Output layout (matches the paths the Kotlin adapters expect):
#   android/app/src/main/assets/vachak_models/{asr,tts,vad}
#     asr/  encoder.onnx  decoder.onnx  tokens.txt          (whisper-tiny multilingual)
#     tts/  model.onnx     tokens.txt    lexicon.txt  espeak-ng-data/  (vits-zh-aishell3)
#     vad/  silero_vad.onnx
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/android/app/src/main/assets/vachak_models"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$ASSETS"/{asr,tts,vad}

echo "[fetch] VAD: silero_vad.onnx"
curl -fsSL -o "$TMP/silero_vad.onnx" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
cp "$TMP/silero_vad.onnx" "$ASSETS/vad/silero_vad.onnx"
echo "        -> $ASSETS/vad/silero_vad.onnx ($(du -h "$ASSETS/vad/silero_vad.onnx" | cut -f1))"

echo "[fetch] ASR: whisper-tiny (multilingual, supports Hindi)"
curl -fsSL -o "$TMP/asr.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
mkdir -p "$TMP/asr"; tar -xjf "$TMP/asr.tar.bz2" -C "$TMP/asr"
ENC="$(find "$TMP/asr" -name '*encoder.int8.onnx' | head -1)"
[ -z "$ENC" ] && ENC="$(find "$TMP/asr" -name '*encoder*.onnx' | head -1)"
DEC="$(find "$TMP/asr" -name '*decoder.int8.onnx' | head -1)"
[ -z "$DEC" ] && DEC="$(find "$TMP/asr" -name '*decoder*.onnx' | head -1)"
TOK="$(find "$TMP/asr" -name '*tokens*.txt' | head -1)"
cp "$ENC" "$ASSETS/asr/encoder.onnx"
cp "$DEC" "$ASSETS/asr/decoder.onnx"
cp "$TOK" "$ASSETS/asr/tokens.txt"
echo "        -> $ASSETS/asr/ ($(du -ch "$ASSETS/asr"/* | tail -1 | cut -f1))"

echo "[fetch] TTS: vits-zh-aishell3 (DEV-FIXTURE voice, NOT Mundari)"
curl -fsSL -o "$TMP/tts.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-aishell3.tar.bz2"
mkdir -p "$TMP/tts"; tar -xjf "$TMP/tts.tar.bz2" -C "$TMP/tts"
MODEL="$(find "$TMP/tts" -name '*.onnx' | head -1)"
cp "$MODEL" "$ASSETS/tts/model.onnx"
cp "$(find "$TMP/tts" -name 'tokens.txt' | head -1)" "$ASSETS/tts/tokens.txt"
LEX="$(find "$TMP/tts" -name 'lexicon*.txt' | head -1)"
[ -n "$LEX" ] && cp "$LEX" "$ASSETS/tts/lexicon.txt"
echo "        -> $ASSETS/tts/ ($(du -ch "$ASSETS/tts"/* | tail -1 | cut -f1))"

echo "[fetch] TTS phonemizer data: espeak-ng-data (required by VITS, NOT in the model tar)"
curl -fsSL -o "$TMP/espeak-ng-data.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2"
mkdir -p "$TMP/espeak"
tar -xjf "$TMP/espeak-ng-data.tar.bz2" -C "$TMP/espeak"
cp -r "$TMP/espeak/espeak-ng-data" "$ASSETS/tts/"
echo "        -> $ASSETS/tts/espeak-ng-data/ ($(du -ch "$ASSETS/tts/espeak-ng-data" | tail -1 | cut -f1))"

echo "[fetch] DONE. Bundled model assets under $ASSETS"
