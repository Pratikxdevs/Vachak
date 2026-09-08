#pragma once
#include <string>
#include <mutex>
#include <memory>

// Forward declare CTranslate2 wrapper
struct Ct2TranslatorWrapper {
    void* translator; // ctranslate2::Translator*
    std::string modelPath;
    std::string translate(const std::string& text, const std::string& src, const std::string& tgt);
    // Tokenized path (validated host chrF 13.0): pre-segmented source pieces +
    // atomic target tag, greedy + anti-loop penalties. Used by the merged
    // Mundari model; the raw-text path above stays for compat.
    std::string translateTokens(const std::vector<std::string>& tokens, const std::string& tgt);
};

class Ct2Singleton {
public:
    static Ct2Singleton& instance();
    Ct2TranslatorWrapper* init(const std::string& modelPath); // load once, retain
    void shutdown(Ct2TranslatorWrapper* handle);
    // Never per-request: singleton retained for <3s sequential pipeline (ASR->MT->TTS)
private:
    Ct2Singleton() = default;
    std::mutex mtx_;
    Ct2TranslatorWrapper* singleton_ = nullptr;
    std::string loadedPath_;
};
