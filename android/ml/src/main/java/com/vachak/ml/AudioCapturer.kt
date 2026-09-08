package com.vachak.ml

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Live Hindi audio capturer — copied from Uktam-ai/uktam AudioCapturer.kt
 * (https://github.com/Uktam-ai/uktam.git → app/src/main/java/com/example/indicoffline/AudioCapturer.kt)
 * Adapted for Vachak offline stack: 16k mono PCM16, VOICE_RECOGNITION source (better than MIC for speech),
 * continuous coroutine capture with dynamic doubling buffer, synchronized via bufferLock.
 * Returns FloatArray normalized Short/32768.0f for sherpa-onnx OfflineRecognizer (same as IndicAsrEngine.transcribe).
 * Uktam uses AssetManager OfflineNemo model.int8.onnx; Vachak uses filesDir via SherpaAssets null AssetManager — both offline.
 * Sequential only, one capturer at a time (mirrors Uktam's single AudioCapturer instance).
 */
class AudioCapturer {
    private val sampleRate = 16000
    /** 14s cap = 224k samples at 16kHz — prevents unbounded doubling on 2GB RAM (hard limit from AGENTS.md). */
    private val maxBufferSize = sampleRate * 14 // 224000 samples; log warning when capped
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var shortBuffer = ShortArray(16000 * 10) // 10s initial, doubles on overflow (Uktam pattern) but capped at maxBufferSize
    private var bufferSize = 0
    private val bufferLock = Any()
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) {
            android.util.Log.d("Vachak-ASR", "startRecording ignored, already recording")
            return
        }
        synchronized(bufferLock) {
            bufferSize = 0
            if (shortBuffer.size < 16000 * 10) {
                shortBuffer = ShortArray(16000 * 10)
            }
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        android.util.Log.d("Vachak-ASR", "startRecording minBufferSize=$minBufferSize sampleRate=$sampleRate")
        if (minBufferSize <= 0) {
            android.util.Log.e("Vachak-ASR", "getMinBufferSize failed: $minBufferSize")
            return
        }

        // Emulator fix: VOICE_RECOGNITION fails on x86_64 emulator, fallback to MIC
        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk")
        val sources = if (isEmulator) listOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION)
                     else listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        var record: AudioRecord? = null
        var lastErr: Exception? = null
        for (src in sources) {
            record = try {
                android.util.Log.d("Vachak-ASR", "Trying AudioSource $src (emulator=$isEmulator)")
                AudioRecord(src, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize)
            } catch (e: Exception) {
                lastErr = e; android.util.Log.w("Vachak-ASR", "AudioRecord $src failed", e); null
            }
            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) break
            try { record?.release() } catch (_: Exception) {}
            record = null
        }
        if (record == null) {
            android.util.Log.e("Vachak-ASR", "AudioRecord creation failed all sources", lastErr)
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            android.util.Log.e("Vachak-ASR", "AudioRecord not initialized state=${record.state}")
            try { record.release() } catch (_: Exception) {}
            return
        }
        audioRecord = record

        try {
            record.startRecording()
        } catch (e: Exception) {
            android.util.Log.e("Vachak-ASR", "startRecording failed", e)
            try { record.release() } catch (_: Exception) {}
            audioRecord = null
            return
        }
        isRecording = true
        android.util.Log.d("Vachak-ASR", "AudioRecord started, isRecording=true")

        recordingJob = coroutineScope.launch {
            val readBuffer = ShortArray(1024)
            android.util.Log.d("Vachak-ASR", "recordingJob started")
            while (isActive && isRecording) {
                val readResult = record.read(readBuffer, 0, readBuffer.size)
                if (readResult > 0) {
                    synchronized(bufferLock) {
                        if (bufferSize >= maxBufferSize) {
                            android.util.Log.w("Vachak-ASR", "AudioCapturer capped at 14s maxBufferSize=$maxBufferSize samples, dropping $readResult samples (2GB RAM limit, sequential ASR→MT→TTS)")
                        } else {
                            if (bufferSize + readResult > shortBuffer.size) {
                                val desired = bufferSize + readResult
                                // Cap doubling at maxBufferSize to avoid OOM on 2GB device
                                val doubled = shortBuffer.size * 2
                                val newSize = minOf(maxBufferSize, maxOf(doubled, desired))
                                if (newSize > shortBuffer.size) {
                                    if (newSize == maxBufferSize && desired > maxBufferSize) {
                                        android.util.Log.w("Vachak-ASR", "AudioCapturer capped at 14s ($maxBufferSize samples, ${maxBufferSize / sampleRate}s), truncating to max (2GB limit)")
                                    }
                                    val newBuffer = ShortArray(newSize)
                                    System.arraycopy(shortBuffer, 0, newBuffer, 0, bufferSize)
                                    shortBuffer = newBuffer
                                }
                            }
                            val toCopy = minOf(readResult, maxBufferSize - bufferSize)
                            System.arraycopy(readBuffer, 0, shortBuffer, bufferSize, toCopy)
                            bufferSize += toCopy
                            if (toCopy < readResult) {
                                android.util.Log.w("Vachak-ASR", "AudioCapturer capped: copied $toCopy/${readResult} samples, bufferSize=$bufferSize/$maxBufferSize (14s limit)")
                            } else {
                                // no truncation
                            }
                        }
                    }
                    if (bufferSize % 16000 < 1024) {
                        android.util.Log.d("Vachak-VAD", "captured samples=$bufferSize")
                    }
                } else if (readResult == 0) {
                    kotlinx.coroutines.delay(10)
                } else if (readResult < 0) {
                    android.util.Log.e("Vachak-ASR", "AudioRecord read error $readResult, stopping")
                    break
                }
            }
            android.util.Log.d("Vachak-ASR", "recordingJob ended isActive=$isActive isRecording=$isRecording bufferSize=$bufferSize")
        }
    }

    suspend fun stopAndGetFloatArray(): FloatArray = withContext(Dispatchers.IO) {
        android.util.Log.d("Vachak-ASR", "stopAndGetFloatArray isRecording=$isRecording bufferSize=$bufferSize")
        isRecording = false
        val rec = audioRecord
        try {
            if (rec?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                rec.stop()
                android.util.Log.d("Vachak-ASR", "AudioRecord.stop() called to unblock read")
            }
        } catch (e: Exception) {
            android.util.Log.e("Vachak-ASR", "stop failed", e)
        }
        try {
            kotlinx.coroutines.withTimeout(1500) { recordingJob?.cancelAndJoin() }
        } catch (e: Exception) {
            android.util.Log.w("Vachak-ASR", "recordingJob cancel timeout: ${e.message}")
            recordingJob?.cancel()
        }
        recordingJob = null
        rec?.apply { try { release() } catch (_: Exception) {} }
        audioRecord = null
        android.util.Log.d("Vachak-ASR", "stopAndGetFloatArray returning ${bufferSize} samples")
        val floatArray = synchronized(bufferLock) {
            val arr = FloatArray(bufferSize)
            for (i in 0 until bufferSize) { arr[i] = shortBuffer[i] / 32768.0f }
            arr
        }
        return@withContext floatArray
    }

    /** For Vachak's SherpaAsrAdapter which expects ShortArray PCM16 (VAD-gated) — converts FloatArray back or returns raw Short copy */
    suspend fun stopAndGetShortArray(): ShortArray = withContext(Dispatchers.IO) {
        android.util.Log.d("Vachak-ASR", "stopAndGetShortArray isRecording=$isRecording bufferSize=$bufferSize")
        isRecording = false
        val rec = audioRecord
        try {
            if (rec?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                rec.stop()
                android.util.Log.d("Vachak-ASR", "AudioRecord.stop() called to unblock read, state=${rec.recordingState}")
            } else {
                try { rec?.stop() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.e("Vachak-ASR", "stop failed", e)
        }
        try {
            kotlinx.coroutines.withTimeout(1500) { recordingJob?.cancelAndJoin() }
            android.util.Log.d("Vachak-ASR", "recordingJob cancelled cleanly")
        } catch (e: Exception) {
            android.util.Log.w("Vachak-ASR", "recordingJob cancel timeout: ${e.message}")
            recordingJob?.cancel()
            kotlinx.coroutines.delay(50)
        }
        recordingJob = null
        rec?.apply { try { release() } catch (_: Exception) {} }
        audioRecord = null
        android.util.Log.d("Vachak-ASR", "stopAndGetShortArray returning ${bufferSize} samples")
        return@withContext synchronized(bufferLock) { shortBuffer.copyOf(bufferSize) }
    }

    fun release() {
        try {
            isRecording = false
            try { audioRecord?.stop() } catch (_: Exception) {}
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null
            recordingJob?.cancel()
            recordingJob = null
            // NEVER cancel coroutineScope here — your log proves why:
            // LiveViewModel.forceReleaseMic() ran at app start (20:43:16),
            // which cancelled the scope, so the later startRecording()'s
            // recordingJob NEVER ran (no "recordingJob started" line) and
            // 9s of mic captured 0 samples. Scope dies only in close().
            android.util.Log.d("Vachak-ASR", "release() done (scope kept alive)")
        } catch (_: Exception) {}
    }

    /** End-of-life only (ViewModel.onCleared). After this the capturer is dead. */
    fun close() {
        release()
        try { coroutineScope.cancel() } catch (_: Exception) {}
    }

    fun isRecording(): Boolean = isRecording

    /** Snapshot for streaming partials while still recording — does NOT stop. Used by LiveScreen partial loop (offline-telugu Path A). */
    fun snapshotShortArray(): ShortArray = synchronized(bufferLock) { shortBuffer.copyOf(bufferSize) }

    fun snapshotFloatArray(): FloatArray = synchronized(bufferLock) {
        val arr = FloatArray(bufferSize)
        for (i in 0 until bufferSize) arr[i] = shortBuffer[i] / 32768.0f
        arr
    }

    fun currentSize(): Int = synchronized(bufferLock) { bufferSize }
}
