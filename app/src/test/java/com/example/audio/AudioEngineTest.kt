package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioEngineTest {

    @Test
    fun computePcmDurationMs_does_not_truncate_long_tts_audio_at_4_seconds() {
        // 12 seconds of 24 kHz 16-bit mono PCM = 24000 * 2 * 12 = 576,000 bytes
        val twelveSecondsBytes = 24_000 * 2 * 12
        val duration = computePcmDurationMs(twelveSecondsBytes, sampleRate = 24_000)
        assertEquals(12_000L, duration)

        // Very short clip still has safe minimum floor
        assertTrue(computePcmDurationMs(100, sampleRate = 24_000) >= 150L)
    }

    @Test
    fun maxRecordingBytes_caps_16khz_mono_16bit_pcm_at_30_seconds() {
        assertEquals(960_000, maxRecordingBytes(sampleRate = 16_000, maxDurationMs = 30_000L))
        assertEquals(320_000, maxRecordingBytes(sampleRate = 16_000, maxDurationMs = 10_000L))
    }

    @Test
    fun wrapPcmAsWav_writes_valid_riff_header_and_payload() {
        val pcm = ByteArray(100) { it.toByte() }
        val wav = wrapPcmAsWav(pcm, sampleRate = 16_000, channels = 1, bitsPerSample = 16)
        assertEquals(144, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(136, buf.getInt(4))
        assertEquals(16_000, buf.getInt(24))
        assertEquals(100, buf.getInt(40))
    }
}
