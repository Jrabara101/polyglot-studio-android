package com.example.ui.tutor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private class FakeLiveSession(
    private val closeDelayMs: Long = 20L
) : CloseableLiveSession {
    val started = AtomicInteger(0)
    val stopped = AtomicInteger(0)
    val closed = AtomicInteger(0)

    override suspend fun startAudioConversation() {
        started.incrementAndGet()
    }

    override suspend fun stopAudioConversation() {
        delay(closeDelayMs)
        stopped.incrementAndGet()
    }

    override suspend fun close() {
        delay(closeDelayMs)
        closed.incrementAndGet()
    }
}

class LiveSessionControllerTest {

    @Test
    fun stop_completes_even_when_caller_composition_scope_is_cancelled() = runBlocking {
        val controller = LiveSessionController(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val fake = FakeLiveSession()

        controller.start { fake }
        withTimeout(2000) {
            while (!controller.isActive.value) delay(10)
        }
        assertEquals(1, fake.started.get())
        assertTrue(controller.isActive.value)

        // Simulate leaving TutorHubScreen: a composition scope is cancelled right when stop() is called.
        val compositionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        compositionScope.cancel()

        controller.stop()
        withTimeout(2000) {
            while (fake.closed.get() == 0) delay(10)
        }

        assertFalse(controller.isActive.value)
        assertEquals(1, fake.stopped.get())
        assertEquals(1, fake.closed.get())
        controller.shutdown()
    }

    @Test
    fun shutdown_immediately_after_stop_still_closes_session_before_cancelling_scope() = runBlocking {
        val controller = LiveSessionController(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val fake = FakeLiveSession(closeDelayMs = 40L)

        controller.start { fake }
        withTimeout(2000) {
            while (!controller.isActive.value) delay(10)
        }

        // Mirrors DisposableEffect.onDispose -> stop() followed immediately by ViewModel.onCleared -> shutdown().
        controller.stop()
        controller.shutdown()

        withTimeout(2000) {
            while (fake.closed.get() == 0) delay(10)
        }

        assertEquals(1, fake.stopped.get())
        assertEquals(1, fake.closed.get())
    }

    @Test
    fun rapid_stop_and_restart_does_not_clobber_new_session_or_double_close() = runBlocking {
        val controller = LiveSessionController(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val gate = CompletableDeferred<Unit>()
        val first = FakeLiveSession(closeDelayMs = 20L)
        val second = FakeLiveSession(closeDelayMs = 10L)

        controller.start {
            gate.await()
            first
        }
        delay(20)
        controller.stop()
        gate.complete(Unit)

        controller.start { second }
        withTimeout(2000) {
            while (!controller.isActive.value) delay(10)
        }

        assertTrue("Restarted session must remain active", controller.isActive.value)
        assertFalse("Restarted session must finish connecting cleanly", controller.isConnecting.value)
        assertTrue("Cancelled first session closed at most once", first.closed.get() <= 1)

        controller.shutdown()
        withTimeout(2000) {
            while (second.closed.get() == 0) delay(10)
        }
        assertEquals(1, second.stopped.get())
        assertEquals(1, second.closed.get())
    }
}
