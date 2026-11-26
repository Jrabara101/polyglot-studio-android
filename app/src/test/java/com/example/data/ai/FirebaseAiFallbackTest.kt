package com.example.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
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
}
