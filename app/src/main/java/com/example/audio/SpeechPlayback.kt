package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private const val TAG = "SpeechPlayback"

/** Plays the pre-generated pronunciation clips bundled in assets/audio. */
class AssetClipPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var current: MediaPlayer? = null

    /** Plays [assetPath] to the end. Returns false if the clip could not be played. */
    suspend fun play(assetPath: String, slow: Boolean): Boolean = withContext(Dispatchers.Main) {
        stop()
        val player = MediaPlayer()
        current = player
        try {
            appContext.assets.openFd(assetPath).use { afd ->
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.prepare()
            if (slow) {
                player.playbackParams = player.playbackParams.setSpeed(SLOW_SPEED)
            }
            suspendCancellableCoroutine { cont ->
                player.setOnCompletionListener { if (cont.isActive) cont.resume(true) }
                player.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Clip error $what/$extra for $assetPath")
                    if (cont.isActive) cont.resume(false)
                    true
                }
                cont.invokeOnCancellation { stop() }
                player.start()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not play $assetPath", e)
            false
        } finally {
            if (current === player) stop()
        }
    }

    fun stop() {
        val player = current ?: return
        current = null
        try {
            player.stop()
        } catch (_: Exception) {
        }
        player.release()
    }

    private companion object {
        const val SLOW_SPEED = 0.75f
    }
}

/** Offline last-resort voice using the phone's own text-to-speech engine. */
class DeviceSpeech(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    /** Speaks [text] and waits until it finishes. Returns false if the language isn't available. */
    suspend fun speak(text: String, locale: Locale, slow: Boolean): Boolean {
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } ?: false
        if (!ok) return false
        val langResult = tts.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Device TTS has no voice for $locale")
            return false
        }
        tts.setSpeechRate(if (slow) 0.7f else 0.95f)
        val utteranceId = UUID.randomUUID().toString()
        return suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(true)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(false)
                }
            })
            cont.invokeOnCancellation { tts.stop() }
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 3000L
    }
}
