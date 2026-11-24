package com.example.data.repository

import android.util.Log
import com.example.core.Formatters
import com.example.data.model.AthleteProfile
import com.example.data.model.DailyHealthLog
import com.example.data.model.MapsGroundedPlace
import com.example.data.model.MapsGroundingResult
import com.example.data.model.PerformanceAnalytics
import com.example.data.model.WorkoutActivity
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.LatLng
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.ToolConfig
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.retrievalConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException

class AthleteIntelligenceRepository {
    private val coachGenerationConfig = generationConfig {
        temperature = 0.55f
        maxOutputTokens = 700
    }

    private val primaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = coachGenerationConfig
        )
    }

    private val fallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = coachGenerationConfig
        )
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun searchNearbyPlacesWithMapsGrounding(
        query: String,
        latitude: Double,
        longitude: Double,
    ): MapsGroundingResult {
        val config = retrievalConfig {
            latLng = LatLng(latitude = latitude, longitude = longitude)
            languageCode = "en-US"
        }
        val sysInstruction = content {
            text(
                "You are VeloStride's outdoor route & trail scout. Use Google Maps data to recommend accurate, real-world parks, running tracks, hiking trails, cycling routes, and outdoor workout spots near the athlete's coordinates. Keep responses concise, structured, and practical with terrain/distance notes."
            )
        }
        val candidateModels = listOf("gemini-4-hydrogen", "gemini-3.8-flash")

        var lastError: Exception? = null
        var result: com.google.firebase.ai.type.GenerateContentResponse? = null
        for (idx in candidateModels.indices) {
            val modelName = candidateModels[idx]
            if (idx > 0) {
                delay(400L)
            }
            try {
                result = withTimeout(25_000L) {
                    val mapsModel = Firebase.ai.generativeModel(
                        modelName = modelName,
                        tools = listOf(Tool.googleMaps()),
                        toolConfig = ToolConfig(retrievalConfig = config),
                        systemInstruction = sysInstruction,
                        generationConfig = coachGenerationConfig
                    )
                    mapsModel.generateContent(query)
                }
                break
            } catch (e: TimeoutCancellationException) {
                lastError = e
                Log.w(TAG, "Maps grounding attempt ${idx + 1} ($modelName) timed out")
                if (idx == candidateModels.lastIndex) throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val canRetry = isTransientDemandError(e) && idx < candidateModels.lastIndex
                Log.w(TAG, "Maps grounding attempt ${idx + 1} ($modelName) failed: ${e.message}")
                if (!canRetry) throw e
            }
        }
        val finalResult = result ?: throw (lastError ?: IllegalStateException("Maps grounding failed."))

        val answerText = finalResult.text?.trim().orEmpty()
            .ifBlank { "No nearby locations returned for \"$query\"." }

        val places = finalResult.candidates.firstOrNull()
            ?.groundingMetadata
            ?.groundingChunks
            ?.mapNotNull { chunk ->
                val maps = chunk.maps ?: return@mapNotNull null
                val title = maps.title.orEmpty().trim()
                val uri = maps.uri.orEmpty().trim()
                if (title.isBlank() && uri.isBlank()) null
                else MapsGroundedPlace(
                    title = title.ifBlank { "Google Maps Location" },
                    uri = uri,
                    placeId = maps.placeId.orEmpty()
                )
            }
            ?.distinctBy { it.placeId.ifBlank { "${it.title}_${it.uri}" } }
            ?: emptyList()

        return MapsGroundingResult(
            query = query,
            answerText = answerText,
            places = places,
            latitude = latitude,
            longitude = longitude
        )
    }

    fun analyzeWorkoutStream(
        workout: WorkoutActivity,
        profile: AthleteProfile,
        analytics: PerformanceAnalytics,
    ): Flow<String> = flow {
        val bioLine = buildBiometricsSummary(profile)
        val prompt = buildString {
            appendLine("You are an elite endurance sports physiologist & Athlete Intelligence coach.")
            appendLine("Analyze this workout concisely in 3 short sections: Effort Verdict, Physiological Breakdown (factoring in age/gender/height/weight/BMI & HR zones if present), Next Step.")
            appendLine("Title: ${workout.title} | Sport: ${workout.parsedSport.label}")
            appendLine("Distance: ${Formatters.formatDistanceKm(workout.distanceMeters)} | Moving Time: ${Formatters.formatDuration(workout.movingTimeSeconds)} | Elev: ${Formatters.formatElevation(workout.elevationGainMeters)}")
            appendLine("Avg Pace: ${Formatters.formatPace(workout.avgPaceSecPerKm)} | Avg Speed: ${Formatters.formatSpeed(workout.avgSpeedKmh)}")
            appendLine("HR: Avg ${workout.avgHeartRate} / Max ${workout.maxHeartRate} bpm (Profile MaxHR=${profile.maxHr}, RestHR=${profile.restingHr}, FTP=${profile.ftpWatts}W)")
            appendLine("HR Zones (s Z1..Z5): ${workout.hrZoneSeconds.joinToString(", ")}")
            appendLine("Effort: ${workout.relativeEffort} | RPE: ${workout.perceivedExertion}/10 | Device: ${workout.deviceName ?: "Phone GPS"}")
            if (bioLine.isNotBlank()) {
                appendLine("Athlete Biometrics: $bioLine")
            }
            appendLine("Current Form: RAI=${analytics.raiScore}/100 (${analytics.raiClassification}), VO2 Max=${analytics.vo2Max} (${analytics.vo2MaxClassification}), CTL=${analytics.fitnessCtl}, ATL=${analytics.fatigueAtl}, TSB=${analytics.formTsb}, Recovery=${analytics.recoveryScore}%.")
        }

        streamWithRetryAndFallback(prompt)
    }

    fun generateTrainingRecommendationStream(
        userQuestion: String,
        profile: AthleteProfile,
        analytics: PerformanceAnalytics,
        recentWorkouts: List<WorkoutActivity>,
        latestHealth: DailyHealthLog?,
    ): Flow<String> = flow {
        val recentSummary = recentWorkouts.take(5).joinToString("\n") { w ->
            "- ${w.title} (${w.parsedSport.label}): ${Formatters.formatDistanceKm(w.distanceMeters)}, ${Formatters.formatDuration(w.movingTimeSeconds)}, HR ${w.avgHeartRate}, Effort ${w.relativeEffort}"
        }.ifBlank { "No recorded workouts yet." }
        val bioLine = buildBiometricsSummary(profile)

        val prompt = buildString {
            appendLine("You are VeloStride AI Coach, an expert endurance performance physiologist.")
            appendLine("Reply clearly and practically in the same language as the user's question (Indonesian or English).")
            appendLine("ATHLETE: ${profile.displayName} | Sport: ${profile.preferredSport} | Goal: ${profile.weeklyDistanceGoalKm} km/wk | MaxHR ${profile.maxHr} | RestHR ${profile.restingHr} | FTP ${profile.ftpWatts}W")
            if (bioLine.isNotBlank()) {
                appendLine("BIOMETRICS: $bioLine")
            }
            appendLine("ANALYTICS: RAI ${analytics.raiScore}/100 (${analytics.raiClassification}: Aerobic ${analytics.raiAerobicComponent}, Load ${analytics.raiLoadComponent}, Recovery ${analytics.raiRecoveryComponent}), VO2 Max ${analytics.vo2Max} (${analytics.vo2MaxClassification}), CTL ${analytics.fitnessCtl}, ATL ${analytics.fatigueAtl}, TSB ${analytics.formTsb}, ACWR ${analytics.acuteChronicRatio} (${analytics.loadStatusLabel}), Recovery ${analytics.recoveryScore}% (${analytics.recoveryStatusLabel}), HRV ${analytics.hrvRmssdMs}ms, Sleep ${analytics.sleepMinutes / 60}h${analytics.sleepMinutes % 60}m (${analytics.sleepScore}/100).")
            if (latestHealth != null) {
                appendLine("WEARABLE TODAY (${latestHealth.sourceDevice}): ${latestHealth.steps} steps, ${latestHealth.activeCalories} kcal")
            }
            appendLine("RECENT SESSIONS:")
            appendLine(recentSummary)
            appendLine("USER QUESTION: $userQuestion")
        }

        streamWithRetryAndFallback(prompt)
    }

    private fun buildBiometricsSummary(profile: AthleteProfile): String {
        val parts = mutableListOf<String>()
        profile.ageYears()?.let { parts.add("Age $it yrs") }
        when (profile.gender.uppercase()) {
            "MALE" -> parts.add("Gender Male")
            "FEMALE" -> parts.add("Gender Female")
            "OTHER" -> parts.add("Gender Other")
        }
        if (profile.heightCm > 0.0) parts.add("Height ${profile.heightCm.toInt()} cm")
        if (profile.weightKg > 0.0) parts.add("Weight ${String.format(java.util.Locale.US, "%.1f", profile.weightKg)} kg")
        profile.bmi()?.let { bmi ->
            val cat = profile.bmiCategory().orEmpty()
            parts.add("BMI $bmi ($cat)")
        }
        if (profile.weightKg > 0.0 && profile.ftpWatts > 0) {
            val wPerKg = String.format(java.util.Locale.US, "%.2f", profile.ftpWatts / profile.weightKg)
            parts.add("Power/Weight $wPerKg W/kg")
        }
        return parts.joinToString(" | ")
    }

    private data class AiAttempt(
        val label: String,
        val model: GenerativeModel,
        val timeoutMs: Long,
        val useStreaming: Boolean,
    )

    private suspend fun FlowCollector<String>.streamWithRetryAndFallback(prompt: String) {
        // Attempt 1: Primary gemini-3.8-flash streaming (bounded 20s timeout so stalls fall back fast).
        // Attempt 2: Fallback gemini-3.5-flash-lite streaming (20s timeout, separate quota & ultra-fast).
        // Attempt 3: Fallback gemini-3.5-flash-lite unary generateContent (20s timeout in case SSE streaming stalls).
        val attempts = listOf(
            AiAttempt("gemini-3.8-flash (stream)", primaryModel, 20_000L, useStreaming = true),
            AiAttempt("gemini-3.5-flash-lite (stream)", fallbackModel, 20_000L, useStreaming = true),
            AiAttempt("gemini-3.5-flash-lite (unary)", fallbackModel, 20_000L, useStreaming = false)
        )

        var lastError: Exception? = null
        for (idx in attempts.indices) {
            val attempt = attempts[idx]
            if (idx > 0) {
                delay(450L)
            }
            val accumulated = StringBuilder()
            var emittedAny = false
            try {
                withTimeout(attempt.timeoutMs) {
                    if (attempt.useStreaming) {
                        attempt.model.generateContentStream(prompt).collect { chunk ->
                            val text = chunk.text.orEmpty()
                            if (text.isNotEmpty()) {
                                emittedAny = true
                                accumulated.append(text)
                                emit(accumulated.toString())
                            }
                        }
                    } else {
                        val resp = attempt.model.generateContent(prompt)
                        val text = resp.text.orEmpty().trim()
                        if (text.isNotEmpty()) {
                            emittedAny = true
                            accumulated.append(text)
                            emit(text)
                        }
                    }
                }
                if (accumulated.isNotBlank()) return
                if (idx == attempts.lastIndex) return
            } catch (e: TimeoutCancellationException) {
                lastError = e
                Log.w(TAG, "AI attempt ${idx + 1} (${attempt.label}) timed out after ${attempt.timeoutMs}ms")
                if (emittedAny || idx == attempts.lastIndex) throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val canFallback = !emittedAny && isTransientDemandError(e) && idx < attempts.lastIndex
                Log.w(TAG, "AI attempt ${idx + 1} (${attempt.label}) failed: ${e.message}")
                if (!canFallback) throw e
            }
        }
        lastError?.let { throw it }
    }

    companion object {
        private const val TAG = "AthleteIntelRepo"

        fun isTimeoutError(throwable: Throwable): Boolean {
            if (generateSequence(throwable) { it.cause }.any {
                    it is TimeoutCancellationException || it is SocketTimeoutException
                }
            ) {
                return true
            }
            val messages = collectMessagesLowercase(throwable)
            return messages.contains("timeout") ||
                messages.contains("timed out") ||
                messages.contains("deadline_exceeded") ||
                messages.contains("deadline exceeded")
        }

        fun isQuotaExceededError(throwable: Throwable): Boolean {
            val messages = collectMessagesLowercase(throwable)
            if (messages.isBlank()) return false
            return messages.contains("quota") ||
                messages.contains("exceeded") ||
                messages.contains("resource_exhausted") ||
                messages.contains("resource exhausted") ||
                messages.contains("429") ||
                messages.contains("too many requests") ||
                messages.contains("rate limit") ||
                messages.contains("rate_limit")
        }

        fun isTransientDemandError(throwable: Throwable): Boolean {
            if (isTimeoutError(throwable) || isQuotaExceededError(throwable)) return true
            val messages = collectMessagesLowercase(throwable)
            if (messages.isBlank()) return false
            return messages.contains("high demand") ||
                messages.contains("spikes in demand") ||
                messages.contains("503") ||
                messages.contains("overloaded") ||
                messages.contains("unavailable") ||
                messages.contains("temporarily")
        }

        private fun collectMessagesLowercase(throwable: Throwable): String =
            generateSequence(throwable) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" | ")
                .lowercase()
    }
}
