package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Formatters
import com.example.core.LocalUnitSystem
import com.example.core.rememberAppStrings
import com.example.data.model.AthleteProfile
import com.example.data.model.GearCategory
import com.example.data.model.SportType
import com.example.data.model.UiState
import com.example.data.model.VoiceCoachStatus
import com.example.ui.MainViewModel
import com.example.ui.components.FitnessFreshnessChart
import com.example.ui.components.MetricCell
import com.example.ui.components.RecoveryGaugeRing
import com.example.ui.components.SleepStagesBar
import com.example.ui.components.SportSelectorGrid
import com.example.ui.components.Vo2MaxTrendChart
import com.example.ui.theme.AmberZone
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import com.example.ui.theme.VioletZone

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: MainViewModel) {
    val analytics by viewModel.analyticsState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val healthLogsState by viewModel.healthLogsState.collectAsStateWithLifecycle()
    val gearState by viewModel.gearState.collectAsStateWithLifecycle()
    val challengesState by viewModel.challengesState.collectAsStateWithLifecycle()
    val coachState by viewModel.coachResponseState.collectAsStateWithLifecycle()
    val liveVoiceState by viewModel.liveVoiceState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val unitSystem = LocalUnitSystem.current
    val strings = rememberAppStrings()

    val profile = (profileState as? UiState.Success)?.data ?: AthleteProfile()
    val latestHealth = (healthLogsState as? UiState.Success)?.data?.firstOrNull()
    val gearList = (gearState as? UiState.Success)?.data.orEmpty()
    val challenges = (challengesState as? UiState.Success)?.data.orEmpty()

    var coachPrompt by remember { mutableStateOf("") }
    var voiceTextInput by remember { mutableStateOf("") }
    var showAddGearDialog by remember { mutableStateOf(false) }
    var showAddChallengeDialog by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startLiveVoiceCoach()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshAllData() },
        modifier = Modifier
            .fillMaxSize()
            .testTag("progress_pull_refresh")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        // 0. RAI Score (Readiness & Athlete Index) — displayed above VO2 Max
        item {
            val raiColor = when {
                analytics.raiScore >= 80 -> EmeraldPeak
                analytics.raiScore >= 65 -> VeloOrange
                analytics.raiScore >= 48 -> SignalCyan
                else -> AmberZone
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("rai_score_card")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.raiSectionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = VeloOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = strings.raiSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${analytics.raiScore}",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "/ 100",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            Surface(
                                color = raiColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = analytics.raiClassification,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = raiColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        RecoveryGaugeRing(
                            score = analytics.raiScore,
                            label = "RAI",
                            accentColor = raiColor,
                            sizeDp = 88.dp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCell(
                            label = strings.raiAerobicLabel,
                            value = "${analytics.raiAerobicComponent}/100",
                            valueColor = SignalCyan
                        )
                        MetricCell(
                            label = strings.raiLoadLabel,
                            value = "${analytics.raiLoadComponent}/100",
                            valueColor = VeloOrange
                        )
                        MetricCell(
                            label = strings.raiRecoveryLabel,
                            value = "${analytics.raiRecoveryComponent}/100",
                            valueColor = EmeraldPeak
                        )
                    }

                    val bioChipText = if (analytics.raiProfileCalibrated) {
                        val details = buildList {
                            profile.ageYears()?.let { add("${strings.ageLabel} $it") }
                            if (profile.heightCm > 0.0) add(Formatters.formatHeight(profile.heightCm, unitSystem))
                            if (profile.weightKg > 0.0) add(Formatters.formatWeight(profile.weightKg, unitSystem))
                            profile.bmi()?.let { add("${strings.bmiLabel} $it") }
                        }.joinToString(" • ")
                        "${strings.raiCalibratedNote}: $details"
                    } else {
                        strings.raiUncalibratedHint
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bioChipText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // 1. VO2 Max & Cardiovascular Fitness
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(strings.vo2MaxSectionLabel, style = MaterialTheme.typography.labelSmall, color = SignalCyan)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${analytics.vo2Max}",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "mL/kg/min",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                color = SignalCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = analytics.vo2MaxClassification,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SignalCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            val deltaPrefix = if (analytics.vo2MaxTrendDelta >= 0) "+" else ""
                            Text(
                                text = "$deltaPrefix${analytics.vo2MaxTrendDelta} (28d trend)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (analytics.vo2MaxTrendDelta >= 0) EmeraldPeak else AmberZone
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Vo2MaxTrendChart(series = analytics.trainingSeries)
                }
            }
        }

        // 2. Training Load (CTL / ATL / TSB / ACWR)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Training Load & Freshness", style = MaterialTheme.typography.titleLarge)
                        Surface(
                            color = VeloOrange.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = analytics.loadStatusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = VeloOrange,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCell("Fitness (CTL)", "${analytics.fitnessCtl}", valueColor = VeloOrange)
                        MetricCell("Fatigue (ATL)", "${analytics.fatigueAtl}", valueColor = VioletZone)
                        MetricCell(
                            label = "Form (TSB)",
                            value = "${if (analytics.formTsb >= 0) "+" else ""}${analytics.formTsb}",
                            valueColor = if (analytics.formTsb >= -5) EmeraldPeak else AmberZone
                        )
                        MetricCell("ACWR", "${analytics.acuteChronicRatio}")
                    }
                    Spacer(Modifier.height(14.dp))
                    FitnessFreshnessChart(series = analytics.trainingSeries)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "7-Day Relative Effort: ${analytics.weeklyRelativeEffort} • Optimal ACWR range: 0.80–1.30",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. Recovery Readiness & Wearable Sleep Architecture
        item {
            val recoveryColor = when {
                analytics.recoveryScore >= 75 -> EmeraldPeak
                analytics.recoveryScore >= 50 -> AmberZone
                else -> CrimsonPulse
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = VioletZone, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Recovery & Sleep", style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = analytics.recoveryStatusLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = recoveryColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Next high-intensity window: ~${analytics.recommendedRecoveryHours}h",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RecoveryGaugeRing(
                            score = analytics.recoveryScore,
                            label = "Readiness",
                            accentColor = recoveryColor,
                            sizeDp = 90.dp
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCell(
                            label = "HRV RMSSD",
                            value = if (analytics.hrvRmssdMs > 0) "${analytics.hrvRmssdMs} ms" else "--",
                            valueColor = SignalCyan
                        )
                        MetricCell(
                            label = "SpO2",
                            value = if ((latestHealth?.spo2Percent ?: 0.0) > 0.0) "${latestHealth?.spo2Percent}%" else "--",
                            valueColor = EmeraldPeak
                        )
                        MetricCell(
                            label = "Sleep",
                            value = if (analytics.sleepMinutes > 0) {
                                "${analytics.sleepMinutes / 60}h ${analytics.sleepMinutes % 60}m"
                            } else "--"
                        )
                        MetricCell(
                            label = "Active Kcal",
                            value = if ((latestHealth?.activeCalories ?: 0) > 0) "${latestHealth?.activeCalories}" else "--"
                        )
                    }

                    if (latestHealth != null && latestHealth.sleepTotalMinutes > 0) {
                        Spacer(Modifier.height(14.dp))
                        SleepStagesBar(
                            deepMin = latestHealth.sleepDeepMinutes,
                            lightMin = latestHealth.sleepLightMinutes,
                            remMin = latestHealth.sleepRemMinutes,
                            awakeMin = latestHealth.sleepAwakeMinutes
                        )
                    }
                }
            }
        }

        // 4. Real-Time Live Voice Coach (Gemini 3.8 Live API)
        item {
            val isLiveActive = liveVoiceState.status == VoiceCoachStatus.ACTIVE
            val isConnecting = liveVoiceState.status == VoiceCoachStatus.CONNECTING
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isLiveActive) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                contentDescription = "Live Voice Coach",
                                tint = if (isLiveActive) EmeraldPeak else VeloOrange
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Live Voice Coach", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Real-time voice conversation (gemini-3.8-live)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isLiveActive || isConnecting) {
                            Button(
                                onClick = { viewModel.stopLiveVoiceCoach() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonPulse,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("stop_live_voice_btn")
                            ) {
                                Icon(Icons.Rounded.CallEnd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("End")
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (viewModel.liveVoiceCoach.hasMicrophonePermission()) {
                                        viewModel.startLiveVoiceCoach()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VeloOrange,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("start_live_voice_btn")
                            ) {
                                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Talk Live")
                            }
                        }
                    }

                    Surface(
                        color = when (liveVoiceState.status) {
                            VoiceCoachStatus.ACTIVE -> EmeraldPeak.copy(alpha = 0.14f)
                            VoiceCoachStatus.ERROR -> CrimsonPulse.copy(alpha = 0.14f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = liveVoiceState.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (liveVoiceState.status) {
                                    VoiceCoachStatus.ACTIVE -> EmeraldPeak
                                    VoiceCoachStatus.ERROR -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    if (isLiveActive) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = voiceTextInput,
                                onValueChange = { voiceTextInput = it },
                                placeholder = { Text("Or send a text note mid-call...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("live_voice_text_input")
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (voiceTextInput.isNotBlank()) {
                                        viewModel.sendLiveVoiceText(voiceTextInput)
                                        voiceTextInput = ""
                                    }
                                },
                                modifier = Modifier.testTag("live_voice_send_text_btn")
                            ) {
                                Icon(Icons.Rounded.Send, contentDescription = "Send text to Live Voice", tint = VeloOrange)
                            }
                        }
                    }

                    if (liveVoiceState.transcripts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            liveVoiceState.transcripts.takeLast(6).forEach { entry ->
                                Surface(
                                    color = if (entry.isUser) {
                                        VeloOrange.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${if (entry.isUser) "You" else "Coach"}: ${entry.text}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Personalized AI Training Coach (Gemini 3.8 Flash)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Psychology, contentDescription = null, tint = SignalCyan)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("AI Performance Coach", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Personalized training advice from VO2 Max, Load, HRV & Sleep",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Recommend today's workout based on my recovery & load",
                            "How can I raise my VO2 Max safely?",
                            "Build a 7-day Run & Cycle polarized plan",
                            "Analyze my sleep recovery & fatigue ratio"
                        ).forEachIndexed { idx, prompt ->
                            AssistChip(
                                onClick = { viewModel.askAiCoach(prompt) },
                                label = {
                                    Text(
                                        when (idx) {
                                            0 -> "Today's Session"
                                            1 -> "Boost VO2 Max"
                                            2 -> "7-Day Plan"
                                            else -> "Recovery Audit"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = SignalCyan, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("ai_coach_chip_$idx")
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = coachPrompt,
                            onValueChange = { coachPrompt = it },
                            placeholder = { Text("Ask your coach (race pacing, recovery, intervals...)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_coach_input")
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (coachPrompt.isNotBlank()) {
                                    viewModel.askAiCoach(coachPrompt)
                                    coachPrompt = ""
                                }
                            },
                            modifier = Modifier.testTag("ai_coach_send_btn")
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = "Ask AI Coach", tint = SignalCyan)
                        }
                    }

                    when (val st = coachState) {
                        is UiState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Evaluating CTL/ATL/TSB, VO2 Max, and recovery telemetry...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is UiState.Error -> {
                            Text(st.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        is UiState.Success -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = st.data,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        null -> {}
                    }
                }
            }
        }

        // 5. Personal Records
        if (analytics.personalRecords.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = VeloOrange)
                            Spacer(Modifier.width(8.dp))
                            Text("Personal Records", style = MaterialTheme.typography.titleLarge)
                        }
                        analytics.personalRecords.forEachIndexed { idx, pr ->
                            if (idx > 0) HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(pr.category, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${pr.workoutTitle} • ${pr.dateLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = pr.valueLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = VeloOrange
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Challenges
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monthly Challenges (${challenges.size})", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { showAddChallengeDialog = true },
                    modifier = Modifier.testTag("add_challenge_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Goal")
                }
            }
        }

        items(challenges, key = { it.id }) { ch ->
            val pct = (ch.currentValue / ch.targetValue.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(ch.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${(pct * 100).toInt()}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            color = EmeraldPeak,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "${String.format("%.1f", ch.currentValue)} / ${ch.targetValue.toInt()} ${ch.unit} • ${ch.periodLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = EmeraldPeak
                    )
                }
            }
        }

        // 7. Gear Locker
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DirectionsRun, contentDescription = null, tint = SignalCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Gear Locker (${gearList.size})", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedButton(
                    onClick = { showAddGearDialog = true },
                    modifier = Modifier.testTag("add_gear_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Gear")
                }
            }
        }

        items(gearList, key = { it.id }) { gear ->
            val category = gear.parsedCategory
            val pct = (gear.distanceMeters / gear.maxDistanceMeters.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
            val gearIcon = when {
                category == GearCategory.HIKING_SHOES || category == GearCategory.TRAIL_RUN_SHOES -> Icons.Rounded.Hiking
                category.isBike -> Icons.Rounded.DirectionsBike
                category == GearCategory.WEARABLE_SENSOR -> Icons.Rounded.Watch
                else -> Icons.Rounded.DirectionsRun
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = SignalCyan.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = gearIcon,
                                    contentDescription = category.label,
                                    tint = SignalCyan,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${gear.brand} ${gear.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${category.label} • ${Formatters.formatDistanceKm(gear.distanceMeters, unitSystem)} / ${Formatters.formatDistanceKm(gear.maxDistanceMeters, unitSystem)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(
                            onClick = { viewModel.toggleGearRetired(gear) },
                            modifier = Modifier.testTag("retire_gear_${gear.id}")
                        ) {
                            Text(if (gear.retired) "Reactivate" else "Retire")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (pct > 0.85f) AmberZone else SignalCyan
                    )
                }
            }
        }
        }
    }

    if (showAddGearDialog) {
        AddGearDialog(
            onDismiss = { showAddGearDialog = false },
            onSave = { name, brand, type, maxKm ->
                viewModel.addGear(name, brand, type, maxKm)
                showAddGearDialog = false
            }
        )
    }

    if (showAddChallengeDialog) {
        AddChallengeDialog(
            onDismiss = { showAddChallengeDialog = false },
            onSave = { title, sport, targetVal, unit ->
                viewModel.addChallenge(title, sport, targetVal, unit)
                showAddChallengeDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGearDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(GearCategory.RUNNING_SHOES) }
    var maxKm by remember { mutableStateOf(GearCategory.RUNNING_SHOES.defaultMaxKm.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Gear") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Gear Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GearCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = category
                                maxKm = category.defaultMaxKm.toInt().toString()
                            },
                            label = { Text(category.label, maxLines = 1) },
                            modifier = Modifier.testTag("gear_type_${category.name.lowercase()}")
                        )
                    }
                }
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (e.g., Nike, Salomon, Hoka, Canyon)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gear_brand_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Model Name") },
                    placeholder = { Text(selectedCategory.defaultModelName) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gear_model_input")
                )
                OutlinedTextField(
                    value = maxKm,
                    onValueChange = { maxKm = it },
                    label = { Text("Retirement Threshold (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gear_threshold_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.ifBlank { selectedCategory.defaultModelName },
                        brand.ifBlank { "Athletic" },
                        selectedCategory.name,
                        maxKm.toDoubleOrNull() ?: selectedCategory.defaultMaxKm
                    )
                },
                modifier = Modifier.testTag("confirm_add_gear_btn")
            ) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddChallengeDialog(
    onDismiss: () -> Unit,
    onSave: (String, SportType, Double, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf(SportType.RUN) }
    var target by remember { mutableStateOf("100") }
    var unit by remember { mutableStateOf("km") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Challenge Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Challenge Title") },
                    placeholder = { Text("Monthly 100K Endurance") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SportSelectorGrid(selectedSport = sport, onSelect = { sport = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("Target") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.ifBlank { "${sport.label} ${target}${unit} Challenge" },
                        sport,
                        target.toDoubleOrNull() ?: 100.0,
                        unit.ifBlank { "km" }
                    )
                },
                modifier = Modifier.testTag("confirm_add_challenge_btn")
            ) {
                Text("Join")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
