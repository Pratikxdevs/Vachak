plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.vachak.ml"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DWITH_CUDA=OFF", "-DWITH_CUDNN=OFF",
                    "-DWITH_MKL=OFF", "-DWITH_DNNL=OFF", "-DWITH_ACCELERATE=OFF",
                    "-DWITH_RUY=ON", "-DWITH_OPENMP=ON", "-DOPENMP_RUNTIME=COMP",
                    "-DBUILD_CLI=OFF", "-DBUILD_TESTS=OFF"
                )
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs {
            pickFirsts += listOf("**/libonnxruntime.so", "**/libc++_shared.so")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(project(":core"))
    implementation(project(":sync"))
    compileOnly(files("libs/sherpa-onnx-1.13.0.aar"))
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.json:json:20231013")
    testImplementation(libs.onnxruntime)
}
