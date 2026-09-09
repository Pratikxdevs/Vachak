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
/**
 * OS-level microphone audit snapshot (see [AudioCapturer.auditMic]).
 * Any one of these makes the OS hand us digital zeros with permission granted.
 */
data class MicAuditResult(
    val osMuted: Boolean,
    val unmutedByUs: Boolean,
    /** Audio sources of OTHER apps currently recording (empty = mic is ours alone). */
    val otherRecorders: List<String>,
    val inCall: Boolean,
    val btScoOn: Boolean,
    val inputDevices: List<String>
) {
    fun suspicious(): Boolean = osMuted || otherRecorders.isNotEmpty() || inCall || btScoOn

    /**
     * User-facing culprit, or null when the OS path looks clean (caller falls
     * through to the generic hardware checklist). Never blank-guesses.
     */
    fun messageForUser(sampleCount: Int, peakDb: String): String? = when {
        osMuted -> "System microphone is MUTED ($sampleCount samples, peak $peakDb). " +
            "Unmute it (Quick Settings → Microphone, or the tablet's mute switch) and retry."
        otherRecorders.isNotEmpty() -> "Another app is recording right now (${otherRecorders.take(2).joinToString()}), " +
            "so this app gets silence ($sampleCount samples). Close it (voice assistant? recorder?) and retry."
        inCall -> "Tablet is in a voice/video call, which owns the microphone " +
            "($sampleCount samples of silence). End the call and retry."
        btScoOn -> "Bluetooth audio is active but delivered silence ($sampleCount samples, peak $peakDb). " +
            "Disconnect the earpiece (or speak into it, not the tablet) and retry."
        else -> null
    }
}

class AudioCapturer {
    private val sampleRate = 16000
    /** 14s cap = 224k samples at 16kHz — prevents unbounded doubling on 2GB RAM (hard limit from AGENTS.md). */
    private val maxBufferSize = sampleRate * 14 // 224000 samples; log warning when capped
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    /** Rolling RMS of the most recent read chunk — drives the UI mic meter. */
    @Volatile var lastRms: Float = 0f
        private set
    /** Peak RMS since startRecording — distinguishes "spoke quietly" from "mic dead". */
    @Volatile var peakRms: Float = 0f
        private set
    /** AudioSource that survived validation (or -1). */
    @Volatile var audioSourceUsed: Int = -1
        private set
    /** True when every source probed digital silence — mic path suspect, not the speaker. */
    @Volatile var silentStart: Boolean = false
        private set
    /** OS-level mic audit from the last start (mute / other recorders / call / route). */
    @Volatile var lastMicAudit: MicAuditResult? = null
        private set

    /**
     * OS-level microphone audit. Permission can be granted while the OS still
     * hands us digital zeros: system mic mute, another app recording, an
     * active call, or a dead Bluetooth route. All are detectable without any
     * permission beyond what the manifest already holds — so detect them and
     * say WHICH one instead of "no speech detected".
     *
     * Also attempts to release an OS software mute (user pressed mic = intent
     * to record). Safe to call repeatedly; pure reads + one conditional write.
     */
    fun auditMic(context: android.content.Context): MicAuditResult {
        val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        var osMuted = false
        try {
            osMuted = am.isMicrophoneMute
        } catch (e: Exception) {
            android.util.Log.w("Vachak-ASR", "mic audit: isMicrophoneMute unreadable: ${e.message}")
        }
        var unmutedByUs = false
        if (osMuted) {
            try {
                am.isMicrophoneMute = false
                osMuted = am.isMicrophoneMute
                unmutedByUs = !osMuted
                android.util.Log.w("Vachak-ASR", "mic audit: OS mic was muted; unmute attempt -> muted=$osMuted")
            } catch (e: Exception) {
                android.util.Log.w("Vachak-ASR", "mic audit: unmute failed (needs MODIFY_AUDIO_SETTINGS): ${e.message}")
            }
        }
        var others: List<String> = emptyList()
        // API 29+: without the guard this is a NoSuchMethodError (not an
        // Exception — uncatchable by `catch (e: Exception)`) on Android 9,
        // our minSdk. Guard + belt-and-braces Throwable catch.
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                others = am.activeRecordingConfigurations.map { "src=${it.audioSource}" }
            } catch (t: Throwable) {
                android.util.Log.w("Vachak-ASR", "mic audit: active recorders unreadable: ${t.message}")
            }
        }
        var inCall = false
        try {
            inCall = am.mode == android.media.AudioManager.MODE_IN_CALL ||
                am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            android.util.Log.w("Vachak-ASR", "mic audit: audio mode unreadable: ${e.message}")
        }
        var btSco = false
        try {
            btSco = am.isBluetoothScoOn
        } catch (e: Exception) {
            android.util.Log.w("Vachak-ASR", "mic audit: BT SCO state unreadable: ${e.message}")
        }
        var inputs: List<String> = emptyList()
        try {
            inputs = am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                .map { "${it.type}:${it.productName}" }
        } catch (t: Throwable) {
            android.util.Log.w("Vachak-ASR", "mic audit: input devices unreadable: ${t.message}")
        }
        val result = MicAuditResult(osMuted, unmutedByUs, others, inCall, btSco, inputs)
        lastMicAudit = result
        if (result.suspicious()) {
            android.util.Log.w("Vachak-ASR", "mic audit SUSPICIOUS: $result")
        } else {
            android.util.Log.d("Vachak-ASR", "mic audit clean: $result")
        }
        return result
    }
    private var shortBuffer = ShortArray(16000 * 10) // 10s initial, doubles on overflow (Uktam pattern) but capped at maxBufferSize
    private var bufferSize = 0
    private val bufferLock = Any()
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Preferred entry: audits the OS mic path first (mute / other recorders /
     * call / route — the digital-silence culprits permission can't catch),
     * then captures. Audit failures never block capture.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(context: android.content.Context) {
        try {
            auditMic(context)
        } catch (e: Exception) {
            android.util.Log.w("Vachak-ASR", "mic audit threw, capturing anyway: ${e.message}")
        }
        startRecording()
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {        if (isRecording) {
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
        // Source validation: a source can INITIALIZE yet stream digital silence
        // (unrouted OEM source, muted route). Probe ~400ms of real signal per
        // source and keep the first live one; a dead source must never win by
        // opening successfully. Probe audio is KEPT (appended to the buffer).
        lastRms = 0f
        peakRms = 0f
        audioSourceUsed = -1
        silentStart = false
        var record: AudioRecord? = null
        var silentRecord: AudioRecord? = null
        var silentSrc = -1
        var silentRms = -1f
        for (src in sources) {
            val candidate = try {
                android.util.Log.d("Vachak-ASR", "Trying AudioSource $src (emulator=$isEmulator)")
                AudioRecord(src, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize)
            } catch (e: Exception) {
                android.util.Log.w("Vachak-ASR", "AudioRecord $src failed", e); null
            }
            if (candidate == null || candidate.state != AudioRecord.STATE_INITIALIZED) {
                try { candidate?.release() } catch (_: Exception) { /* best-effort cleanup */ }
                continue
            }
            try {
                candidate.startRecording()
            } catch (e: Exception) {
                android.util.Log.w("Vachak-ASR", "AudioRecord $src startRecording failed", e)
                try { candidate.release() } catch (_: Exception) { /* best-effort cleanup */ }
                continue
            }
            val probeRms = probeSignal(candidate)
            android.util.Log.d("Vachak-ASR", "AudioSource $src probe rms=$probeRms (${VachakAudio.rmsToDb(probeRms)})")
            if (probeRms >= VachakAudio.DIGITAL_SILENCE_RMS) {
                try { silentRecord?.release() } catch (_: Exception) { /* best-effort cleanup */ }
                silentRecord = null
                record = candidate
                audioSourceUsed = src
                silentStart = false
                break
            }
            android.util.Log.w("Vachak-ASR", "AudioSource $src streams digital silence — trying next source")
            if (probeRms > silentRms) {
                try { silentRecord?.release() } catch (_: Exception) { /* best-effort cleanup */ }
                silentRecord = candidate
                silentSrc = src
                silentRms = probeRms
            } else {
                try { candidate.release() } catch (_: Exception) { /* best-effort cleanup */ }
            }
            record = null
        }
        if (record == null) {
            // Every source silent (or failed): keep the loudest so capture still
            // runs, but flag it — the stop path reports MIC instead of VAD.
            if (silentRecord != null) {
                android.util.Log.e("Vachak-ASR", "ALL sources digital silence (best=$silentSrc rms=$silentRms) — keeping it, flagging silentStart")
                record = silentRecord
                audioSourceUsed = silentSrc
                silentStart = true
            } else {
                android.util.Log.e("Vachak-ASR", "AudioRecord creation failed all sources")
                return
            }
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            android.util.Log.e("Vachak-ASR", "AudioRecord not initialized state=${record.state}")
            try { record.release() } catch (_: Exception) { /* best-effort cleanup */ }
            return
        }
        audioRecord = record
        isRecording = true
        android.util.Log.d("Vachak-ASR", "AudioRecord started src=$audioSourceUsed silentStart=$silentStart, isRecording=true")

        recordingJob = coroutineScope.launch {
            val readBuffer = ShortArray(1024)
            android.util.Log.d("Vachak-ASR", "recordingJob started")
            while (isActive && isRecording) {
                val readResult = record.read(readBuffer, 0, readBuffer.size)
                if (readResult > 0) {
                    // Rolling level for the UI mic meter + peak for stop-path diagnosis.
                    var sum = 0.0
                    for (i in 0 until readResult) {
                        val v = readBuffer[i] / 32768.0
                        sum += v * v
                    }
                    val chunkRms = kotlin.math.sqrt(sum / readResult).toFloat()
                    lastRms = chunkRms
                    if (chunkRms > peakRms) peakRms = chunkRms
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

    /**
     * Startup probe: blocking-read up to [VachakAudio.SOURCE_PROBE_SAMPLES] (~400ms)
     * with a deadline, APPEND it to the capture buffer (no audio lost), and
     * return its RMS. Lets the caller reject sources that open fine but stream
     * digital silence. Runs on the caller's IO thread.
     */
    private fun probeSignal(record: AudioRecord): Float {
        val want = VachakAudio.SOURCE_PROBE_SAMPLES
        val tmp = ShortArray(1024)
        var got = 0
        var sum = 0.0
        val deadlineMs = android.os.SystemClock.elapsedRealtime() + 900
        while (got < want && android.os.SystemClock.elapsedRealtime() < deadlineMs) {
            val n = try {
                record.read(tmp, 0, minOf(tmp.size, want - got), AudioRecord.READ_BLOCKING)
            } catch (e: Exception) {
                android.util.Log.w("Vachak-ASR", "probe read failed: ${e.message}")
                break
            }
            if (n <= 0) break
            for (i in 0 until n) {
                val v = tmp[i] / 32768.0
                sum += v * v
            }
            synchronized(bufferLock) {
                if (bufferSize < maxBufferSize) {
                    val toCopy = minOf(n, maxBufferSize - bufferSize)
                    System.arraycopy(tmp, 0, shortBuffer, bufferSize, toCopy)
                    bufferSize += toCopy
                }
            }
            got += n
        }
        val rms = if (got > 0) kotlin.math.sqrt(sum / got).toFloat() else 0f
        lastRms = rms
        if (rms > peakRms) peakRms = rms
        return rms
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
