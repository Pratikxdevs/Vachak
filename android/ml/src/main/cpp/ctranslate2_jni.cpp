#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <android/log.h>
#include "ctranslate2_singleton.h"

#define LOG_TAG "Vachak-Native"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

// model initialization: load CT2 model once, retain singleton - crash-safe
JNIEXPORT jlong JNICALL
Java_com_vachak_ml_adapter_Ct2Jni_nativeInit(JNIEnv* env, jclass, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::string p(path);
    env->ReleaseStringUTFChars(modelPath, path);
    LOGD("nativeInit path=%s", p.c_str());
    try {
        // Singleton, thread-safe, CPU-only, arm64-v8a
        auto* translator = Ct2Singleton::instance().init(p);
        if (!translator) {
            LOGE("nativeInit failed: init returned null for %s", p.c_str());
            return 0;
        }
        LOGD("nativeInit success handle=%p", translator);
        return reinterpret_cast<jlong>(translator);
    } catch (const std::exception& e) {
        LOGE("nativeInit exception for %s: %s", p.c_str(), e.what());
        // Throw Java exception so Kotlin catch works, don't abort
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        if (exClass) env->ThrowNew(exClass, e.what());
        return 0;
    } catch (...) {
        LOGE("nativeInit unknown exception for %s", p.c_str());
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        if (exClass) env->ThrowNew(exClass, "CT2 init unknown error");
        return 0;
    }
}

// translation: hin_Deva -> unr_Deva (Mundari) via pruned 30k shared vocab - crash-safe
JNIEXPORT jstring JNICALL
Java_com_vachak_ml_adapter_Ct2Jni_nativeTranslate(JNIEnv* env, jclass, jlong handle, jstring text, jstring srcLang, jstring tgtLang) {
    if (handle == 0) {
        LOGE("nativeTranslate handle=0");
        return env->NewStringUTF("");
    }
    const char* ctext = env->GetStringUTFChars(text, nullptr);
    const char* csrc = env->GetStringUTFChars(srcLang, nullptr);
    const char* ctgt = env->GetStringUTFChars(tgtLang, nullptr);
    std::string result;
    try {
        auto* translator = reinterpret_cast<Ct2TranslatorWrapper*>(handle);
        result = translator->translate(std::string(ctext), std::string(csrc), std::string(ctgt));
    } catch (const std::exception& e) {
        LOGE("nativeTranslate exception: %s", e.what());
        result = "";
    } catch (...) {
        LOGE("nativeTranslate unknown exception");
        result = "";
    }
    env->ReleaseStringUTFChars(text, ctext);
    env->ReleaseStringUTFChars(srcLang, csrc);
    env->ReleaseStringUTFChars(tgtLang, ctgt);
    return env->NewStringUTF(result.c_str());
}

// translation (tokenized): pre-segmented pieces + atomic target tag.
// Validated recipe for the merged Mundari model — see translateTokens.
JNIEXPORT jstring JNICALL
Java_com_vachak_ml_adapter_Ct2Jni_nativeTranslateTokens(JNIEnv* env, jclass, jlong handle, jobjectArray tokens, jstring tgtLang) {
    if (handle == 0) {
        LOGE("nativeTranslateTokens handle=0");
        return env->NewStringUTF("");
    }
    std::vector<std::string> toks;
    const jsize n = env->GetArrayLength(tokens);
    toks.reserve((size_t)n);
    for (jsize i = 0; i < n; ++i) {
        auto* s = (jstring)env->GetObjectArrayElement(tokens, i);
        if (!s) continue;
        const char* c = env->GetStringUTFChars(s, nullptr);
        toks.emplace_back(c ? c : "");
        if (c) env->ReleaseStringUTFChars(s, c);
        env->DeleteLocalRef(s);
    }
    const char* ctgt = env->GetStringUTFChars(tgtLang, nullptr);
    std::string result;
    try {
        auto* translator = reinterpret_cast<Ct2TranslatorWrapper*>(handle);
        result = translator->translateTokens(toks, std::string(ctgt ? ctgt : ""));
    } catch (const std::exception& e) {
        LOGE("nativeTranslateTokens exception: %s", e.what());
        result = "";
    } catch (...) {
        LOGE("nativeTranslateTokens unknown exception");
        result = "";
    }
    env->ReleaseStringUTFChars(tgtLang, ctgt);
    return env->NewStringUTF(result.c_str());
}

// model shutdown: release singleton (called on process kill, not per-request)
JNIEXPORT void JNICALL
Java_com_vachak_ml_adapter_Ct2Jni_nativeShutdown(JNIEnv* env, jclass, jlong handle) {
    if (handle == 0) return;
    auto* translator = reinterpret_cast<Ct2TranslatorWrapper*>(handle);
    Ct2Singleton::instance().shutdown(translator);
}

} // extern "C"
