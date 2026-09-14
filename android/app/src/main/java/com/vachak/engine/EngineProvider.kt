package com.vachak.engine

import com.vachak.engine.VachakLog

import android.content.Context
import com.vachak.engine.mock.*
import com.vachak.ml.adapter.AdapterTranslationEngine
import com.vachak.ml.adapter.IndicTrans2Adapter
import com.vachak.ml.adapter.SherpaAsrAdapter
import com.vachak.ml.adapter.SherpaTtsAdapter
import kotlinx.coroutines.flow.StateFlow

/**
 * EngineProvider is the single injection point the UI depends on. The Compose
 * screen (MainScreen) only sees these interface-typed properties. Swapping a
 * real adapter for a mock (or vice-versa) happens HERE and NOWHERE ELSE, so UI
 * code never changes when an engine implementation is replaced.
 *
 * This is the mechanism that satisfies "mock translation can be swapped without
 * UI changes" — see EngineSwapTest.
 */
data class EngineProvider(
    val translation: TranslationEngine,
    val asr: ASREngine,
    val tts: TTSEngine,
    val curriculum: CurriculumEngine,
    val worksheet: WorksheetEngine,
    val flashcard: FlashcardEngine,
    val packs: LanguagePackManager,
    val sync: SyncManager,
    val benchmark: BenchmarkRunner,
    val activeLanguage: StateFlow<String> = ActiveLanguage.flow
) {
    companion object {
        /** Default build: all engines are dev-fixture mocks (fully offline). */
        fun mock(): EngineProvider = EngineProvider(
            translation = MockTranslationEngine,
            asr = MockAsrEngine,
            tts = MockTtsEngine,
            curriculum = MockCurriculumEngine,
            worksheet = MockWorksheetEngine,
            flashcard = MockFlashcardEngine,
            packs = MockLanguagePackManager,
            sync = MockSyncManager,
            benchmark = MockBenchmarkRunner
        )

        /**
         * REAL on-device runtime: sherpa-onnx ASR + TTS are live, IndicTrans2 Hin->Sat
         * MT is live via IndicTrans2Adapter (ONNX int8, ORT Mobile). Curriculum is
         * Room-backed real FLN (Phase 4). All offline, sequential.
         */
        fun real(context: Context): EngineProvider {
            val content = com.vachak.content.ContentEngine(context)
            // Sync ActiveLanguage from installed pack if present (offline, no network)
            try {
                val packs = com.vachak.sync.PackManager.packEntities(context)
                val active = packs.firstOrNull { it.isActive } ?: packs.firstOrNull()
                active?.language?.let { lang ->
                    if (lang.isNotBlank()) ActiveLanguage.set(lang)
                }
            } catch (e: Exception) {
                VachakLog.w("Vachak-Pack", "active-pack language sync failed, keeping default ${ActiveLanguage.current}: ${e.message}")
            }
            val adapterEngine = AdapterTranslationEngine(context, ActiveLanguage.current)
            return EngineProvider(
                translation = adapterEngine,
                asr = SherpaAsrAdapter(context, vad = com.vachak.ml.adapter.SherpaVadDetector(context)),
                tts = SherpaTtsAdapter(context),
                curriculum = content,
                worksheet = content,
                flashcard = content,
                packs = MockLanguagePackManager,
                sync = MockSyncManager,
                benchmark = MockBenchmarkRunner,
                activeLanguage = ActiveLanguage.flow
            )
        }
    }
}

object MockLanguagePackManager : LanguagePackManager {
    private val store = mutableMapOf<String, PackInfo>()
    override fun install(packPath: String): EngineResult<PackInfo> {
        val info = PackInfo(packPath, "mund", "0.1.0", 28, 0L)
        store[info.id] = info
        return EngineResult.Ok(info)
    }
    override fun uninstall(packId: String): EngineResult<Unit> =
        if (store.remove(packId) != null) EngineResult.Ok(Unit)
        else EngineResult.Err(EngineError.IO_ERROR, "not installed")
    override fun installed() = EngineResult.Ok(store.values.toList())
    override fun validate(packId: String) =
        EngineResult.Ok(store.containsKey(packId))
    override fun rollback(packId: String) =
        store[packId]?.let { EngineResult.Ok(it) }
            ?: EngineResult.Err(EngineError.IO_ERROR, "no prior version")
    override fun storageUsedBytes() = EngineResult.Ok(store.values.sumOf { it.sizeBytes })
}

object MockSyncManager : SyncManager {
    override fun installPackage(path: String) =
        EngineResult.Ok(SyncReport(path, "0.1.0", System.currentTimeMillis(), applied = true))
    override fun lastSync() = EngineResult.Ok(null)
}

object MockBenchmarkRunner : BenchmarkRunner {
    override fun run(pair: LanguagePair, sample: String): EngineResult<BenchmarkReport> {
        val r = BenchmarkReport(asrMs = 200, mtMs = 120, ttsMs = 300, totalMs = 620, withinBudget = true)
        return EngineResult.Ok(r)
    }
}
