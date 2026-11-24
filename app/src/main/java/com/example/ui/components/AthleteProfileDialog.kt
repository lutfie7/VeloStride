package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.core.Formatters
import com.example.core.LocalUnitSystem
import com.example.core.UnitSystem
import com.example.core.rememberAppStrings
import com.example.data.model.AthleteProfile
import com.example.data.model.SportType
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class AthleteProfileUpdatePayload(
    val displayName: String,
    val photoUrl: String,
    val birthDateIso: String,
    val gender: String,
    val heightCm: Double,
    val weightKg: Double,
    val weeklyGoalKm: Double,
    val maxHr: Int,
    val restingHr: Int,
    val ftpWatts: Int,
    val preferredSport: SportType,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AthleteProfileDialog(
    profile: AthleteProfile,
    onDismiss: () -> Unit,
    onSave: (AthleteProfileUpdatePayload) -> Unit,
) {
    val context = LocalContext.current
    val strings = rememberAppStrings()
    val unitSystem = LocalUnitSystem.current
    val distUnitLabel = Formatters.distanceUnitLabel(unitSystem)
    val weightUnitLabel = Formatters.weightUnitLabel(unitSystem)

    val initialGoalDisplay = Formatters.kmToDisplayDistance(profile.weeklyDistanceGoalKm, unitSystem)
        .roundToInt()
        .coerceAtLeast(1)
        .toString()
    val initialWeightDisplay = if (profile.weightKg > 0.0) {
        String.format(Locale.US, "%.1f", Formatters.kgToDisplayWeight(profile.weightKg, unitSystem))
    } else ""

    var displayName by remember(profile) { mutableStateOf(profile.displayName) }
    var photoUrl by remember(profile) { mutableStateOf(profile.photoUrl) }
    var birthDateIso by remember(profile) { mutableStateOf(profile.birthDateIso) }
    var gender by remember(profile) { mutableStateOf(profile.gender) }
    var heightInput by remember(profile) {
        mutableStateOf(if (profile.heightCm > 0.0) profile.heightCm.roundToInt().toString() else "")
    }
    var weightInput by remember(profile, unitSystem) { mutableStateOf(initialWeightDisplay) }
    var goalInput by remember(profile, unitSystem) { mutableStateOf(initialGoalDisplay) }
    var maxHr by remember(profile) { mutableStateOf(profile.maxHr.toString()) }
    var restHr by remember(profile) { mutableStateOf(profile.restingHr.toString()) }
    var ftp by remember(profile) { mutableStateOf(profile.ftpWatts.toString()) }
    var preferredSport by remember(profile) { mutableStateOf(SportType.fromRaw(profile.preferredSport)) }
    var showDatePicker by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            photoUrl = uri.toString()
        }
    }

    val previewHeightCm = heightInput.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 280.0) ?: 0.0
    val previewWeightKg = weightInput.replace(',', '.').toDoubleOrNull()?.let {
        Formatters.displayWeightToKg(it, unitSystem).coerceIn(0.0, 400.0)
    } ?: 0.0
    val previewProfile = remember(birthDateIso, gender, previewHeightCm, previewWeightKg) {
        profile.copy(
            birthDateIso = birthDateIso,
            gender = gender,
            heightCm = previewHeightCm,
            weightKg = previewWeightKg
        )
    }
    val previewAge = previewProfile.ageYears()
    val previewBmi = previewProfile.bmi()
    val previewSuggestedMaxHr = previewProfile.estimatedMaxHrByAge()

    fun commitSave() {
        val goalVal = goalInput.replace(',', '.').toDoubleOrNull()
        val goalKm = if (goalVal != null) {
            Formatters.displayDistanceToKm(goalVal, unitSystem)
        } else {
            40.0
        }
        onSave(
            AthleteProfileUpdatePayload(
                displayName = displayName.trim().ifBlank { "Athlete" },
                photoUrl = photoUrl.trim(),
                birthDateIso = birthDateIso.trim(),
                gender = gender.trim(),
                heightCm = previewHeightCm,
                weightKg = previewWeightKg,
                weeklyGoalKm = goalKm,
                maxHr = (maxHr.toIntOrNull() ?: 192).coerceIn(100, 230),
                restingHr = (restHr.toIntOrNull() ?: 54).coerceIn(30, 120),
                ftpWatts = (ftp.toIntOrNull() ?: 230).coerceIn(50, 600),
                preferredSport = preferredSport
            )
        )
    }

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
                                text = strings.athleteTargetsTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_profile_editor_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = strings.backLabel
                                )
                            }
                        },
                        actions = {
                            Button(
                                onClick = { commitSave() },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("save_profile_targets_btn")
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.saveLabel)
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
                        // 1. Display Picture & Name Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                if (photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = photoUrl,
                                        contentDescription = displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(CircleShape)
                                            .border(2.dp, VeloOrange, CircleShape)
                                            .testTag("profile_avatar_preview")
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(CircleShape)
                                            .background(VeloOrange.copy(alpha = 0.16f))
                                            .border(2.dp, VeloOrange.copy(alpha = 0.4f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = VeloOrange,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.testTag("pick_profile_photo_btn")
                                    ) {
                                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.changePhotoLabel)
                                    }
                                    if (photoUrl.isNotBlank()) {
                                        TextButton(
                                            onClick = { photoUrl = "" },
                                            modifier = Modifier.testTag("reset_profile_photo_btn")
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.resetPhotoLabel)
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it },
                                    label = { Text(strings.displayNameLabel) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("profile_display_name_input")
                                )
                            }
                        }

                        // 2. Biometrics Card (Birth Date, Gender, Height, Weight)
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
                                    text = strings.biometricsSectionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalCyan,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = birthDateIso,
                                        onValueChange = { birthDateIso = it },
                                        label = { Text(strings.birthDateLabel) },
                                        placeholder = { Text("1995-06-15") },
                                        supportingText = if (previewAge != null) {
                                            { Text("${strings.ageLabel}: $previewAge") }
                                        } else null,
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("profile_birthdate_input")
                                    )
                                    OutlinedButton(
                                        onClick = { showDatePicker = true },
                                        modifier = Modifier
                                            .height(52.dp)
                                            .testTag("profile_pick_birthdate_btn")
                                    ) {
                                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(strings.pickBirthDateLabel)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = strings.genderLabel,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            "MALE" to strings.genderMale,
                                            "FEMALE" to strings.genderFemale,
                                            "OTHER" to strings.genderOther
                                        ).forEach { (code, label) ->
                                            FilterChip(
                                                selected = gender.equals(code, ignoreCase = true),
                                                onClick = { gender = code },
                                                label = { Text(label) },
                                                modifier = Modifier.testTag("profile_gender_${code.lowercase()}")
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = heightInput,
                                        onValueChange = { heightInput = it },
                                        label = { Text(strings.heightCmLabel) },
                                        placeholder = { Text("172") },
                                        supportingText = if (unitSystem == UnitSystem.IMPERIAL && previewHeightCm > 0.0) {
                                            { Text(Formatters.formatHeight(previewHeightCm, UnitSystem.IMPERIAL)) }
                                        } else null,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("profile_height_input")
                                    )
                                    OutlinedTextField(
                                        value = weightInput,
                                        onValueChange = { weightInput = it },
                                        label = { Text("${strings.weightLabel} ($weightUnitLabel)") },
                                        placeholder = { Text(if (unitSystem == UnitSystem.IMPERIAL) "150.0" else "68.0") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("profile_weight_input")
                                    )
                                }

                                if (previewBmi != null || previewSuggestedMaxHr != null) {
                                    Surface(
                                        color = SignalCyan.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (previewBmi != null) {
                                                    Text(
                                                        text = "${strings.bmiLabel}: $previewBmi (${previewProfile.bmiCategory().orEmpty()})",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        color = EmeraldPeak,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                if (previewSuggestedMaxHr != null) {
                                                    Text(
                                                        text = "Est. MaxHR: $previewSuggestedMaxHr bpm",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontFamily = JetBrainsMonoFontFamily,
                                                        color = SignalCyan
                                                    )
                                                }
                                            }
                                            if (previewSuggestedMaxHr != null && maxHr.toIntOrNull() != previewSuggestedMaxHr) {
                                                TextButton(
                                                    onClick = { maxHr = previewSuggestedMaxHr.toString() },
                                                    modifier = Modifier.testTag("apply_age_max_hr_btn")
                                                ) {
                                                    Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = VeloOrange)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("${strings.applySuggestedMaxHr} ($previewSuggestedMaxHr bpm)")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Training Thresholds & Goals Card
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
                                    text = strings.thresholdsSectionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VeloOrange,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = goalInput,
                                        onValueChange = { goalInput = it },
                                        label = { Text("${strings.weeklyGoalLabel} ($distUnitLabel)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = ftp,
                                        onValueChange = { ftp = it },
                                        label = { Text(strings.ftpLabel) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = maxHr,
                                        onValueChange = { maxHr = it },
                                        label = { Text(strings.maxHrLabel) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = restHr,
                                        onValueChange = { restHr = it },
                                        label = { Text(strings.restHrLabel) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                SportSelectorGrid(
                                    selectedSport = preferredSport,
                                    onSelect = { preferredSport = it }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(birthDateIso) {
            parseIsoToUtcMillis(birthDateIso) ?: defaultBirthDateUtcMillis()
        }
        val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = 1930..currentYear,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            birthDateIso = formatUtcMillisToIso(millis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(strings.saveLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(strings.cancelLabel)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun parseIsoToUtcMillis(iso: String): Long? {
    if (iso.isBlank()) return null
    return runCatching {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        sdf.parse(iso.trim())?.time
    }.getOrNull()
}

private fun defaultBirthDateUtcMillis(): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(1995, Calendar.JUNE, 15, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun formatUtcMillisToIso(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date(millis))
}
