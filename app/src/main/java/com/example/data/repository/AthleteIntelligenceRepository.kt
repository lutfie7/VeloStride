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
import com.google.firebase.ai.type.retrievalConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

class AthleteIntelligenceRepository {
    private val primaryModel by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val fallbackModel by lazy {
        Firebase.ai.generativeModel("gemini-3.5-flash-lite")
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
        val candidateModels = listOf("gemini-4-hydrogen", "gemini-4-hydrogen", "gemini-3.8-flash")

        val response = withTimeout(60_000L) {
            var lastError: Exception? = null
            var result: com.google.firebase.ai.type.GenerateContentResponse? = null
            for (idx in candidateModels.indices) {
                val modelName = candidateModels[idx]
                if (idx > 0) {
                    delay(if (idx == 1) 800L else 500L)
                }
                try {
                    val mapsModel = Firebase.ai.generativeModel(
                        modelName = modelName,
                        tools = listOf(Tool.googleMaps()),
                        toolConfig = ToolConfig(retrievalConfig = config),
                        systemInstruction = sysInstruction
                    )
                    result = mapsModel.generateContent(query)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    val canRetry = isTransientDemandError(e) && idx < candidateModels.lastIndex
                    Log.w(TAG, "Maps grounding attempt ${idx + 1} ($modelName) failed: ${e.message}")
                    if (!canRetry) throw e
                }
            }
            result ?: throw (lastError ?: IllegalStateException("Maps grounding failed."))
        }

        val answerText = response.text?.trim().orEmpty()
            .ifBlank { "No nearby locations returned for \"$query\"." }

        val places = response.candidates.firstOrNull()
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
        val prompt = buildString {
            appendLine("You are an elite endurance sports physiologist & Strava Athlete Intelligence coach.")
            appendLine("Analyze this recorded workout in concise, high-signal athletic prose (3 short sections: Effort Verdict, Physiological Breakdown, Next Step).")
            appendLine("Workout Title: ${workout.title}")
            appendLine("Sport: ${workout.parsedSport.label}")
            appendLine("Distance: ${Formatters.formatDistanceKm(workout.distanceMeters)}")
            appendLine("Moving Time: ${Formatters.formatDuration(workout.movingTimeSeconds)}")
            appendLine("Elevation Gain: ${Formatters.formatElevation(workout.elevationGainMeters)}")
            appendLine("Avg Pace: ${Formatters.formatPace(workout.avgPaceSecPerKm)} | Avg Speed: ${Formatters.formatSpeed(workout.avgSpeedKmh)}")
            appendLine("Heart Rate: Avg ${workout.avgHeartRate} bpm / Max ${workout.maxHeartRate} bpm (Athlete MaxHR=${profile.maxHr}, RestingHR=${profile.restingHr})")
            appendLine("HR Zones (seconds Z1..Z5): ${workout.hrZoneSeconds.joinToString(", ")}")
            appendLine("Relative Effort: ${workout.relativeEffort} | RPE: ${workout.perceivedExertion}/10 | Device: ${workout.deviceName ?: "Phone GPS"}")
            appendLine("Current Athlete Context: VO2 Max=${analytics.vo2Max} (${analytics.vo2MaxClassification}), Fitness CTL=${analytics.fitnessCtl}, Fatigue ATL=${analytics.fatigueAtl}, Form TSB=${analytics.formTsb}, Recovery=${analytics.recoveryScore}%.")
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
        val recentSummary = recentWorkouts.take(6).joinToString("\n") { w ->
            "- ${w.title} (${w.parsedSport.label}): ${Formatters.formatDistanceKm(w.distanceMeters)}, ${Formatters.formatDuration(w.movingTimeSeconds)}, AvgHR ${w.avgHeartRate} bpm, Effort ${w.relativeEffort}"
        }.ifBlank { "No recorded workouts yet." }

        val prompt = buildString {
            appendLine("You are VeloStride AI Coach, an expert running & cycling performance coach.")
            appendLine("Respond clearly and practically in the same language as the user's question (Indonesian or English).")
            appendLine()
            appendLine("ATHLETE BASELINE & GOALS:")
            appendLine("- Name: ${profile.displayName}")
            appendLine("- Primary Sport: ${profile.preferredSport} | Weekly Distance Goal: ${profile.weeklyDistanceGoalKm} km")
            appendLine("- MaxHR: ${profile.maxHr} bpm | RestingHR: ${profile.restingHr} bpm | FTP: ${profile.ftpWatts} W")
            appendLine()
            appendLine("COMPREHENSIVE PERFORMANCE ANALYTICS:")
            appendLine("- Estimated VO2 Max: ${analytics.vo2Max} ml/kg/min (${analytics.vo2MaxClassification}, trend ${analytics.vo2MaxTrendDelta})")
            appendLine("- Fitness (CTL): ${analytics.fitnessCtl} | Fatigue (ATL): ${analytics.fatigueAtl} | Form (TSB): ${analytics.formTsb}")
            appendLine("- Acute:Chronic Workload Ratio (ACWR): ${analytics.acuteChronicRatio} (${analytics.loadStatusLabel})")
            appendLine("- Weekly Relative Effort: ${analytics.weeklyRelativeEffort}")
            appendLine("- Recovery Readiness: ${analytics.recoveryScore}% (${analytics.recoveryStatusLabel}), recommended recovery window: ${analytics.recommendedRecoveryHours}h")
            appendLine("- HRV RMSSD: ${analytics.hrvRmssdMs} ms | Last Sleep: ${analytics.sleepMinutes / 60}h ${analytics.sleepMinutes % 60}m (Score ${analytics.sleepScore}/100)")
            if (latestHealth != null) {
                appendLine("- Today Wearable Sync (${latestHealth.sourceDevice}): ${latestHealth.steps} steps, ${latestHealth.activeCalories} active kcal")
            }
            appendLine()
            appendLine("RECENT WORKOUT HISTORY:")
            appendLine(recentSummary)
            appendLine()
            appendLine("USER REQUEST / QUESTION:")
            appendLine(userQuestion)
        }

        streamWithRetryAndFallback(prompt)
    }

    private suspend fun FlowCollector<String>.streamWithRetryAndFallback(prompt: String) {
        val attempts: List<Pair<String, GenerativeModel>> = listOf(
            "gemini-3.8-flash" to primaryModel,
            "gemini-3.8-flash (retry)" to primaryModel,
            "gemini-3.5-flash-lite" to fallbackModel
        )

        withTimeout(60_000L) {
            for (idx in attempts.indices) {
                val (label, targetModel) = attempts[idx]
                if (idx > 0) {
                    delay(if (idx == 1) 900L else 500L)
                }
                val accumulated = StringBuilder()
                var emittedAny = false
                try {
                    targetModel.generateContentStream(prompt).collect { chunk ->
                        val text = chunk.text.orEmpty()
                        if (text.isNotEmpty()) {
                            emittedAny = true
                            accumulated.append(text)
                            emit(accumulated.toString())
                        }
                    }
                    if (accumulated.isNotBlank()) return@withTimeout
                    if (idx == attempts.lastIndex) return@withTimeout
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val canRetry = !emittedAny && isTransientDemandError(e) && idx < attempts.lastIndex
                    Log.w(TAG, "AI stream attempt ${idx + 1} ($label) failed: ${e.message}")
                    if (!canRetry) throw e
                }
            }
        }
    }

    companion object {
        private const val TAG = "AthleteIntelRepo"

        fun isTransientDemandError(throwable: Throwable): Boolean {
            val messages = generateSequence(throwable) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" | ")
                .lowercase()
            if (messages.isBlank()) return false
            return messages.contains("high demand") ||
                messages.contains("spikes in demand") ||
                messages.contains("503") ||
                messages.contains("429") ||
                messages.contains("overloaded") ||
                messages.contains("unavailable") ||
                messages.contains("resource_exhausted") ||
                messages.contains("resource exhausted") ||
                messages.contains("too many requests") ||
                messages.contains("temporarily")
        }
    }
}
