package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.core.AppLanguage
import com.example.core.UnitSystem
import com.example.core.rememberAppStrings
import com.example.data.model.AthleteProfile
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    profile: AthleteProfile,
    userEmail: String,
    isDarkTheme: Boolean,
    selectedLanguage: AppLanguage,
    selectedUnitSystem: UnitSystem,
    onToggleTheme: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectUnitSystem: (UnitSystem) -> Unit,
    onOpenProfileTargets: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = rememberAppStrings()
    var showSignOutConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = strings.settingsTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_settings_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = strings.backLabel
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // 1. Account Summary Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = strings.accountSection.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VeloOrange,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (profile.photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = profile.photoUrl,
                                            contentDescription = profile.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(VeloOrange.copy(alpha = 0.16f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = VeloOrange
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.displayName.ifBlank { "VeloStride Athlete" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (userEmail.isNotBlank()) {
                                            Text(
                                                text = userEmail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val bioSummary = buildList {
                                            profile.ageYears()?.let { add("${strings.ageLabel} $it") }
                                            if (profile.heightCm > 0.0) add(com.example.core.Formatters.formatHeight(profile.heightCm, selectedUnitSystem))
                                            if (profile.weightKg > 0.0) add(com.example.core.Formatters.formatWeight(profile.weightKg, selectedUnitSystem))
                                            profile.bmi()?.let { add("${strings.bmiLabel} $it") }
                                        }.joinToString(" • ")
                                        if (bioSummary.isNotBlank()) {
                                            Text(
                                                text = bioSummary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SignalCyan,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        onOpenProfileTargets()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("settings_open_profile_targets_btn")
                                ) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.editAthleteTargets)
                                }
                            }
                        }

                        // 2. Preferences: Language, Unit System, Appearance
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = strings.preferencesSection.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalCyan,
                                    fontWeight = FontWeight.Bold
                                )

                                // Language Option
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Language,
                                            contentDescription = null,
                                            tint = SignalCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = strings.languageLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = strings.languageDesc,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SelectableOptionCard(
                                            title = AppLanguage.ENGLISH_US.displayName,
                                            subtitle = "en-US",
                                            selected = selectedLanguage == AppLanguage.ENGLISH_US,
                                            onClick = { onSelectLanguage(AppLanguage.ENGLISH_US) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("settings_lang_en")
                                        )
                                        SelectableOptionCard(
                                            title = AppLanguage.INDONESIAN.displayName,
                                            subtitle = "id-ID",
                                            selected = selectedLanguage == AppLanguage.INDONESIAN,
                                            onClick = { onSelectLanguage(AppLanguage.INDONESIAN) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("settings_lang_id")
                                        )
                                    }
                                }

                                HorizontalDivider()

                                // Unit System Option (Metric / Imperial)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Straighten,
                                            contentDescription = null,
                                            tint = VeloOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = strings.unitSystemLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = strings.unitSystemDesc,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SelectableOptionCard(
                                            title = strings.metricLabel,
                                            subtitle = strings.metricDetail,
                                            selected = selectedUnitSystem == UnitSystem.METRIC,
                                            onClick = { onSelectUnitSystem(UnitSystem.METRIC) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("settings_unit_metric")
                                        )
                                        SelectableOptionCard(
                                            title = strings.imperialLabel,
                                            subtitle = strings.imperialDetail,
                                            selected = selectedUnitSystem == UnitSystem.IMPERIAL,
                                            onClick = { onSelectUnitSystem(UnitSystem.IMPERIAL) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("settings_unit_imperial")
                                        )
                                    }
                                }

                                HorizontalDivider()

                                // Appearance toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DarkMode,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = strings.darkModeLabel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = strings.darkModeDesc,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = isDarkTheme,
                                        onCheckedChange = { onToggleTheme() },
                                        modifier = Modifier.testTag("settings_dark_mode_switch")
                                    )
                                }
                            }
                        }

                        // 3. About App & App Version
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.testTag("settings_about_card")
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = SignalCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = strings.aboutSection.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                VeloStrideWordmark(
                                    fontSize = 22.sp,
                                    onDarkSurface = isDarkTheme,
                                    showCrest = true,
                                    crestSize = 24.dp
                                )

                                Text(
                                    text = strings.aboutAppTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.aboutAppDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_app_version"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.appVersionLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME} (${strings.buildCodeLabel} ${BuildConfig.VERSION_CODE})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.packageIdLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = BuildConfig.APPLICATION_ID,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 4. Exit / Sign Out inside Settings
                        Button(
                            onClick = { showSignOutConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonPulse,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("sign_out_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = strings.exitAppLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(strings.exitConfirmTitle) },
            text = { Text(strings.exitConfirmMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onDismiss()
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonPulse,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_sign_out_btn")
                ) {
                    Text(strings.exitAppLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(strings.cancelLabel)
                }
            }
        )
    }
}

@Composable
private fun SelectableOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) VeloOrange else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (selected) {
        VeloOrange.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) VeloOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
