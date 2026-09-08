package com.vachak.ml.adapter

/**
 * JNI bridge for CTranslate2 arm64-v8a CPU-only.
 * Exposes: init / translate / shutdown. Loaded once, singleton retained.
 * Build: android/ml/src/main/cpp/CMakeLists.txt -> libvachak_ct2_jni.so (arm64-v8a, API 28, OpenMP, no CUDA/CUDNN/CLI/tests)
 */
object Ct2Jni {
    // Load native lib once (CPU-only, arm64-v8a)
    private var loaded = false
    private var loadTried = false
    private var loadError: String? = null
    private fun ensureLoaded() {
        if (loaded) return
        if (loadTried && !loaded) throw UnsatisfiedLinkError(loadError ?: "vachak_ct2_jni not available")
        loadTried = true
        try {
            // Load dependencies explicitly before vachak_ct2_jni (DT_NEEDED may not auto-load on all OEMs)
            // Order: c++_shared -> omp -> spdlog -> ctranslate2 -> vachak
            try { System.loadLibrary("c++_shared") } catch (_: Throwable) {}
            try { System.loadLibrary("omp") } catch (_: Throwable) {}
            try { System.loadLibrary("spdlogd") } catch (_: Throwable) {}
            try { System.loadLibrary("spdlog") } catch (_: Throwable) {}
            try { System.loadLibrary("ctranslate2") } catch (e: Throwable) {
                android.util.Log.w("Vachak-Native", "libctranslate2 preload failed: ${e.message}")
            }
            System.loadLibrary("vachak_ct2_jni")
            loaded = true
            android.util.Log.d("Vachak-Native", "libvachak_ct2_jni.so loaded (with deps)")
        } catch (e: UnsatisfiedLinkError) {
            loaded = false
            loadError = e.message
            android.util.Log.e("Vachak-Native", "libvachak_ct2_jni not available: ${e.message} (host x86_64 fallback or missing pack)", e)
            throw e
        } catch (e: Exception) {
            loaded = false
            loadError = e.message
            android.util.Log.e("Vachak-Native", "libvachak_ct2_jni load failed: ${e.message}", e)
            throw UnsatisfiedLinkError(e.message)
        }
    }

    // Native handles: singleton Translator* as jlong
    @JvmStatic private external fun nativeInit(modelPath: String): Long
    @JvmStatic private external fun nativeTranslate(handle: Long, text: String, srcLang: String, tgtLang: String): String
    @JvmStatic private external fun nativeTranslateTokens(handle: Long, tokens: Array<String>, tgtLang: String): String
    @JvmStatic private external fun nativeShutdown(handle: Long)

    // Singleton handle, never per-request
    @Volatile private var handle: Long = 0
    private val lock = Any()

    fun init(modelPath: String): Long {
        ensureLoaded()
        synchronized(lock) {
            if (handle != 0L) return handle
            val h = nativeInit(modelPath)
            // nativeInit returns 0 on failure and may have thrown Java exception
            if (h == 0L) {
                android.util.Log.e("Vachak-Native", "nativeInit returned 0 for $modelPath - model missing or load failed")
                throw RuntimeException("CT2 init failed for $modelPath")
            }
            handle = h
            return handle
        }
    }

    fun translate(handle: Long, text: String, src: String, tgt: String): String {
        if (handle == 1L) throw IllegalArgumentException("mock handle 1L must use refMap fallback, not nativeTranslate")
        ensureLoaded()
        return nativeTranslate(handle, text, src, tgt)
    }

    /**
     * Tokenized path for the merged Mundari model (host-validated dev chrF
     * 13.0, identical to true-SP segmentation): pre-segmented source pieces +
     * atomic target tag, greedy + repetition guard, max 64. Returns
     * space-joined target pieces (caller detokenizes ▁ -> space).
     */
    fun translateTokens(handle: Long, tokens: List<String>, tgt: String): String {
        if (handle == 1L) throw IllegalArgumentException("mock handle 1L must use fallback, not nativeTranslateTokens")
        ensureLoaded()
        return nativeTranslateTokens(handle, tokens.toTypedArray(), tgt)
    }

    fun shutdown(handle: Long) {
        synchronized(lock) {
            if (handle != 0L) {
                try { nativeShutdown(handle) } catch (_: Throwable) {}
                this.handle = 0
            }
        }
    }

    // Host fallback for verification without NDK (uses JVM CTranslate2 via pip)
    fun isNativeAvailable(): Boolean {
        return try { ensureLoaded(); true } catch (_: Throwable) { false }
    }
}
