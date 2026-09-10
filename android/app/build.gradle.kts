plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.vachak"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vachak"
        minSdk = 28 // Android 9 — hard requirement
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Build provenance: short git SHA baked in so any tablet can answer
        // "which build is installed" (Diagnostics + startup log). Never guess again.
        val gitSha = providers.exec {
            commandLine("git", "rev-parse", "--short=12", "HEAD")
        }.standardOutput.asText.get().trim().ifEmpty { "unknown" }
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        // ABI strategy: arm64-v8a first (2GB RAM tablet target). x86_64 is added for the
        // dev emulator (mt4_test) so the arm64-only native libs can be exercised on x86_64.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        // JUnit4 instrumented tests (BothAdaptersTest, TranslationEngineTest).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            // no shrink for fastest incremental builds
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Offline app: no network permissions declared (see Manifest)
        }
    }
    packaging {
        jniLibs {
            pickFirsts += listOf("**/libonnxruntime.so")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.material3.window.size)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(project(":core"))
    implementation(project(":content"))
    implementation(project(":sync"))
    implementation(project(":ml"))
    // sherpa-onnx vendored AAR must be on :app as well because :ml uses
    // compileOnly to avoid AGP 8.9 local-AAR-in-library error. APK still
    // needs the .so/.jar at runtime.
    implementation(files("../ml/libs/sherpa-onnx-1.13.0.aar"))
    implementation(libs.cupertino)
    implementation(libs.cupertino.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.tooling)
}
