package com.example.ui.tutor

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

private class FakeLiveSession : CloseableLiveSession {
    var started = 0
    var stopped = 0
    var closed = 0

    override suspend fun startAudioConversation() {
        started++
    }

    override suspend fun stopAudioConversation() {
        delay(20)
        stopped++
    }

    override suspend fun close() {
        delay(20)
        closed++
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
        assertEquals(1, fake.started)
        assertTrue(controller.isActive.value)

        // Simulate leaving TutorHubScreen: a composition scope is cancelled right when stop() is called.
        val compositionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        compositionScope.cancel()

        controller.stop()
        withTimeout(2000) {
            while (fake.closed == 0) delay(10)
        }

        assertFalse(controller.isActive.value)
        assertEquals(1, fake.stopped)
        assertEquals(1, fake.closed)
        controller.shutdown()
    }
}
