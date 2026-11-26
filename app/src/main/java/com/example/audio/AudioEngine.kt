package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

private const val TAG = "AudioEngine"

internal const val MAX_RECORDING_DURATION_MS = 30_000L

internal fun computePcmDurationMs(
    byteCount: Int,
    sampleRate: Int = 24_000,
    channels: Int = 1,
    bytesPerSample: Int = 2
): Long {
    if (byteCount <= 0 || sampleRate <= 0 || channels <= 0 || bytesPerSample <= 0) return 150L
    val bytesPerSecond = sampleRate.toLong() * channels.toLong() * bytesPerSample.toLong()
    return ((byteCount.toLong() * 1000L) / bytesPerSecond).coerceAtLeast(150L)
}

internal fun maxRecordingBytes(
    sampleRate: Int = 16_000,
    maxDurationMs: Long = MAX_RECORDING_DURATION_MS,
    channels: Int = 1,
    bytesPerSample: Int = 2
): Int {
    val bytesPerSecond = sampleRate.toLong() * channels.toLong() * bytesPerSample.toLong()
    return ((bytesPerSecond * maxDurationMs.coerceAtLeast(1000L)) / 1000L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

internal fun wrapPcmAsWav(
    pcmData: ByteArray,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int
): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = (channels * bitsPerSample / 8).toShort()
    val dataSize = pcmData.size
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(36 + dataSize)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16)
    header.putShort(1.toShort()) // PCM
    header.putShort(channels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort(blockAlign)
    header.putShort(bitsPerSample.toShort())
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataSize)
    return header.array() + pcmData
}

class PcmAudioPlayer {
    private val trackLock = Any()

    @Volatile
    private var currentTrack: AudioTrack? = null

    suspend fun playPcm24k(pcmBytes: ByteArray) = withContext(Dispatchers.IO) {
        if (pcmBytes.isEmpty()) return@withContext
        currentCoroutineContext().ensureActive()
        stop()
        currentCoroutineContext().ensureActive()

        val sampleRate = 24_000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, minOf(pcmBytes.size, 32_768)))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack failed to initialize (state=${track.state})")
            safeRelease(track)
            return@withContext
        }

        val ctx = currentCoroutineContext()
        try {
            synchronized(trackLock) {
                ctx.ensureActive()
                currentTrack = track
            }
            val startTimeMs = System.currentTimeMillis()
            track.play()
            var offset = 0
            var writeFailed = false
            val chunkSize = 4096
            while (offset < pcmBytes.size && currentCoroutineContext().isActive) {
                val len = minOf(chunkSize, pcmBytes.size - offset)
                val written = track.write(pcmBytes, offset, len)
                if (written <= 0) {
                    writeFailed = true
                    break
                }
                offset += written
            }
            if (!writeFailed && currentCoroutineContext().isActive && currentTrack === track) {
                val expectedDurationMs = computePcmDurationMs(offset, sampleRate)
                val elapsedMs = System.currentTimeMillis() - startTimeMs
                val remainingMs = (expectedDurationMs - elapsedMs + 150L).coerceAtLeast(150L)
                delay(remainingMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback failed", e)
        } finally {
            synchronized(trackLock) {
                if (currentTrack === track) {
                    currentTrack = null
                }
            }
            // Sole owner of release(): safe after write() has exited on this IO coroutine.
            safeRelease(track)
        }
    }

    /** Unblocks any active write() without calling release() concurrently from another thread. */
    fun stop() {
        val toStop = synchronized(trackLock) {
            val t = currentTrack
            currentTrack = null
            t
        } ?: return
        try {
            if (toStop.playState == AudioTrack.PLAYSTATE_PLAYING) {
                toStop.pause()
                toStop.flush()
                toStop.stop()
            }
        } catch (_: Exception) {
        }
    }

    private fun safeRelease(track: AudioTrack) {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
        } catch (_: Exception) {
        }
        try {
            track.release()
        } catch (_: Exception) {
        }
    }
}

class WavAudioRecorder {
    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val recordLock = Any()
    private val sessionToken = AtomicLong(0L)

    private var recordingJob: Job? = null

    @Volatile
    private var activeRecord: AudioRecord? = null

    @Volatile
    private var pcmStream: ByteArrayOutputStream? = null

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        maxDurationMs: Long = MAX_RECORDING_DURATION_MS,
        onAutoStop: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        if (_isRecording.value) return true
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(3200)

        val previousJob = synchronized(recordLock) {
            val prev = recordingJob
            val prevRecord = activeRecord
            activeRecord = null
            if (prevRecord != null) {
                try {
                    if (prevRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        prevRecord.stop()
                    }
                } catch (_: Exception) {
                }
            }
            prev?.cancel()
            prev
        }

        // If no previous capture is unwinding, probe AudioRecord synchronously so callers get
        // immediate false when the mic hardware cannot initialize.
        val prebuiltRecord: AudioRecord? = if (previousJob == null || previousJob.isCompleted) {
            createInitializedRecord(minBuf) ?: return false
        } else {
            null
        }

        val myToken = sessionToken.incrementAndGet()
        val maxBytes = maxRecordingBytes(sampleRate, maxDurationMs)
        val out = ByteArrayOutputStream()
        pcmStream = out
        _isRecording.value = true

        val job = scope.launch(Dispatchers.IO) {
            var record: AudioRecord? = prebuiltRecord
            var hitCap = false
            var captureError: String? = null
            try {
                // Wait for any prior recording job to finish releasing the mic before opening a new one.
                previousJob?.join()
                if (sessionToken.get() != myToken || !isActive) return@launch

                if (record == null) {
                    record = createInitializedRecord(minBuf)
                    if (record == null) {
                        captureError = "Microphone unavailable right now. Close other apps using the mic and try again."
                        return@launch
                    }
                }

                synchronized(recordLock) {
                    if (sessionToken.get() != myToken) return@launch
                    activeRecord = record
                }

                record.startRecording()
                val buffer = ByteArray(minBuf)
                while (isActive && _isRecording.value && sessionToken.get() == myToken) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val remaining = maxBytes - out.size()
                        if (remaining <= 0) {
                            hitCap = true
                            break
                        }
                        val toWrite = minOf(read, remaining)
                        out.write(buffer, 0, toWrite)
                        _amplitude.value = computeNormalizedAmplitude(buffer, toWrite)
                        if (out.size() >= maxBytes) {
                            hitCap = true
                            break
                        }
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord.read returned error code $read")
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during AudioRecord capture", e)
                captureError = "Microphone capture failed. Please try again."
            } finally {
                synchronized(recordLock) {
                    if (activeRecord === record) {
                        activeRecord = null
                    }
                }
                if (record != null) {
                    try {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    } catch (_: Exception) {
                    }
                    try {
                        record.release()
                    } catch (_: Exception) {
                    }
                }
                val isStillOwner = sessionToken.get() == myToken
                if (isStillOwner) {
                    _amplitude.value = 0f
                    _isRecording.value = false
                }
                if (isStillOwner && captureError != null && onError != null) {
                    withContext(Dispatchers.Main.immediate) {
                        onError(captureError)
                    }
                } else if (isStillOwner && hitCap && onAutoStop != null) {
                    withContext(Dispatchers.Main.immediate) {
                        onAutoStop()
                    }
                }
            }
        }
        synchronized(recordLock) {
            if (sessionToken.get() == myToken) {
                recordingJob = job
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun createInitializedRecord(minBuf: Int): AudioRecord? {
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBuf * 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord creation failed", e)
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            try {
                record.release()
            } catch (_: Exception) {
            }
            return null
        }
        return record
    }

    suspend fun stopAndGetWav(): ByteArray {
        _isRecording.value = false
        val (job, record) = synchronized(recordLock) {
            recordingJob to activeRecord
        }
        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: Exception) {
            }
        }
        job?.join()
        synchronized(recordLock) {
            if (recordingJob === job) recordingJob = null
        }
        val pcmBytes = pcmStream?.toByteArray() ?: ByteArray(0)
        pcmStream = null
        return if (pcmBytes.isEmpty()) ByteArray(0) else wrapPcmAsWav(pcmBytes, sampleRate, 1, 16)
    }

    fun cancel() {
        sessionToken.incrementAndGet()
        _isRecording.value = false
        _amplitude.value = 0f
        val (job, record) = synchronized(recordLock) {
            val j = recordingJob
            val r = activeRecord
            recordingJob = null
            activeRecord = null
            j to r
        }
        // Unblock any blocking AudioRecord.read() while leaving release() solely to the IO finally block.
        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: Exception) {
            }
        }
        job?.cancel()
        pcmStream = null
    }

    private fun computeNormalizedAmplitude(bytes: ByteArray, length: Int): Float {
        var maxSample = 0
        var i = 0
        while (i + 1 < length) {
            val low = bytes[i].toInt() and 0xFF
            val high = bytes[i + 1].toInt()
            val sample = abs((high shl 8) or low)
            if (sample > maxSample) maxSample = sample
            i += 2
        }
        return (maxSample / 32768f).coerceIn(0f, 1f)
    }
}
