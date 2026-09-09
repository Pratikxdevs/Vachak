package com.vachak.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.vachak.engine.EngineProvider
import com.vachak.ml.StreamingAsrSession
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Entry point. Injects the engine set via EngineProvider. To ship real models, change ONLY
 * EngineProvider (e.g. real sherpa-onnx / IndicTrans2 adapters); this Activity and the Compose
 * screen stay unchanged.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        init {
            // Explicitly load ONNX Runtime native library before any ONNX usage
            // This fixes "libonnxruntime.so missing" errors on some devices
            val nativeLibs = listOf("onnxruntime", "onnxruntime4j_jni")
            var loaded = false
            for (lib in nativeLibs) {
                try {
                    System.loadLibrary(lib)
                    android.util.Log.d("Vachak-Native", "Successfully loaded $lib")
                    loaded = true
                    break
                } catch (e: UnsatisfiedLinkError) {
                    android.util.Log.d("Vachak-Native", "Failed to load $lib: ${e.message}")
                }
            }
            if (!loaded) {
                android.util.Log.e("Vachak-Native", "Failed to load any ONNX Runtime native library")
                // Diagnostic: check if native library directory exists
                try {
                    val nativeLibDir = java.lang.System.getProperty("java.library.path")
                    android.util.Log.d("Vachak-Native", "java.library.path: $nativeLibDir")
                } catch (e: Exception) {
                    android.util.Log.e("Vachak-Native", "Failed to get java.library.path: ${e.message}")
                }
            }
        }
    }

    private val micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        android.util.Log.d("Vachak-ASR", "MainActivity mic permission result=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request mic permission immediately on launch (emulator needs explicit prompt)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("Vachak-ASR", "MainActivity requesting RECORD_AUDIO")
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            android.util.Log.d("Vachak-ASR", "MainActivity mic permission already granted")
        }
        // ── High refresh rate opt-in ──
        // Without this, Android caps the app at 60Hz while the display runs at 90/120Hz,
        // causing judder/lag that is extremely visible on high-refresh devices.
        enableHighRefreshRate()
        // Edge-to-edge: let Compose handle insets via statusBarsPadding/navigationBarsPadding/imePadding
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Log device info for debugging
        android.util.Log.d("Vachak-Native", "Device ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        android.util.Log.d("Vachak-Native", "Android version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        // Log refresh rate for diagnostics
        @Suppress("DEPRECATION")
        val displayRefresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.refreshRate
        } else {
            windowManager.defaultDisplay.refreshRate
        }
        android.util.Log.d("Vachak-Native", "Display refreshRate: ${displayRefresh}Hz, preferredModeId=${window.attributes.preferredDisplayModeId}")

        val engine = EngineProvider.real(this)
        // Background model preparation — preload heavy ONNX/sherpa assets off Main, not blocking first frame.
        // Results are LOGGED WITH CAUSES (never swallowed): a red model here
        // explains every downstream failure. Live state in ModelStatus.
        lifecycleScope.launch(Dispatchers.IO) {
            val tag = "Vachak-Models"
            try {
                when (val r = engine.translation.loadModel("")) {
                    is com.vachak.engine.EngineResult.Ok -> android.util.Log.d(tag, "preload MT: OK")
                    is com.vachak.engine.EngineResult.Err -> android.util.Log.e(tag, "preload MT failed [${r.code}]: ${r.message}")
                }
            } catch (e: Throwable) { android.util.Log.e(tag, "preload MT threw", e) }
            try {
                when (val r = engine.asr.loadModel("")) {
                    is com.vachak.engine.EngineResult.Ok -> android.util.Log.d(tag, "preload ASR: OK")
                    is com.vachak.engine.EngineResult.Err -> android.util.Log.e(tag, "preload ASR failed [${r.code}]: ${r.message}")
                }
            } catch (e: Throwable) { android.util.Log.e(tag, "preload ASR threw", e) }
            try {
                when (val r = engine.tts.loadModel("")) {
                    is com.vachak.engine.EngineResult.Ok -> android.util.Log.d(tag, "preload TTS: OK")
                    is com.vachak.engine.EngineResult.Err -> android.util.Log.e(tag, "preload TTS failed [${r.code}]: ${r.message}")
                }
            } catch (e: Throwable) { android.util.Log.e(tag, "preload TTS threw", e) }
            // Pre-warm ASR session to reduce first partial latency
            try {
                val warmupSession = StreamingAsrSession(this@MainActivity)
                warmupSession.start(System.nanoTime())
                warmupSession.warmUpAsync()
            } catch (e: Throwable) { android.util.Log.w("Vachak-ASR", "startup ASR warm-up threw (first mic press will cold-load)", e) }
        }
        setContent {
            // Adapt system bars to device dark/light setting — avoids contradicting user theme
            // isSystemInDarkTheme() respects Settings > Display > Dark theme / scheduled dark mode
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                // Light status/nav icons when darkTheme=false (light bg), dark icons when true is inverted
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
                // Keep bars transparent for edge-to-edge; Compose Surface draws the #FAF9FF / #151218 bg
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
            // Auth bypassed per transformation plan — direct to SaaS app
            VachakApp(engine = engine)
        }
    }

    /**
     * Opt into the display's highest refresh rate mode.
     * - API 30+ we can set preferredDisplayModeId to the mode with max refreshRate.
     * - API 23+ fallback is done via WindowManager.LayoutParams.preferredRefreshRate on some OEMs.
     * Without this, Choreographer vsyncs at 60Hz while SurfaceFlinger composites at 90/120Hz → visible stutter.
     */
    private fun enableHighRefreshRate() {
        try {
            val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val modes = display.supportedModes
                if (!modes.isNullOrEmpty()) {
                    // Pick mode with highest refresh rate at native resolution
                    val best = modes.maxByOrNull { it.refreshRate }
                    if (best != null) {
                        val lp = window.attributes
                        // Only override if we found a higher-than-60Hz mode and it differs from current
                        if (best.refreshRate > 60.5f) {
                            lp.preferredDisplayModeId = best.modeId
                            window.attributes = lp
                            android.util.Log.d("Vachak-Native", "High-refresh enabled: modeId=${best.modeId} ${best.physicalWidth}x${best.physicalHeight}@${best.refreshRate}Hz")
                        }
                        // API 30+ also supports preferredRefreshRate hint for OEMs that ignore modeId
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            lp.preferredRefreshRate = best.refreshRate
                            window.attributes = lp
                        }
                    }
                }
            }
            // OEM fallback: some devices expose high refresh via layout param flag
            // Setting preferredRefreshRate via reflection-safe attribute already handled above
        } catch (e: Exception) {
            android.util.Log.w("Vachak-Native", "enableHighRefreshRate failed: ${e.message}")
        }
    }
}
