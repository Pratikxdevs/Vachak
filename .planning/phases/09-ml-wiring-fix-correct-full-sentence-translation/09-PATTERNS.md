# Phase 09 — Pattern Map

**Phase:** 09 — ML Wiring Fix
**Source:** GSD pattern-mapper (codebase analog scan)

## Files to Create/Modify

| File | Role | Analog | Excerpt Pattern |
|------|------|--------|-----------------|
| `android/ml/src/main/java/com/vachak/ml/adapter/IndicTrans2Adapter.kt` | MT adapter (BPE+ORT) | `01-02 PLAN` adapter + `indictrans2-onnx-export/src/translate.py:141` | `class IndicTrans2Adapter:TranslationEngine { fun translate(text, pair) { preprocess→encode→runEncoder→greedyDecode→batchDecode→postprocess } }` |
| `android/ml/src/main/java/com/vachak/ml/SherpaAssets.kt` | Asset copy | `SherpaAssets.kt:29` prepare() recursive | `fun prepare(context, subdir): String { File(filesDir, subdir).mkdirs(); copyTree(context.assets.list) }` |
| `android/ml/src/main/java/com/vachak/ml/IndicProcessorPort.kt` | Pre/post | `IndicProcessorPort.kt:28` | `object IndicProcessorPort { fun preprocessBatch(list, src,tgt):List<String> { NFKC+wrapPlaceholders+trivialTokenize } }` |
| `android/app/src/main/java/com/vachak/ui/screens/LiveScreen.kt` | Live UI | `LiveScreen.kt:63` | `LaunchedEffect(manualHindi) { delay(600); engine.translation.translate(...) }` Surface Live · Santali |
| `.planning/phases/09-*/09-01-PLAN.md` etc. | Plans | `01-02 PLAN` structure | frontmatter wave/depends_on/files_modified/autonomous/requirements/must_haves |

## Data Flow

`LiveScreen manualHindi / mic PCM → EngineProvider.real → IndicTrans2Adapter.translate → IndicProcessorPort.preprocess → BPE encode (merges 245k) → ORT encoder [1,seq,512] → greedy decoder (past KV) → batchDecode Metaspace → postprocess → OlChikiValidator U+1C50–U+1C7F → LiveScreen preview + ConversationMessagePair + TTS`

## Conventions to Respect

- `ReentrantLock` sequential, `Dispatchers.IO`, `numThreads=1`, `Log.d Vachak-MT` at tokenize/encode/decode/output/latency
- No `INTERNET` permission, offline `filesDir/vachak_models/mt`
- Pack-aware `PackManager.getActivePackFor(context,"mt")` → `SherpaAssets.prepare` fallback
- Fonts `VachakFontFamily` Lexend→Noto Devanagari→Noto Ol Chiki

## Anti-Patterns to Avoid

- Whitespace split instead of BPE
- Dummy zeros for encoder_hidden_states / past KV
- Fixed `[1,8,1,64]` shape for growing past
- `join(" ")` for Metaspace decode
