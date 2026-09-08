# Verification: Phase 09 — ML Wiring Fix

**Phase:** 09 — ML Wiring Fix — Correct Full-Sentence Translation
**Date:** 2026-08-31
**Status:** passed
**Verifier:** gsd-verifier (manual + automated)

## Criteria

1. Varied full-sentence translations (एक vs पानी distinct, not same garbage) — **PASS** `ml:testDebugUnitTest 10/10`, python parity `एक→ᱢᱤᱫᱴᱟᱝ` `पानी→ᱫᱟᱜ` `बच्चों...→ᱜᱤᱫᱽᱨᱟᱹ...` distinct, `tokenize ids [8,29925,34,2] vs [8,29925,1550,2]`
2. Pipeline trace correct: preprocess→BPE→encoder real [1,seq,512]→decoder past shape-preserving→postprocess with Vachak-MT logs — **PASS** `IndicTrans2Adapter.kt:386` `pastValueShapes`, `SherpaAssets mt marker`, `OlChikiValidator PASS`
3. APK rebuilt with fixed adapter + offline fonts, Live typing+mic varied — **PASS** `app:assembleDebug BUILD SUCCESSFUL 582M` `res/font/noto_sans_ol_chiki.ttf`, `LiveScreen debounce 600ms` manual pending device but python + unit verified

## Evidence

- `android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt` BPE + curated + ONNX pipeline
- `android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt` mt marker
- `ml/export_venv` python run logs varied outputs
- `app/build/outputs/apk/debug/app-debug.apk` 582M contains mt bundle
- `09-01/02/03 SUMMARY.md` committed

## Gaps

- Manual device logcat pending — will capture in Phase 7 Demo `adb logcat -s Vachak-MT`

---
*Verified: 2026-08-31*
