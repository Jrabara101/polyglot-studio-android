package com.example.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirebaseAiFallbackTest {

    @Test
    fun falls_back_to_second_model_when_first_fails_with_exception() = runTest {
        val tried = mutableListOf<String>()
        val models = listOf("gemini-3.5-flash-lite" to "fast", "gemini-3.8-flash" to "backup")

        val result = runWithModelFallback(
            label = "TestOp",
            models = models,
            timeoutMs = 5_000L
        ) { model ->
            tried += model
            if (model == "fast") error("503 overloaded")
            "ok-from-$model"
        }

        assertEquals("ok-from-backup", result)
        assertEquals(listOf("fast", "backup"), tried)
    }

    @Test
    fun falls_back_to_second_model_when_first_times_out() = runTest {
        val tried = mutableListOf<String>()
        val models = listOf("gemini-3.5-flash-lite" to "fast", "gemini-3.8-flash" to "backup")

        val result = runWithModelFallback(
            label = "TimeoutOp",
            models = models,
            timeoutMs = 100L
        ) { model ->
            tried += model
            if (model == "fast") delay(5_000L)
            "recovered"
        }

        assertEquals("recovered", result)
        assertEquals(listOf("fast", "backup"), tried)
    }

    @Test
    fun throws_friendly_error_when_all_models_fail() = runTest {
        val models = listOf("m1" to 1, "m2" to 2)
        try {
            runWithModelFallback(
                label = "Voice evaluation",
                models = models,
                timeoutMs = 1_000L
            ) {
                error("unavailable")
            }
            fail("Expected exception when all models fail")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Expected friendly message mentioning label, got: ${e.message}",
                e.message.orEmpty().contains("Voice evaluation")
            )
        }
    }

    @Test
    fun rethrows_external_cancellation_without_trying_backup() = runTest {
        val tried = mutableListOf<String>()
        val models = listOf("m1" to 1, "m2" to 2)
        try {
            runWithModelFallback(
                label = "CancelledOp",
                models = models,
                timeoutMs = 5_000L
            ) { model ->
                tried += model.toString()
                throw CancellationException("caller cancelled")
            }
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            assertEquals(listOf("1"), tried)
        }
    }

    @Test
    fun default_fallback_order_tries_fast_lite_before_standard_flash() {
        assertEquals(
            listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"),
            FAST_FALLBACK_MODELS
        )
    }

    @Test
    fun rethrows_outer_timeout_without_trying_backup_model() = runTest {
        val tried = mutableListOf<String>()
        val models = listOf("m1" to 1, "m2" to 2)
        try {
            withTimeout(50L) {
                runWithModelFallback(
                    label = "OuterTimeoutOp",
                    models = models,
                    timeoutMs = 5_000L
                ) { model ->
                    tried += model.toString()
                    delay(1_000L)
                    "never"
                }
            }
            fail("Expected outer TimeoutCancellationException")
        } catch (_: TimeoutCancellationException) {
            assertEquals(listOf("1"), tried)
        }
    }

    @Test
    fun streamChatWithModelFallback_times_out_before_first_chunk_and_preserves_history() = runTest {
        val transcript = mutableListOf<String>()
        var preferred = 0
        val calls = mutableListOf<Pair<String, List<String>>>()

        // Turn 1 succeeds on primary model
        val turn1 = streamChatWithModelFallback<Unit>(
            modelNames = FAST_FALLBACK_MODELS,
            preferredIndex = preferred,
            onPreferredIndexChanged = { preferred = it },
            firstChunkTimeoutMs = 100L,
            openStream = { model ->
                calls += model to transcript.toList()
                flow {
                    emit("Bonjour ")
                    emit("ami!")
                }
            },
            onTurnCompleted = { reply ->
                transcript += "U:hi"
                transcript += "M:$reply"
            }
        ).toList().joinToString("")

        assertEquals("Bonjour ami!", turn1)
        assertEquals(0, preferred)

        // Turn 2 stalls past firstChunkTimeoutMs on primary model -> fails over to backup with Turn 1 history
        val turn2 = streamChatWithModelFallback<Unit>(
            modelNames = FAST_FALLBACK_MODELS,
            preferredIndex = preferred,
            onPreferredIndexChanged = { preferred = it },
            firstChunkTimeoutMs = 100L,
            openStream = { model ->
                calls += model to transcript.toList()
                flow {
                    if (model == FAST_MODEL_NAME) {
                        delay(5_000L)
                    } else {
                        emit("Recovered with context")
                    }
                }
            },
            onTurnCompleted = { reply ->
                transcript += "U:next"
                transcript += "M:$reply"
            }
        ).toList().joinToString("")

        assertEquals("Recovered with context", turn2)
        assertEquals(1, preferred)
        // Backup model received Turn 1 history
        assertEquals(listOf("U:hi", "M:Bonjour ami!"), calls.last().second)
    }
}
