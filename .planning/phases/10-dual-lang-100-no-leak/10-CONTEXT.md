# Context: Phase 10 Dual-Language 100% No-Leak

**Goal:** No functional leak - every page supports both Santali Ol Chiki and Mundari as adapters, live voice playback works end-to-end, debug UI visible, and remaining 20-25% closed without deleting ONNX.

**Scope:** `android/*`, `ml/*`, `curriculum/*`, `packages/*`, `benchmarks/*` - 100% functional demo.

**Hard constraints:** `DONT DELETE` ONNX 357M, offline after sync, sequential ASR->MT->TTS never parallel, 2GB RAM, precomputed curriculum only, template worksheets, prebuilt flashcards, no MACHINE_TRANSLATED as APPROVED, Piper GPL training-only.

**Success criteria (no leak):**
1. Home/Curriculum/Live/Tools/Settings/Diagnostics + ManagePacks all show language switch Santali<->Mundari (adapter switch via `LanguagePackManager activeLang`), no hard-coded `sat_Olck` or `unr_Deva` in UI.
2. Live PTT -> Hindi ASR windowed 48k -> CT2 MT (merged LoRA) -> Ol Chiki validate -> VITS 22050Hz -> AudioTrack play, total T0->T4 <3s logged `Vachak-Latency`, mic closes reliably.
3. TTS real voice for Santali (Coqui 300ep) with shim fallback for CI, LoRA merged CT2 223M replaces refMap for Mundari.
4. Debug UI: `DiagnosticsScreen` live latency/budget + floating `DebugOverlay` `adb logcat -s Vachak-*` viewer + `ManagePacks` active adapter badge.
5. SME gate: `2.2% verified` stays `DRAFT` with watermark, pack `APPROVED` requires human-reviewed `translation_provenance`.
6. Benchmark: `2GB Android9 arm64` `adb logcat Vachak-MT` captured to `BENCHMARK_REPORT.md` via `forbid_dev_as_android`.

**Out of scope:** Deleting ONNX, `compileSdk` downgrade, network inference, AI-generated worksheets.

**Dependencies:** Phases 1-9 complete (mic/translate hotfix, 15 lessons, 28M adapters bundled, packSha).
