package com.example

import com.example.data.repository.AthleteIntelligenceRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiErrorHandlingTest {

    @Test
    fun `isTransientDemandError detects Gemini high demand and overload errors`() {
        val highDemand = RuntimeException(
            "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again."
        )
        val nested503 = IllegalStateException("Inference failed", RuntimeException("HTTP 503 Service Unavailable"))
        val rateLimit429 = RuntimeException("429 Too Many Requests / RESOURCE_EXHAUSTED")

        assertTrue(AthleteIntelligenceRepository.isTransientDemandError(highDemand))
        assertTrue(AthleteIntelligenceRepository.isTransientDemandError(nested503))
        assertTrue(AthleteIntelligenceRepository.isTransientDemandError(rateLimit429))
    }

    @Test
    fun `isTransientDemandError does not classify auth or app check errors as demand spikes`() {
        val appCheck = RuntimeException("HTTP 403 App Check token is invalid.")
        val emptyMsg = RuntimeException()

        assertFalse(AthleteIntelligenceRepository.isTransientDemandError(appCheck))
        assertFalse(AthleteIntelligenceRepository.isTransientDemandError(emptyMsg))
    }
}
