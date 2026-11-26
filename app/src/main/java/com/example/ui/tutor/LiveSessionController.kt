package com.example.ui.tutor

import android.annotation.SuppressLint
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private var currentToken: Long = 0L
    private var isShutDown: Boolean = false
    private val pendingCloseJobs = mutableSetOf<Job>()

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
        val myToken = synchronized(sessionLock) {
            if (isShutDown || _isConnecting.value || _isActive.value) return
            currentToken += 1L
            _error.value = null
            _isConnecting.value = true
            currentToken
        }

        val job = scope.launch {
            var opened: CloseableLiveSession? = null
            try {
                val session = connect()
                opened = session
                val adopted = synchronized(sessionLock) {
                    if (currentToken == myToken && !isShutDown) {
                        activeSession = session
                        true
                    } else {
                        false
                    }
                }
                if (!adopted) {
                    closeQuietly(session)
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                session.startAudioConversation()
                synchronized(sessionLock) {
                    if (currentToken == myToken && activeSession === session) {
                        _isActive.value = true
                    }
                }
            } catch (e: CancellationException) {
                val toClose = claimSessionIfStillActive(opened)
                if (toClose != null) {
                    closeQuietly(toClose)
                }
                synchronized(sessionLock) {
                    if (currentToken == myToken) {
                        _isActive.value = false
                    }
                }
                throw e
            } catch (e: Exception) {
                val toClose = claimSessionIfStillActive(opened)
                if (toClose != null) {
                    closeQuietly(toClose)
                }
                synchronized(sessionLock) {
                    if (currentToken == myToken) {
                        _error.value = e.localizedMessage ?: "Unable to connect Live Voice session."
                        _isActive.value = false
                    }
                }
            } finally {
                synchronized(sessionLock) {
                    if (currentToken == myToken) {
                        _isConnecting.value = false
                    }
                }
            }
        }
        synchronized(sessionLock) {
            if (currentToken == myToken) {
                connectJob = job
            } else {
                job.cancel()
            }
        }
    }

    fun stop() {
        val (jobToCancel, sessionToClose) = synchronized(sessionLock) {
            currentToken += 1L
            val j = connectJob
            connectJob = null
            val s = activeSession
            activeSession = null
            _isConnecting.value = false
            _isActive.value = false
            j to s
        }
        jobToCancel?.cancel()
        if (sessionToClose != null) {
            launchTrackedClose(sessionToClose)
        }
    }

    fun shutdown() {
        val (jobToCancel, sessionToClose, existingCloseJobs) = synchronized(sessionLock) {
            isShutDown = true
            currentToken += 1L
            val j = connectJob
            connectJob = null
            val s = activeSession
            activeSession = null
            _isConnecting.value = false
            _isActive.value = false
            Triple(j, s, pendingCloseJobs.toList())
        }
        jobToCancel?.cancel()

        val allCloseJobs = buildList {
            addAll(existingCloseJobs)
            if (sessionToClose != null) {
                add(launchTrackedClose(sessionToClose))
            }
        }

        if (allCloseJobs.isEmpty()) {
            scope.cancel()
        } else {
            scope.launch(start = CoroutineStart.ATOMIC) {
                try {
                    allCloseJobs.forEach { it.join() }
                } finally {
                    scope.cancel()
                }
            }
        }
    }

    private fun claimSessionIfStillActive(expected: CloseableLiveSession?): CloseableLiveSession? {
        if (expected == null) return null
        return synchronized(sessionLock) {
            if (activeSession === expected) {
                activeSession = null
                expected
            } else {
                null
            }
        }
    }

    private fun launchTrackedClose(session: CloseableLiveSession): Job {
        val closeJob = scope.launch(start = CoroutineStart.ATOMIC) {
            closeQuietly(session)
        }
        synchronized(sessionLock) {
            pendingCloseJobs.add(closeJob)
        }
        closeJob.invokeOnCompletion {
            synchronized(sessionLock) {
                pendingCloseJobs.remove(closeJob)
            }
        }
        return closeJob
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
