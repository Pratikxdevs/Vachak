# Vachak R8 keep rules — production (offline, shrinks APK for 2GB/500MB budget)
# Preserve sherpa-onnx, ONNX Runtime, and CT2 JNI; allow mock stripping in release.
# ONNX 357M assets kept in app/assets/vachak_models/mt until CT2 passes — do NOT strip.

# sherpa-onnx (k2-fsa) — vendored AAR libs/sherpa-onnx-1.13.0.aar (Apache-2.0)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.k2fsa.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ONNX Runtime Mobile — com.microsoft.onnxruntime (ORT 1.24.3, matches sherpa's libonnxruntime.so)
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn com.microsoft.onnxruntime.**

# CTranslate2 JNI — com.vachak.ml.adapter.Ct2Jni + native libvachak_ct2_jni.so (arm64-v8a)
-keep class com.vachak.ml.adapter.Ct2Jni { *; }
-keep class com.vachak.ml.adapter.IndicTrans2Adapter { *; }
-keep class com.vachak.ml.adapter.AdapterTranslationEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep real adapters used by EngineProvider.real (prevent R8 stripping before reflection check)
-keep class com.vachak.ml.adapter.SherpaAsrAdapter { *; }
-keep class com.vachak.ml.adapter.SherpaTtsAdapter { *; }
-keep class com.vachak.ml.adapter.SherpaVadDetector { *; }
-keep class com.vachak.ml.SherpaOnnxTtsAdapter { *; }
-keep class com.vachak.ml.IndicConformerAsrAdapter { *; }
-keep class com.vachak.ml.AudioCapturer { *; }
-keep class com.vachak.ml.StreamingAsrSession { *; }

# Keep Engine interfaces — UI depends on them via EngineProvider (no reflection removal)
-keep class com.vachak.engine.** { *; }
-keep interface com.vachak.engine.** { *; }

# Keep Room entities (content/sync) — reflection via @Entity
-keep class com.vachak.content.db.** { *; }
-keep class com.vachak.sync.db.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase { *; }

# Do NOT keep mocks in release — allow R8 to strip MockEngines if unused.
# EngineProvider.mock is debug/test only; real path is EngineProvider.real.
# Explicitly not keeping: com.vachak.engine.mock.** remains eligible for removal in minified release.
# If you need mocks in debug, use -keep in debug proguard only.
# (No -keep for mock package here by design.)

# AndroidX keep for lifecycle ViewModel that now owns AudioCapturer
-keep class androidx.lifecycle.** { *; }
-keep class com.vachak.ui.screens.LiveViewModel { *; }

# Generic Android keep
-keep class androidx.compose.** { *; }
-dontnote kotlin.**
-dontwarn kotlin.**
