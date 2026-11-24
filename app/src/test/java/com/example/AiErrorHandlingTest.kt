package com.example

import com.example.data.repository.AthleteIntelligenceRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class AiErrorHandlingTest {

    @Test
    fun `isTransientDemandError detects Gemini high demand, quota, and overload errors`() {
        val highDemand = RuntimeException(
            "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again."
        )
        val nested503 = IllegalStateException("Inference failed", RuntimeException("HTTP 503 Service Unavailable"))
        val rateLimit429 = RuntimeException("429 Too Many Requests / RESOURCE_EXHAUSTED")
        val quotaExceeded = RuntimeException(
            "You exceeded your current quota, please check your plan and billing details."
        )

        assertTrue(AthleteIntelligenceRepository.isTransientDemandError(highDemand))
        assertTrue(AthleteIntelligenceRepository.isTransientDemandError(nested503))
        assertTrue(AthleteIntelligenceRepository.isTransientDemandError(rateLimit429))
        assertTrue(AthleteIntelligenceRepository.isQuotaExceededError(quotaExceeded))
        assertTrue(AthleteIntelligenceRepository.isTransientDemandError(quotaExceeded))
    }

    @Test
    fun `isTimeoutError detects socket timeouts and coroutine TimeoutCancellationException`() = runTest {
        val socketTimeout = SocketTimeoutException("Read timed out")
        var caughtTimeout: TimeoutCancellationException? = null
        try {
            withTimeout(1L) {
                kotlinx.coroutines.delay(50L)
            }
        } catch (e: TimeoutCancellationException) {
            caughtTimeout = e
        }

        assertTrue(AthleteIntelligenceRepository.isTimeoutError(socketTimeout))
        assertTrue(AthleteIntelligenceRepository.isTransientDemandError(socketTimeout))
        assertTrue(caughtTimeout != null && AthleteIntelligenceRepository.isTimeoutError(caughtTimeout))
        assertTrue(caughtTimeout != null && AthleteIntelligenceRepository.isTransientDemandError(caughtTimeout))
    }

    @Test
    fun `isTransientDemandError does not classify auth or app check errors as demand spikes`() {
        val appCheck = RuntimeException("HTTP 403 App Check token is invalid.")
        val emptyMsg = RuntimeException()

        assertFalse(AthleteIntelligenceRepository.isTransientDemandError(appCheck))
        assertFalse(AthleteIntelligenceRepository.isQuotaExceededError(appCheck))
        assertFalse(AthleteIntelligenceRepository.isTimeoutError(appCheck))
        assertFalse(AthleteIntelligenceRepository.isTransientDemandError(emptyMsg))
    }
}
