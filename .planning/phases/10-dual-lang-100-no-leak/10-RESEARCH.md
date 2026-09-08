# Research: Phase 10 Dual-Language 100% No-Leak

**Date:** 2026-09-04
**Scope:** Close remaining 20-25% functional gap to 100% with zero functional leak, dual-language (Santali Ol Chiki + Mundari) as adapters, live voice playback, debug UI, and P6/P7 device proof. `DONT DELETE` ONNX 357M preserved.

## 1. Current State (Evidence)

- APK 556M debug `android/app/build/outputs/apk/debug/app-debug.apk` with `compileSdk 35` `isMinifyEnabled false/true` dual, `ONNX 357M` + `CT2 223M` + `shim TTS 40M` + `adapters 28M` in `assets/modelpacks`.
- `LiveScreen 676 lines` God Composable with `LiveViewModel Hilt` minimal owning `AudioCapturer`, but `Home/Curriculum/Tools/Settings` still `runBlocking`/`title.contains`/`random` and `VachakApp when(current)` no NavHost.
- `AudioCapturer cap 14s 224k` + `stopBeforeCancel 1500ms` + `AtomicInteger` fixes mic close `pcmFinal 108k committed` `VOICE END ASR 131ms`.
- `IndicTrans2Adapter supports lower-case` fixes `hi->unr_Deva` UNSUPPORTED, mock `1L` guard fallback `refMap` prevents native crash.
- `sat_lessons 15` vs `manifest 15` fixed, `2.2% verified DRAFT`, `hin_mun BY-NC-SA-FS` quarantined marker `datasets/_quarantine`, `VOICE_CONSENT` unsigned.
- `packSha two-phase` outer truth `157df3...` `558->386M zip`, `PackDao suspend IO` wrappers, `benchmarks PENDING` honest.

## 2. Remaining Research Questions

- **TTS real:** `models/vits-sat.onnx` shim `MatMul[4096,2560]` 40M vs Coqui VITS 22050Hz Ol Chiki 55 tokens `U+1C50-U+1C7F` needs `SAT-IV-SP001 3200/4.52h` + GPU 300ep. Current `detectShim` only logs.
- **LoRA merge:** `ml/finetune/it2_mundari_lora_real 14M r=16` present, `merge_lora_to_ct2.py --dry-run` exists but not executed to `stripped_mt_merged 223M`. `AdapterTranslationEngine` still `refMap` not merged CT2.
- **NavHost:** `VachakApp: when(current)` loses backstack, `pendingLesson` dead. No type-safe `NavDest` args.
- **SME gate:** `2.2% verified` -> `DRAFT` watermark correct per `Never ship MACHINE_TRANSLATED`, needs native speaker path but blocked for demo.
- **Device <3s:** `LatencyTracker T0-T4` logs `VOICE END total 136ms` but `BENCHMARK_REPORT.md PENDING` no `2GB Android9 adb` capture.

## 3. Decisions (Locked)

- Keep `ONNX 357M` in debug, `CT2 223M` is prod MT, `LoRA 14M` build-time merged (not runtime overlay) - runtime overlay would need `peft` on device.
- TTS real behind `BuildConfig.DEBUG` guard: `shim` for CI, real for `release` `MODEL_NOT_LOADED` fallback to degraded text if missing.
- NavHost with `androidx.navigation:navigation-compose 2.7.7` already in `app/build.gradle.kts`, migrate one screen at a time.
- Debug UI: `DiagnosticsScreen` + new `DebugOverlay` (floating `Vachak-*` log viewer) + `ManagePacksScreen` storage live.

## 4. Risks

- `compileSdk 35` `windowOptOutEdgeToEdge` already forced, Hilt adds 2-3M - acceptable vs 500M.
- LoRA merge needs `transformers+peft` alias `BartForConditionalGeneration=IndicClass` and vocab trim `122672` - validated in `convert_ct2.py`.
- Audio cap truncated drops samples - log warning, no OOM.
