package com.vachak.ml

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig

/**
 * Hindi ASR via sherpa-onnx NEMO CTC 134M int8 (Uktam hi).
 * Offline, 16kHz, null AssetManager via SherpaAssets, 1 thread for 2GB.
 */
class IndicConformerAsrAdapter(
    private val context: Context,
    private val modelDir: String = "asr"
) : AsrAdapter {

    private var recognizer: OfflineRecognizer? = null
    private val tag = "Vachak-ASR"

    companion object {
        @Volatile private var sharedRecognizer: OfflineRecognizer? = null
        @Volatile private var sharedBaseDir: String? = null
        private val sharedLock = Any()
    }

    private fun buildConfig(baseDir: String): OfflineRecognizerConfig {
        val nemoPath = when {
            java.io.File("$baseDir/model.onnx").exists() -> "$baseDir/model.onnx"
            java.io.File("$baseDir/model.int8.onnx").exists() -> "$baseDir/model.int8.onnx"
            else -> "$baseDir/model.onnx"
        }
        Log.d(tag, "buildConfig NEMO dir=$baseDir model=$nemoPath")
        val modelConfig = OfflineModelConfig(
            numThreads = 1,
            tokens = "$baseDir/tokens.txt"
        ).apply {
            nemo = OfflineNemoEncDecCtcModelConfig(model = nemoPath)
        }
        return OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig
        )
    }

    private fun resolveBaseDir(): String {
        try {
            val packAsr = com.vachak.sync.PackManager.getActivePackFor(context, modelDir)
            if (packAsr != null && java.io.File("$packAsr/tokens.txt").exists()) {
                Log.d(tag, "using PackManager active ASR dir: $packAsr")
                return packAsr
            }
            val activePack = com.vachak.sync.PackManager.getActivePack(context)
            if (activePack != null) {
                val cand = java.io.File(activePack, "vachak_models/$modelDir")
                if (cand.isDirectory && java.io.File(cand, "tokens.txt").exists()) {
                    Log.d(tag, "using active pack vachak_models/$modelDir: ${cand.absolutePath}")
                    return cand.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "pack ASR resolution failed, falling back to bundled assets: ${e.message}")
        }
        return SherpaAssets.prepare(context, modelDir)
    }

    fun reloadFromPack() {
        recognizer?.let { try { /* OfflineRecognizer native handle will be recreated */ } catch (_: Exception) {} }
        recognizer = null
        Log.d(tag, "reloadFromPack: cleared recognizer for pack switch")
    }

    @Synchronized
    private fun ensureLoaded() {
        if (recognizer != null) return
        // Fast-path reuse of process-wide shared recognizer (avoids 100MB reload on every mic press -> UI lag).
        val baseDir = resolveBaseDir()
        synchronized(sharedLock) {
            if (sharedRecognizer != null && sharedBaseDir == baseDir) {
                recognizer = sharedRecognizer
                Log.d(tag, "reusing shared OfflineRecognizer dir=$baseDir")
                ModelStatus.setAsr(ModelInfo(ModelState.READY, "shared recognizer ($baseDir)"))
                return
            }
        }
        ModelStatus.loadingAsr("NEMO Hindi ($baseDir)")
        val t0 = android.os.SystemClock.elapsedRealtimeNanos()
        Log.d(tag, "creating OfflineRecognizer (NEMO, dir=$baseDir)")
        // Models are extracted to the filesystem (filesDir) or pack dir, so pass null AssetManager.
        try {
            val created = OfflineRecognizer(null, buildConfig(baseDir))
            recognizer = created
            synchronized(sharedLock) {
                sharedRecognizer = created
                sharedBaseDir = baseDir
            }
            val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
            ModelStatus.setAsr(ModelInfo(ModelState.READY, "NEMO Hindi 134M ($baseDir)", ms))
            Log.d(tag, "OfflineRecognizer ready in ${ms}ms (cached for reuse)")
        } catch (e: Exception) {
            ModelStatus.setAsr(ModelInfo(ModelState.ERROR, "ASR load failed: ${e.message?.take(140)}"))
            Log.e(tag, "OfflineRecognizer creation failed (dir=$baseDir)", e)
            throw e
        }
    }

    /** Non-blocking warm-up to be called off the UI thread (IO). */
    fun warmUpIfNeeded() {
        try {
            ensureLoaded()
        } catch (e: Exception) {
            // Status already recorded as ERROR by ensureLoaded; log once here.
            Log.w(tag, "warmUp failed: ${e.message}")
        }
    }

    override fun transcribe(samples: FloatArray, sampleRate: Int): AsrResult {
        ensureLoaded()
        val startNs = android.os.SystemClock.elapsedRealtimeNanos()
        val stream = recognizer!!.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer!!.decode(stream)
        val text = recognizer!!.getResult(stream).text
        stream.release()
        val latencyMs = (android.os.SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000f
        Log.d(tag, "transcribed ${samples.size} samples @ $sampleRate Hz -> \"$text\" (${latencyMs}ms)")
        Log.d("Vachak-Latency", "ASR decode ${latencyMs}ms for ${samples.size} samples")
        return AsrResult(text = text, confidence = 1.0f, isFixture = false)
    }
}
