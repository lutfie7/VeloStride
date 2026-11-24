package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.GearItem
import com.example.data.model.SportType
import com.example.data.model.WorkoutActivity
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditActivityDialog(
    workout: WorkoutActivity,
    gearOptions: List<GearItem>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        sportType: SportType,
        gear: GearItem?,
        perceivedExertion: Int,
        privateActivity: Boolean,
        notes: String?,
        mediaUris: List<String>,
    ) -> Unit,
    onExportGpx: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var title by remember(workout.id) { mutableStateOf(workout.title) }
    var sportType by remember(workout.id) { mutableStateOf(workout.parsedSport) }
    var selectedGear by remember(workout.id, gearOptions) {
        mutableStateOf(
            gearOptions.firstOrNull { !workout.gearId.isNullOrBlank() && it.id == workout.gearId }
                ?: gearOptions.firstOrNull { !workout.gearName.isNullOrBlank() && it.name == workout.gearName }
        )
    }
    var rpe by remember(workout.id) { mutableFloatStateOf(workout.perceivedExertion.coerceIn(1, 10).toFloat()) }
    var isPrivate by remember(workout.id) { mutableStateOf(workout.privateActivity) }
    var notes by remember(workout.id) { mutableStateOf(workout.notes.orEmpty()) }
    var mediaUris by remember(workout.id) { mutableStateOf(workout.mediaUris) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val availableGear = remember(gearOptions, selectedGear) {
        gearOptions.filter { !it.retired || it.id == selectedGear?.id }
    }
    val hasGpsPoints = workout.decodedPoints.size >= 2

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { pickedUris ->
        if (pickedUris.isNotEmpty()) {
            pickedUris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            val merged = (mediaUris + pickedUris.map { it.toString() }).distinct().take(10)
            mediaUris = merged
        }
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
                                text = "Edit Activity",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("edit_activity_back_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Cancel editing")
                            }
                        },
                        actions = {
                            Button(
                                onClick = {
                                    onSave(
                                        title,
                                        sportType,
                                        selectedGear,
                                        rpe.roundToInt(),
                                        isPrivate,
                                        notes.takeIf { it.isNotBlank() },
                                        mediaUris
                                    )
                                },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .testTag("edit_activity_save_btn")
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
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
                        // Rename & Notes Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text("Activity Title (Rename)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("edit_activity_title_input")
                                )
                                SportSelectorGrid(
                                    selectedSport = sportType,
                                    onSelect = { sportType = it }
                                )
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text("Description / Notes") },
                                    placeholder = { Text("How did the session feel, weather, splits...") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("edit_activity_notes_input")
                                )
                            }
                        }

                        // Media Photos Card (Zero-permission Android Photo Picker)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = SignalCyan)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Activity Media (${mediaUris.size}/10)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = mediaUris.size < 10,
                                        modifier = Modifier.testTag("edit_activity_add_media_btn")
                                    ) {
                                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add Media")
                                    }
                                }

                                if (mediaUris.isEmpty()) {
                                    Text(
                                        text = "Add route or workout photos directly from your device gallery.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(mediaUris, key = { it }) { uriStr ->
                                            Box(
                                                modifier = Modifier
                                                    .size(110.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                            ) {
                                                AsyncImage(
                                                    model = uriStr,
                                                    contentDescription = "Attached workout photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                IconButton(
                                                    onClick = { mediaUris = mediaUris.filterNot { it == uriStr } },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.65f))
                                                        .testTag("edit_activity_remove_media_${uriStr.hashCode()}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Close,
                                                        contentDescription = "Remove photo",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Gear & Perceived Exertion Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Gear & Effort", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Gear Used",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 2.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedGear == null,
                                            onClick = { selectedGear = null },
                                            label = { Text("No Gear") },
                                            modifier = Modifier.testTag("edit_activity_gear_none")
                                        )
                                    }
                                    items(availableGear, key = { it.id }) { gear ->
                                        FilterChip(
                                            selected = selectedGear?.id == gear.id,
                                            onClick = {
                                                selectedGear = if (selectedGear?.id == gear.id) null else gear
                                            },
                                            label = {
                                                Text("${gear.name} (${gear.parsedCategory.label})", maxLines = 1)
                                            },
                                            modifier = Modifier.testTag("edit_activity_gear_${gear.id}")
                                        )
                                    }
                                }

                                Text(
                                    text = "Perceived Exertion (RPE): ${rpe.roundToInt()} / 10",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Slider(
                                    value = rpe,
                                    onValueChange = { rpe = it },
                                    valueRange = 1f..10f,
                                    steps = 8
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Rounded.Lock,
                                            contentDescription = null,
                                            tint = if (isPrivate) VeloOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("Private Activity", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Hide from public feed exports",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isPrivate,
                                        onCheckedChange = { isPrivate = it },
                                        modifier = Modifier.testTag("edit_activity_private_switch")
                                    )
                                }
                            }
                        }

                        // Export GPX & Delete Actions Card
                        if ((hasGpsPoints && onExportGpx != null) || onDelete != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Activity Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (hasGpsPoints && onExportGpx != null) {
                                        OutlinedButton(
                                            onClick = onExportGpx,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("edit_activity_export_gpx_btn")
                                        ) {
                                            Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Export .GPX Route")
                                        }
                                    }
                                    if (onDelete != null) {
                                        OutlinedButton(
                                            onClick = { showDeleteConfirm = true },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonPulse),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .testTag("edit_activity_delete_btn")
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Delete Activity")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Activity?") },
            text = { Text("Are you sure you want to permanently delete \"${workout.title}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonPulse,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_delete_activity_btn")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
