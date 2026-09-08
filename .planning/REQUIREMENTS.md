# Requirements: Vachak — Santali Only

## MT — Hin→Santali (Ol Chiki)

- **MT-01**: Fully on-device via ONNX Runtime Mobile, sequential ASR→MT→TTS, no network
- **MT-02**: Base IndicTrans2 distilled 320M; fine-tune only on CC BY 4.0 COILD HIN-SAT 20,603 + Education_v2; quantized int8 ONNX ≤180MB (arm64-v8a first)
- **MT-03**: ≤0.5s on-device (LatencyTracker); never train on IN22-Gen/Conv

## TTS — Santali Voice

- **TTS-01**: VITS via sherpa-onnx, 20–80MB, fine-tuned on CC BY 4.0 IndicVoices Santali 19,779 + Nirantar 13,503 (HF adjaysagar/nirantar) + Rasa; not Chinese DEV-FIXTURE
- **TTS-02**: Voice consent + dataset/model licenses in THIRD_PARTY_NOTICES; Piper GPL-3.0 excluded from APK

## ASR — Hindi

- **ASR-01**: Offline Hindi ASR (whisper-tiny / IndicConformer / Vosk benchmark, 30–80MB) + Silero VAD, ≤1s
- **ASR-02**: Verifiable without mic via WAV-fed path (emulator/headless)

## Curriculum

- **CUR-01**: FLN lessons with Hindi source + precomputed Santali Ol Chiki translation stored in Room (curriculum/ → content/)
- **CUR-02**: NIPUN outcome mapping per lesson
- **CUR-03**: Template worksheets (not AI-generated) + prebuilt flashcard assets; 10–30MB + 20–50MB

## Packaging

- **PACK-01**: packages/ builds signed versioned pack (MT+TTS+ASR models + curriculum) with manifest (sha256 + license list)
- **PACK-02**: sync/ is pack installer (not network client) — verifies, copies to app data, registers in Room; no android.permission.INTERNET; supports P2 model swap

## Performance

- **PERF-01**: Total <3s (ASR≤1s + MT≤0.5s + TTS≤1s), sequential only (RAM limit)
- **PERF-02**: Peak RSS ≤2GB, APK+pack ~500MB within AGENTS.md budget table

## Demo

- **DEMO-01**: Full offline script WiFi-OFF passes with evidence (screens + Vachak-* logcat + LatencyTracker) + provenance complete

## Datasets (verified, CC BY 4.0 unless noted)

| Dataset | Size | License | Use |
|---------|------|---------|-----|
| COILD-MT-Corpus HIN-SAT | 20,603 pairs | CC BY 4.0 | Primary MT fine-tune (Santali) |
| Education_v2 HIN-SAT | education domain | CC BY 4.0 | Domain adapt |
| IndicVoices Santali | 19,779 train | CC BY 4.0 | TTS (+ ASR) |
| Nirantar Santali | 13,503 utt / 161.29h / 433 spk | CC BY 4.0 (verify) | TTS/ASR diversity |
| Common Voice Santali | ~533 clips | CC BY 4.0 | Supplementary |
| Rasa (AI4Bharat) | Santali subset | CC-BY-4.0 | TTS methodology |
| IN22-Gen/Conv | — | — | Eval only, never train |
