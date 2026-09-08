#include "ctranslate2_singleton.h"
#include <ctranslate2/translator.h>
#include <ctranslate2/models/model.h>

Ct2Singleton& Ct2Singleton::instance() {
    static Ct2Singleton inst;
    return inst;
}

Ct2TranslatorWrapper* Ct2Singleton::init(const std::string& modelPath) {
    std::lock_guard<std::mutex> lock(mtx_);
    if (singleton_ && loadedPath_ == modelPath) return singleton_;
    if (singleton_) { delete static_cast<ctranslate2::Translator*>(singleton_->translator); delete singleton_; singleton_=nullptr; }
    // Validate model files exist before trying to load (avoid abort)
    {
        std::string modelBin = modelPath + "/model.bin";
        FILE* f = fopen(modelBin.c_str(), "rb");
        if (!f) {
            // Try modelPath as file itself
            f = fopen(modelPath.c_str(), "rb");
            if (!f) {
                throw std::runtime_error("model.bin not found at " + modelPath + " : " + modelBin);
            }
            fclose(f);
        } else fclose(f);
    }
    ctranslate2::ReplicaPoolConfig config;
    config.num_threads_per_replica = 1; // sequential 1 thread for 2GB RAM
    auto* trans = new ctranslate2::Translator(modelPath, ctranslate2::Device::CPU, ctranslate2::ComputeType::DEFAULT, {0}, false, config);
    singleton_ = new Ct2TranslatorWrapper{trans, modelPath};
    loadedPath_ = modelPath;
    return singleton_;
}
void Ct2Singleton::shutdown(Ct2TranslatorWrapper* h) {
    // No-op per-request; only on process exit to avoid reload cost. Kept for JNI contract.
    (void)h;
}
std::string Ct2TranslatorWrapper::translate(const std::string& text, const std::string& src, const std::string& tgt) {
    // Integrated 30k pruned SentencePiece + shared vocab (1.6M) - no 46M reuse
    // Preprocess: IndicProcessor tag already applied via Java (hin_Deva unr_Deva)
    auto* trans = static_cast<ctranslate2::Translator*>(translator);
    // CT2 translate_batch expects tokenized input; shared_vocab 93k handles text via SP directly
    // We pass raw text as single token batch - CT2's SentencePiece will segment via shared_vocabulary
    std::vector<std::vector<std::string>> batch = {{text}};
    ctranslate2::TranslationOptions opts;
    opts.max_decoding_length = 256;
    opts.beam_size = 4;
    auto results = trans->translate_batch(batch, opts);
    if (results.empty() || results[0].hypotheses.empty()) return "";
    // Postprocess handled in Java (IndicProcessor)
    std::string out;
    for (auto& t : results[0].hypotheses[0]) { out += t + " "; }
    if (!out.empty()) out.pop_back();
    return out;
}

std::string Ct2TranslatorWrapper::translateTokens(const std::vector<std::string>& tokens, const std::string& tgt) {
    auto* trans = static_cast<ctranslate2::Translator*>(translator);
    // Host-validated recipe (dev chrF 13.0): single atomic target tag prefix,
    // greedy decode, repetition guard. Matches training-time decoder behavior.
    std::vector<std::vector<std::string>> batch = {tokens};
    std::vector<std::vector<std::string>> prefix = {{tgt}};
    ctranslate2::TranslationOptions opts;
    opts.max_decoding_length = 64;
    opts.beam_size = 1;
    opts.repetition_penalty = 1.2;
    opts.no_repeat_ngram_size = 3;
    auto results = trans->translate_batch(batch, prefix, opts);
    if (results.empty() || results[0].hypotheses.empty()) return "";
    std::string out;
    for (auto& t : results[0].hypotheses[0]) { out += t + " "; }
    if (!out.empty()) out.pop_back();
    return out;
}
