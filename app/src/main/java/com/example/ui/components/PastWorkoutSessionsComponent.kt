package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.ActivityShareHelper
import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.data.model.WorkoutActivity
import com.example.data.model.WorkoutHistoryStats
import com.example.data.model.WorkoutSessionSummary
import com.example.data.model.WorkoutSortOrder
import com.example.ui.theme.CrimsonPulse
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange

@Composable
fun PastWorkoutHistorySummaryCard(
    stats: WorkoutHistoryStats,
    sortOrder: WorkoutSortOrder,
    onSortOrderChange: (WorkoutSortOrder) -> Unit,
    onImportGpx: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = VeloOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Past Workout Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onImportGpx != null) {
                        OutlinedButton(
                            onClick = onImportGpx,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("import_gpx_workout_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.UploadFile,
                                contentDescription = "Import GPX file",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(".GPX", maxLines = 1)
                        }
                    }
                    Surface(
                        color = VeloOrange.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${stats.sessionCount} logged",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = VeloOrange,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = "Total Dist",
                    value = Formatters.formatDistanceKm(stats.totalDistanceMeters),
                    valueColor = VeloOrange
                )
                MetricCell(
                    label = "Total Time",
                    value = Formatters.formatDurationCompact(stats.totalDurationSeconds)
                )
                MetricCell(
                    label = "Total Elev",
                    value = "+${stats.totalElevationGainMeters.toInt()} m",
                    valueColor = SignalCyan
                )
            }

            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WorkoutSortOrder.entries) { order ->
                    FilterChip(
                        selected = sortOrder == order,
                        onClick = { onSortOrderChange(order) },
                        label = { Text("Sort: ${order.label}") },
                        modifier = Modifier.testTag("workout_sort_${order.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
fun PastWorkoutSessionCard(
    session: WorkoutSessionSummary,
    onClick: () -> Unit,
    onToggleKudos: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("workout_card_${session.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SportBadge(session.sportType)
                Text(
                    text = Formatters.formatDateTime(session.sourceActivity.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!session.deviceName.isNullOrBlank() || !session.gearName.isNullOrBlank()) {
                val deviceText = session.deviceName?.let { "via $it" }.orEmpty()
                val gearText = session.gearName?.let {
                    if (deviceText.isNotBlank()) " • $it" else it
                }.orEmpty()
                Text(
                    text = "$deviceText$gearText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell(
                    label = "Distance",
                    value = Formatters.formatDistanceKm(session.distanceMeters),
                    valueColor = VeloOrange
                )
                MetricCell(
                    label = "Duration",
                    value = Formatters.formatDuration(session.durationSeconds)
                )
                MetricCell(
                    label = "Elev Gain",
                    value = "+${session.elevationGainMeters.toInt()} m",
                    valueColor = SignalCyan
                )
                MetricCell(
                    label = if (session.sportType.isPaceBased) "Avg Pace" else "Avg Speed",
                    value = if (session.sportType.isPaceBased) {
                        Formatters.formatPace(session.avgPaceSecPerKm)
                    } else {
                        Formatters.formatSpeed(session.avgSpeedKmh)
                    }
                )
            }

            Spacer(Modifier.height(12.dp))
            RoutePolylineCanvas(
                points = session.routePoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Effort ${session.relativeEffort}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = SignalCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terrain,
                                contentDescription = null,
                                tint = SignalCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = session.elevationSource,
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalCyan
                            )
                        }
                    }
                    if (session.avgHeartRate > 0) {
                        Text(
                            text = "${session.avgHeartRate} bpm",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = CrimsonPulse
                        )
                    }
                    if (session.prCount > 0) {
                        Surface(
                            color = EmeraldPeak.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${session.prCount} PR",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldPeak,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (session.hasAiInsight) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI insight saved",
                            tint = SignalCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.testTag("share_activity_btn_${session.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share Activity",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleKudos,
                        modifier = Modifier.testTag("kudos_btn_${session.id}")
                    ) {
                        Icon(
                            imageVector = if (session.kudosGiven) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Give Kudos",
                            tint = if (session.kudosGiven) VeloOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${session.kudosCount}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }
            }
        }
    }
}

@Composable
fun ShareActivityDialog(
    workout: WorkoutActivity,
    onExportGpx: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val previewBitmap = remember(workout.id, workout.elevationGainMeters, workout.distanceMeters) {
        ActivityShareHelper.renderShareCardBitmap(workout).asImageBitmap()
    }
    val hasGpsPoints = workout.decodedPoints.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share & Export Activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    bitmap = previewBitmap,
                    contentDescription = "Shareable activity card preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1080f / 1350f)
                        .clip(RoundedCornerShape(14.dp))
                )
                Button(
                    onClick = {
                        ActivityShareHelper.shareAsImageCard(context, workout)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_image_card_btn")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Image Card")
                }
                OutlinedButton(
                    onClick = {
                        ActivityShareHelper.shareAsText(context, workout)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_text_summary_btn")
                ) {
                    Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Text Summary")
                }
                if (hasGpsPoints) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onExportGpx != null) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onExportGpx()
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_gpx_file_btn")
                            ) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save .GPX", maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val xml = GpxSerializer.buildWorkoutGpxXml(workout)
                                GpxSerializer.shareGpxFile(context, workout.title, xml)
                                onDismiss()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_gpx_file_btn")
                        ) {
                            Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share .GPX", maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
