package com.example.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.core.Formatters
import com.example.data.model.AthleteProfile
import com.example.data.model.DailyHealthLog
import com.example.data.model.PerformanceAnalytics
import com.example.data.model.VoiceCoachStatus
import com.example.data.model.VoiceTranscriptEntry
import com.example.data.model.WorkoutActivity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class LiveVoiceCoachUiState(
    val status: VoiceCoachStatus = VoiceCoachStatus.IDLE,
    val statusMessage: String = "Tap Start Voice Coach to speak in real time with Gemini 3.8 Live",
    val transcripts: List<VoiceTranscriptEntry> = emptyList(),
)

@OptIn(PublicPreviewAPI::class)
class LiveVoiceCoachManager(
    private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(LiveVoiceCoachUiState())
    val state: StateFlow<LiveVoiceCoachUiState> = _state.asStateFlow()

    private var activeSession: LiveSession? = null
    private var conversationJob: Job? = null

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startConversation(
        profile: AthleteProfile,
        analytics: PerformanceAnalytics,
        recentWorkouts: List<WorkoutActivity>,
        latestHealth: DailyHealthLog? = null,
    ) {
        if (!hasMicrophonePermission()) {
            _state.update {
                it.copy(
                    status = VoiceCoachStatus.ERROR,
                    statusMessage = "Microphone permission is required for real-time voice conversations."
                )
            }
            return
        }

        if (_state.value.status == VoiceCoachStatus.CONNECTING ||
            _state.value.status == VoiceCoachStatus.ACTIVE
        ) {
            return
        }

        conversationJob?.cancel()
        conversationJob = scope.launch {
            _state.update {
                it.copy(
                    status = VoiceCoachStatus.CONNECTING,
                    statusMessage = "Connecting to gemini-3.8-live voice session..."
                )
            }

            try {
                val workoutDigest = recentWorkouts.take(5).joinToString("; ") { w ->
                    "${w.title} (${w.parsedSport.label}): ${Formatters.formatDistanceKm(w.distanceMeters)}, " +
                        "${Formatters.formatDuration(w.movingTimeSeconds)}, +${w.elevationGainMeters.toInt()}m elev"
                }.ifBlank { "No workouts logged yet" }

                val liveModel = Firebase.ai.liveModel(
                    modelName = "gemini-3.8-live",
                    generationConfig = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        speechConfig = SpeechConfig(voice = Voice("FENRIR"))
                        inputAudioTranscription = AudioTranscriptionConfig()
                        outputAudioTranscription = AudioTranscriptionConfig()
                    },
                    systemInstruction = content {
                        text(
                            buildString {
                                append("You are VeloStride Live Voice Coach, a concise, motivating endurance coach speaking in real time. ")
                                append("Reply naturally in the user's language (English or Indonesian). ")
                                append("Athlete: ${profile.displayName}, Primary Sport: ${profile.preferredSport}, Weekly Goal: ${profile.weeklyDistanceGoalKm} km. ")
                                profile.ageYears()?.let { append("Age: $it. ") }
                                if (profile.gender.isNotBlank()) append("Gender: ${profile.gender}. ")
                                if (profile.heightCm > 0.0) append("Height: ${profile.heightCm.toInt()} cm. ")
                                if (profile.weightKg > 0.0) append("Weight: ${profile.weightKg} kg. ")
                                append("Telemetry: RAI ${analytics.raiScore}/100 (${analytics.raiClassification}), ")
                                append("VO2 Max ${analytics.vo2Max}, CTL ${analytics.fitnessCtl}, ATL ${analytics.fatigueAtl}, ")
                                append("TSB ${analytics.formTsb}, Recovery ${analytics.recoveryScore}% (${analytics.recoveryStatusLabel}), ")
                                append("HRV ${analytics.hrvRmssdMs} ms. ")
                                if (latestHealth != null) {
                                    append("Wearable (${latestHealth.sourceDevice}): ${latestHealth.steps} steps, ")
                                    append("${latestHealth.activeCalories} kcal active, Resting HR ${latestHealth.restingHr} bpm, ")
                                    if (latestHealth.spo2Percent > 0.0) append("SpO2 ${latestHealth.spo2Percent}%, ")
                                    if (latestHealth.sleepTotalMinutes > 0) {
                                        append("Sleep ${latestHealth.sleepTotalMinutes / 60}h${latestHealth.sleepTotalMinutes % 60}m (Deep ${latestHealth.sleepDeepMinutes}m, REM ${latestHealth.sleepRemMinutes}m), ")
                                    }
                                }
                                append("Recent sessions: $workoutDigest.")
                            }
                        )
                    }
                )

                val session = withTimeout(30_000L) {
                    liveModel.connect()
                }
                activeSession = session

                _state.update {
                    it.copy(
                        status = VoiceCoachStatus.ACTIVE,
                        statusMessage = "Live voice session active — speak naturally with your coach"
                    )
                }

                val conversationConfig = liveAudioConversationConfig {
                    enableInterruptions = true
                    transcriptHandler = { inputTranscription, outputTranscription ->
                        val userText = inputTranscription?.text?.trim().orEmpty()
                        val modelText = outputTranscription?.text?.trim().orEmpty()
                        if (userText.isNotBlank()) {
                            appendTranscript(isUser = true, text = userText)
                        }
                        if (modelText.isNotBlank()) {
                            appendTranscript(isUser = false, text = modelText)
                        }
                    }
                    goAwayHandler = {
                        _state.update {
                            it.copy(
                                status = VoiceCoachStatus.STOPPED,
                                statusMessage = "Live session closed by server. Tap Start to reconnect."
                            )
                        }
                    }
                }

                session.startAudioConversation(conversationConfig)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Live API voice session failed", e)
                val raw = e.localizedMessage ?: "Failed to start Live voice session."
                val display = if (
                    raw.contains("App Check", ignoreCase = true) ||
                    raw.contains("403") ||
                    raw.contains("closed by the server", ignoreCase = true)
                ) {
                    "$raw\n\nTip: Untuk APK hasil build GitHub, buat Debug Token di Firebase Console (App Check -> Manage debug tokens -> Add debug token) lalu masukkan ke GitHub Secret FIREBASE_APPCHECK_DEBUG_TOKEN dan build ulang."
                } else raw
                _state.update {
                    it.copy(
                        status = VoiceCoachStatus.ERROR,
                        statusMessage = display
                    )
                }
                stopConversationInternal(updateStatus = false)
            }
        }
    }

    fun sendTextDuringLiveSession(message: String) {
        val clean = message.trim()
        val session = activeSession ?: return
        if (clean.isBlank()) return
        scope.launch {
            try {
                appendTranscript(isUser = true, text = clean)
                session.sendTextRealtime(clean)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending realtime text to LiveSession", e)
                _state.update {
                    it.copy(statusMessage = e.localizedMessage ?: "Failed to send message.")
                }
            }
        }
    }

    fun stopConversation() {
        scope.launch {
            stopConversationInternal(updateStatus = true)
        }
    }

    private suspend fun stopConversationInternal(updateStatus: Boolean) {
        conversationJob?.cancel()
        conversationJob = null
        val session = activeSession
        activeSession = null
        try {
            session?.stopAudioConversation()
            session?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiveSession", e)
        }
        if (updateStatus) {
            _state.update {
                it.copy(
                    status = VoiceCoachStatus.STOPPED,
                    statusMessage = "Voice conversation ended. Tap Start Voice Coach anytime."
                )
            }
        }
    }

    private fun appendTranscript(isUser: Boolean, text: String) {
        _state.update { current ->
            val list = current.transcripts.toMutableList()
            val last = list.lastOrNull()
            if (last != null && last.isUser == isUser && System.currentTimeMillis() - last.timestampMs < 4000L) {
                list[list.lastIndex] = last.copy(
                    text = "${last.text} $text".trim(),
                    timestampMs = System.currentTimeMillis()
                )
            } else {
                list.add(VoiceTranscriptEntry(isUser = isUser, text = text))
            }
            current.copy(transcripts = list.takeLast(30))
        }
    }

    private companion object {
        const val TAG = "LiveVoiceCoach"
    }
}
