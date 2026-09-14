package com.vachak.ml

import android.content.Context
import com.vachak.engine.VachakLog
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream

/**
 * REAL ASR via the vendored sherpa-onnx AAR (k2-fsa/sherpa-onnx, Apache-2.0), built
 * over the cloned reference repo's Android API.
 *
 * Model: `vachak_models/asr/model.onnx` — a NeMo EncDecCTCModelBPE (5113 nodes,
 * opset17, quantized to int8/int4, metadata `model_type=EncDecCTCModelBPE`,
 * `subsampling_factor=4`, vocab 5633). Its declared input is `audio_signal`
 * `[B, 80, T]` (channel-first, the original AI4Bharat export) — this is what
 * sherpa-onnx feeds it: `OfflineNemoEncDecCtcModel::Forward` computes fbank
 * as (B,T,80) and applies `Transpose12` itself, `(B, T, C) -> (B, C, T)`.
 * (A Sep-2026 `fix_asr_input_layout.py` wrongly rewrote the graph to [B,T,80];
 * reverted by `scripts/revert_asr_layout_fix.py` after device logcat proved
 * every decode threw `index: 2 Got: <T> Expected: 80`.)
 *
 * This adapter therefore drives the model through sherpa-onnx's
 * [OfflineRecognizer] + [OfflineNemoEncDecCtcModelConfig], which owns the
 * feature extraction, Transpose12, CTC greedy decode and token->text mapping.
 * It does NOT hand features to a bare ORT session: that path would need the
 * caller to replicate sherpa's fbank + transpose exactly.
 *
 * Assets: `model.onnx` (NeMo CTC), `tokens.txt` (5633 BPE tokens). Extracted to
 * filesDir via [SherpaAssets.prepare]. numThreads=1 (2GB RAM bound). No network.
 *
 * Provenance: `ml/asr/` training run -> `models/indicconformer-hi.onnx` ->
 * `android/assets/vachak_models/asr/`. See `THIRD_PARTY_NOTICES.md:Phase 3` and
 * `docs/MODEL_AND_DATA_PROVENANCE.md:P3`.
 */
class IndicConformerAsrAdapter(
    private val context: Context,
    private val modelDir: String = "asr",
    private val sampleRate: Int = 16000
) : AsrAdapter {
    private var recognizer: OfflineRecognizer? = null
    private var loaded = false
    private val tag = "Vachak-ASR"

    companion object {
        /**
         * Process-wide recognizer: the 140MB load happens ONCE and is reused
         * by every mic press / session / adapter instance. Before this, each
         * StreamingAsrSession built its own adapter + recognizer (2.7-4s load
         * per Start, 140MB churn each time) — the biggest slice of
         * time-to-first-partial. Guarded by [sharedLock]; decode callers
         * synchronize on it too (native recognizer is not thread-safe, and
         * the pipeline is sequential anyway). Only a successfully created
         * recognizer is cached — a failed load never poisons later presses.
         */
        private val sharedLock = Any()
        private var sharedRecognizer: OfflineRecognizer? = null
        private var sharedModelPath: String? = null

        fun shared(modelPath: String, tokensPath: String, threads: Int, tag: String): OfflineRecognizer =
            synchronized(sharedLock) {
                val cur = sharedRecognizer
                if (cur != null && sharedModelPath == modelPath) return cur
                if (cur != null) {
                    runCatching { cur.release() }
                    sharedRecognizer = null
                }
                val config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        nemo = OfflineNemoEncDecCtcModelConfig(model = modelPath),
                        numThreads = threads,
                        tokens = tokensPath
                    )
                )
                VachakLog.d(tag, "creating SHARED OfflineRecognizer (NEMO CTC, model=$modelPath, threads=$threads)")
                val created = OfflineRecognizer(null, config)
                sharedRecognizer = created
                sharedModelPath = modelPath
                created
            }

        /** True once the shared recognizer is resident (partials may decode). */
        fun isSharedWarm(): Boolean = synchronized(sharedLock) { sharedRecognizer != null }

        /** Serializes decodes across sessions sharing the recognizer. */
        fun <T> withSharedLock(block: () -> T): T = synchronized(sharedLock) { block() }
    }

    /**
     * True when the resolved ASR model is the bundled NeMo CTC graph (always true
     * for the shipped `model.onnx`). Callers use this to decide whether to attempt
     * a native decode or surface an honest MODEL_NOT_LOADED-style message.
     */
    fun isShim(): Boolean = false

    /**
     * Non-blocking warm-up off the UI thread (IO): resolves + creates the
     * OfflineRecognizer so first decode has no cold-load pause.
     * @return true when warmed; false on real failure.
     */
    fun warmUpIfNeeded(): Boolean {
        return try {
            ensureLoaded()
            true
        } catch (t: Throwable) {
            // Honesty contract: a failed warm-up reports false AND ModelStatus.ERROR
            // (never a fake OK, never a swallowed throw). Under Robolectric there
            // are no native sherpa libs, so this is the expected clean failure.
            ModelStatus.setAsr(ModelInfo(ModelState.ERROR, "ASR warm-up failed: ${t.message?.take(140)}"))
            VachakLog.w(tag, "warmUp failed: ${t.message}")
            false
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        val baseDir = SherpaAssets.prepare(context, modelDir)
        val modelPath = "$baseDir/model.onnx"
        val tokensPath = "$baseDir/tokens.txt"
        if (!java.io.File(modelPath).exists()) {
            VachakLog.e(tag, "ASR model.onnx not found at $modelPath")
            throw IllegalStateException("ASR model.onnx missing at $modelPath")
        }
        if (!java.io.File(tokensPath).exists()) {
            VachakLog.e(tag, "ASR tokens.txt not found at $tokensPath")
            throw IllegalStateException("ASR tokens.txt missing at $tokensPath")
        }
        val t0 = android.os.SystemClock.elapsedRealtimeNanos()
        // UI-observable "model live" state: the ASR dot + mic button read
        // this. Previously only the ERROR path reported, so ASR never showed
        // READY even while transcribing fine.
        if (!isSharedWarm()) ModelStatus.loadingAsr("Conformer Hindi ASR (140MB)")
        // 2 threads: conformer matmuls scale ~1.6x vs 1 thread on multi-core
        // (emulator + 2GB tablets); threads share weights, no extra RAM, and
        // the pipeline is still sequential (one model at a time).
        val threads = minOf(2, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        // Models are extracted to filesDir, so pass null AssetManager (fs path).
        // Shared process-wide: first press loads, every later press reuses.
        recognizer = shared(modelPath, tokensPath, threads, tag)
        val ms = (android.os.SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000
        ModelStatus.setAsr(ModelInfo(ModelState.READY, "Conformer live — ready for capture", ms))
        VachakLog.d(tag, "OfflineRecognizer ready in ${ms}ms (baseDir=$baseDir, shared=${isSharedWarm()})")
        loaded = true
    }

    /** True when this adapter can decode without a cold load. */
    fun isLoaded(): Boolean = loaded || isSharedWarm()

    /**
     * Transcribe one speech segment (float PCM at [sampleRate]) to Hindi text.
     *
     * Drives the sherpa-onnx OfflineRecognizer: createStream -> acceptWaveform
     * -> decode -> getResult. The recognizer owns fbank + Transpose12 into the
     * [B,80,T] layout the NeMo graph expects, so the float PCM is passed
     * straight in — no manual transpose.
     */
    override fun transcribe(samples: FloatArray, sampleRate: Int): AsrResult {
        ensureLoaded()
        val startNs = android.os.SystemClock.elapsedRealtimeNanos()
        VachakLog.d(tag, "decode ${samples.size} samples @ $sampleRate Hz (~${samples.size / 160} frames)")
        // Serialized on the shared lock: the process-wide recognizer is not
        // thread-safe, and partial + final decodes must never overlap.
        val result = withSharedLock {
            val stream: OfflineStream = recognizer!!.createStream()
            stream.acceptWaveform(samples, sampleRate)
            recognizer!!.decode(stream)
            recognizer!!.getResult(stream)
        }
        val text = result.text.trim()
        val latencyMs = (android.os.SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000f
        VachakLog.d(tag, "transcribed ${samples.size} samples @ $sampleRate Hz -> \"$text\" (${latencyMs}ms, tokens=${result.tokens.size})")
        if (latencyMs > 1000) VachakLog.w("Vachak-Latency", "ASR latency ${latencyMs}ms exceeds 1000ms budget")
        return AsrResult(text = text, confidence = 0.0f, isFixture = false)
    }
}