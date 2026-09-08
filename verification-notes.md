# <3s Latency Verification Notes

## Benchmark Protocol

### Target Device
- **Device**: ARM64 2GB Android device (real hardware)
- **OS**: Android 9+ (API 28+)
- **Network**: WiFi disabled (fully offline)
- **ABI**: arm64-v8a

### Measurement Commands

```bash
# Clear logcat before test
adb logcat -c

# Run test with stage-specific logging
adb logcat -s Vachak-ASR:V Vachak-MT:V Vachak-TTS:V Vachak-Latency:V Vachak-VAD:V

# Expected log format
VOICE END ASR <asr_ms>ms -> "<transcript>"
MT <mt_ms>ms [MT:INFERENCE] "<hindi>" -> "<santali>"
TTS <tts_ms>ms [TTS:SYNTHESIS] <sample_count> samples
VOICE END total <total_ms>ms (< 3s ✓ | ≥ 3s ⚠)
```

### Stage Definitions

1. **ASR Final Latency**: `VOICE END ASR` timestamp - speech end
   - Target: < 1000ms
   - Measures: StreamingAsrSession.finish() time

2. **MT Inference Latency**: `MT [MT:INFERENCE]` timestamp
   - Target: < 500ms
   - Measures: IndicTrans2Adapter.translate() time

3. **TTS Synthesis Latency**: `TTS [TTS:SYNTHESIS]` timestamp
   - Target: < 1000ms
   - Measures: SherpaOnnxTtsAdapter.synthesize() time

4. **First Audio Latency**: `VOICE END total` timestamp - speech end
   - Target: < 3000ms
   - Measures: End-to-end pipeline (ASR + MT + TTS)

### Test Procedure

#### Cold Start
1. Kill app: `adb shell am force-stop com.vachak.ui`
2. Start app: `adb shell am start -n com.vachak.ui/.MainActivity`
3. Wait 5 seconds for model warm-up
4. Tap mic, speak "नमस्ते" (short greeting)
5. Record logcat output
6. Extract stage timings from logs

#### Warm Start
1. Keep app running after cold start
2. Tap mic, speak "कैसे हो" (short phrase)
3. Record logcat output
4. Extract stage timings from logs

#### Peak RAM
1. During warm test, monitor memory:
   ```bash
   adb shell dumpsys meminfo com.vachak.ui | grep TOTAL
   ```
2. Record peak RAM usage

### Acceptance Criteria

- **ASR Final**: < 1000ms (cold & warm)
- **MT Inference**: < 500ms (cold & warm)
- **TTS Synthesis**: < 1000ms (cold & warm)
- **First Audio**: < 3000ms (cold & warm)
- **Peak RAM**: < 500MB total app memory

### Known Optimizations Applied

1. **09-01**: Deleted duplicate voice MT/TTS calls (~700ms saved)
2. **09-02**: Decoupled tryPlayPcm sleep from pipeline (~650ms saved)
3. **09-03**: Pre-warmed models at app startup (~1300ms saved on first partial)
4. **09-04**: Voice Hindi live, Santali on stop (no repeated MT during speech)
5. **09-05**: Added stage-tagged logging for accurate measurement
6. **BPE Cache**: Increased to 2000 entries (~100ms saved on repeated words)
7. **TTS Singleton**: SherpaOnnxTtsAdapter cached (~100ms saved on cold starts)

### Expected Results (Projected)

Based on applied optimizations:
- Cold start: ~2500ms total (within 3s budget)
- Warm start: ~2000ms total (within 3s budget)
- Peak RAM: ~400MB (within 500MB budget)

**NOTE**: These are projections. Actual device measurements required for verification.

### Debug Dialog

Tap the bug icon (🐛) in LiveScreen header to see:
- ASR error status
- MT live/conversation status
- TTS status
- Current latency measurement
- Device ABI
- Stage-tagged pipeline description

### Logcat Stage Tags

- `[MT:INFERENCE]`: ONNX Runtime MT execution
- `[MT:ORT]`: ONNX native library errors
- `[MT:BPE]`: BPE tokenization (cached)
- `[TTS:SYNTHESIS]`: Sherpa-onnx TTS generation
- `[ASR:VAD]`: VAD silence detection
- `[ASR:TRANSCRIBE]`: Offline ASR fallback
