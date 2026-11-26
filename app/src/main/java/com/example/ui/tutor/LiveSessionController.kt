package com.example.ui.tutor

import android.annotation.SuppressLint
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Testable lifecycle boundary around Firebase AI Logic's [LiveSession]. */
interface CloseableLiveSession {
    suspend fun startAudioConversation()
    suspend fun stopAudioConversation()
    suspend fun close()
}

@OptIn(PublicPreviewAPI::class)
class FirebaseLiveSessionAdapter(
    private val delegate: LiveSession
) : CloseableLiveSession {
    @SuppressLint("MissingPermission")
    override suspend fun startAudioConversation() {
        delegate.startAudioConversation()
    }

    override suspend fun stopAudioConversation() {
        delegate.stopAudioConversation()
    }

    override suspend fun close() {
        delegate.close()
    }
}

/**
 * Owns the active Gemini Live Voice session on an independent IO scope so leaving a Composable
 * (switching bottom tabs, navigating back, or rotating) always stops mic capture and closes the
 * WebSocket instead of cancelling cleanup alongside `rememberCoroutineScope()`.
 */
class LiveSessionController(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val sessionLock = Any()
    private var activeSession: CloseableLiveSession? = null
    private var connectJob: Job? = null

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setError(message: String?) {
        _error.value = message
    }

    fun start(connect: suspend () -> CloseableLiveSession) {
        if (_isConnecting.value || _isActive.value) return
        _error.value = null
        _isConnecting.value = true
        connectJob = scope.launch {
            var opened: CloseableLiveSession? = null
            try {
                val session = connect()
                opened = session
                synchronized(sessionLock) {
                    activeSession = session
                }
                session.startAudioConversation()
                _isActive.value = true
            } catch (e: CancellationException) {
                opened?.let { closeQuietly(it) }
                synchronized(sessionLock) {
                    if (activeSession === opened) activeSession = null
                }
                _isActive.value = false
                throw e
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Unable to connect Live Voice session."
                opened?.let { closeQuietly(it) }
                synchronized(sessionLock) {
                    if (activeSession === opened) activeSession = null
                }
                _isActive.value = false
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        val sessionToClose = synchronized(sessionLock) {
            val s = activeSession
            activeSession = null
            s
        }
        _isConnecting.value = false
        _isActive.value = false
        if (sessionToClose != null) {
            scope.launch {
                closeQuietly(sessionToClose)
            }
        }
    }

    fun shutdown() {
        connectJob?.cancel()
        connectJob = null
        val sessionToClose = synchronized(sessionLock) {
            val s = activeSession
            activeSession = null
            s
        }
        _isConnecting.value = false
        _isActive.value = false
        if (sessionToClose != null) {
            scope.launch {
                try {
                    closeQuietly(sessionToClose)
                } finally {
                    scope.cancel()
                }
            }
        } else {
            scope.cancel()
        }
    }

    private suspend fun closeQuietly(session: CloseableLiveSession) = withContext(NonCancellable) {
        try {
            session.stopAudioConversation()
        } catch (_: Exception) {
        }
        try {
            session.close()
        } catch (_: Exception) {
        }
    }
}
