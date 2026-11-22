package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SportType
import com.example.ui.theme.JetBrainsMonoFontFamily

fun sportIcon(sport: SportType): ImageVector = when (sport) {
    SportType.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
    SportType.TRAIL_RUN -> Icons.Rounded.Terrain
    SportType.HIKE -> Icons.Rounded.Landscape
    SportType.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
    SportType.CYCLE, SportType.GRAVEL_RIDE, SportType.MTB -> Icons.AutoMirrored.Rounded.DirectionsBike
    SportType.WORKOUT, SportType.ELLIPTICAL -> Icons.Rounded.FitnessCenter
    SportType.SWIM -> Icons.Rounded.Pool
    SportType.BADMINTON -> Icons.Rounded.SportsTennis
    SportType.YOGA -> Icons.Rounded.SelfImprovement
    SportType.ROWING -> Icons.Rounded.Rowing
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportSelectorGrid(
    selectedSport: SportType,
    onSelect: (SportType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by remember { mutableStateOf(!selectedSport.isPrimary) }
    val listToShow = if (showAll) SportType.entries else SportType.primarySports

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Primary & General Sports",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.testTag("toggle_all_sports_btn")
            ) {
                Text(if (showAll) "Primary 6" else "All (${SportType.entries.size})")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listToShow.forEach { sport ->
                val selected = sport == selectedSport
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(sport) },
                    label = { Text(sport.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = sportIcon(sport),
                            contentDescription = sport.label,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("sport_chip_${sport.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
fun SportBadge(sport: SportType, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = sportIcon(sport),
            contentDescription = sport.label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = sport.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
