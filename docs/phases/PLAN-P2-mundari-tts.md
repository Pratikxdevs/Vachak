# PLAN — P2: Real Mundari/Santali Voice (TTS)

**Goal:** Swap the Chinese `vits-zh-aishell3` DEV-FIXTURE for a fine-tuned Santali/Mundari
VITS so the demo plays audible, on-domain audio.

**Context:**
- Current TTS adapter `SherpaOnnxTtsAdapter` works (loads via null AssetManager, generates
  audio) — only the *voice* is a placeholder.
- AGENTS.md: TTS budget 20–80 MB; train in `ml/tts`; ship via sherpa-onnx (Apache-2.0).
  **Piper is GPL-3.0 — excluded from the app** (training-only / reference only).
- Voice consent + dataset license must be tracked before merge.

**Tasks:**
1. Fine-tune Santali VITS in `ml/tts` on IndicVoices Santali + Common Voice Santali (CC BY 4.0).
2. Export → `vits-<mundari>.onnx` + `tokens.txt` + `lexicon.txt` (+ `espeak-ng-data` if needed).
3. Make `SherpaOnnxTtsAdapter` load its model path from the active language pack (P5), not hardcoded.
4. Bundle as a language pack; verify load + audible synthesis of a Mundari phrase.
5. Update provenance + THIRD_PARTY_NOTICES (voice consent, dataset licenses).

**Verify:** app plays audible Mundari audio; APK TTS slice ≤80 MB; offline.

**Acceptance:** "Speak" produces real Mundari speech, not a 12 ms Latin-blip.
