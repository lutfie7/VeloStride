package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.Formatters
import com.example.core.GpxSerializer
import com.example.core.MapLayerType
import com.example.core.MapProjection
import com.example.data.model.MapsGroundingResult
import com.example.data.model.SavedRoute
import com.example.data.model.SegmentRecord
import com.example.data.model.SportType
import com.example.data.model.TrackPoint
import com.example.data.model.UiState
import com.example.data.model.WorkoutActivity
import com.example.ui.MainViewModel
import com.example.ui.components.MapAttributionBadge
import com.example.ui.components.MapControlsOverlay
import com.example.ui.components.MapTileBackground
import com.example.ui.components.MetricCell
import com.example.ui.components.MultiTrackMapCanvas
import com.example.ui.components.RoutePolylineCanvas
import com.example.ui.components.SportBadge
import com.example.ui.components.SportSelectorGrid
import com.example.ui.components.mapTransformGestures
import com.example.ui.theme.EmeraldPeak
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VeloOrange
import com.google.android.gms.location.LocationServices
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsRoutesScreen(viewModel: MainViewModel) {
    val routesState by viewModel.routesState.collectAsStateWithLifecycle()
    val segmentsState by viewModel.segmentsState.collectAsStateWithLifecycle()
    val workoutsState by viewModel.workoutsState.collectAsStateWithLifecycle()
    val mapsGroundingState by viewModel.mapsGroundingState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var showAddSegmentDialog by remember { mutableStateOf(false) }

    val routes = (routesState as? UiState.Success)?.data.orEmpty()
    val segments = (segmentsState as? UiState.Success)?.data.orEmpty()
    val workouts = (workoutsState as? UiState.Success)?.data.orEmpty()

    val context = LocalContext.current
    var pendingExportRoute by remember { mutableStateOf<SavedRoute?>(null) }

    val importRouteGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importRouteFromGpx(context, uri)
        }
    }

    val exportRouteGpxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val route = pendingExportRoute
        pendingExportRoute = null
        if (uri != null && route != null) {
            viewModel.exportRouteGpxToUri(context, uri, route)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            listOf("Explorer", "Nearby Maps", "Route Builder", "Heatmap", "Segments").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedSubTab == idx,
                    onClick = { selectedSubTab = idx },
                    text = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("maps_subtab_$idx")
                )
            }
        }

        when (selectedSubTab) {
            0 -> RouteExplorerList(
                routes = routes,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshAllData() },
                onToggleStar = { viewModel.toggleRouteStar(it) },
                onOpenBuilder = { selectedSubTab = 2 },
                onOpenNearbyMaps = { selectedSubTab = 1 },
                onImportGpxRoute = {
                    importRouteGpxLauncher.launch(
                        arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")
                    )
                },
                onExportGpxRoute = { route ->
                    pendingExportRoute = route
                    exportRouteGpxLauncher.launch(GpxSerializer.suggestedGpxFileName(route.name))
                }
            )
            1 -> NearbyMapsGroundingView(
                groundingState = mapsGroundingState,
                onSearch = { query, lat, lng ->
                    viewModel.searchNearbyWithMapsGrounding(query, lat, lng)
                }
            )
            2 -> InteractiveRouteBuilder(
                workouts = workouts,
                onSaveRoute = { name, sport, surface, pts, distM, eleM ->
                    viewModel.saveCustomRoute(name, sport, surface, pts, distM, eleM)
                    selectedSubTab = 0
                }
            )
            3 -> PersonalHeatmapView(
                workouts = workouts,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshAllData() }
            )
            4 -> SegmentsLeaderboardView(
                segments = segments,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshAllData() },
                onToggleStar = { viewModel.toggleSegmentStar(it) },
                onOpenAddSegment = { showAddSegmentDialog = true }
            )
        }
    }

    if (showAddSegmentDialog) {
        AddSegmentDialog(
            onDismiss = { showAddSegmentDialog = false },
            onConfirm = { name, sport, distM, eleM, prSec, komSec ->
                viewModel.saveCustomSegment(name, sport, distM, eleM, prSec, komSec)
                showAddSegmentDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteExplorerList(
    routes: List<SavedRoute>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleStar: (SavedRoute) -> Unit,
    onOpenBuilder: () -> Unit,
    onOpenNearbyMaps: () -> Unit,
    onImportGpxRoute: () -> Unit,
    onExportGpxRoute: (SavedRoute) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .testTag("maps_explorer_pull_refresh")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_route_mountain),
                        contentDescription = "Mountain cycling and trail route",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC0C0F14))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "ROUTE EXPLORER & BUILDER",
                            style = MaterialTheme.typography.labelSmall,
                            color = VeloOrange
                        )
                        Text(
                            text = "Discover climbs, trails & custom loops",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Saved Routes (${routes.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportGpxRoute,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1.15f)
                            .testTag("import_gpx_route_btn")
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Import .GPX", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onOpenNearbyMaps,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("open_nearby_maps_btn")
                    ) {
                        Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nearby", maxLines = 1)
                    }
                    Button(
                        onClick = onOpenBuilder,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("open_route_builder_btn")
                    ) {
                        Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Build", maxLines = 1)
                    }
                }
            }
        }

        if (routes.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_route_trail),
                            contentDescription = "Forest trail route preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("No Custom Routes Saved", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap waypoints in Route Builder to craft a custom Run, Trail Run, Hike, or Cycle route and sync it to Firestore.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(routes, key = { it.id }) { route ->
                val sport = SportType.fromRaw(route.sportType)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                SportBadge(sport)
                                Spacer(Modifier.height(4.dp))
                                Text(route.name, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "${route.surfaceType} • Est. ${route.estimatedMinutes} min",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (route.decodedWaypoints.size >= 2) {
                                    IconButton(
                                        onClick = { onExportGpxRoute(route) },
                                        modifier = Modifier.testTag("export_route_gpx_${route.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FileDownload,
                                            contentDescription = "Export route GPX",
                                            tint = SignalCyan
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onToggleStar(route) },
                                    modifier = Modifier.testTag("star_route_${route.id}")
                                ) {
                                    Icon(
                                        imageVector = if (route.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Star route",
                                        tint = if (route.starred) VeloOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        RoutePolylineCanvas(
                            points = route.decodedWaypoints,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(172.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell("Distance", Formatters.formatDistanceKm(route.distanceMeters), valueColor = VeloOrange)
                            MetricCell("Elev Gain", Formatters.formatElevation(route.elevationGainMeters))
                            MetricCell("Waypoints", "${route.decodedWaypoints.size} pts")
                        }
                    }
                }
            }
        }
    }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NearbyMapsGroundingView(
    groundingState: UiState<MapsGroundingResult>?,
    onSearch: (String, Double?, Double?) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var deviceLat by remember { mutableStateOf<Double?>(null) }
    var deviceLng by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (hasLocation) {
            runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            deviceLat = loc.latitude
                            deviceLng = loc.longitude
                        }
                    }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = SignalCyan)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Nearby Trails & Workout Spots", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Live place recommendations grounded with Google Maps (gemini-4-hydrogen)",
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
                            "Best running tracks & parks nearby" to "Running Parks",
                            "Hiking trails & hill climbs near me" to "Hiking Trails",
                            "Cycling routes & bike-friendly cafes nearby" to "Cycling Spots",
                            "Outdoor workout & calisthenics parks nearby" to "Workout Parks"
                        ).forEachIndexed { idx, (prompt, label) ->
                            AssistChip(
                                onClick = {
                                    query = prompt
                                    onSearch(prompt, deviceLat, deviceLng)
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("maps_grounding_chip_$idx")
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Find trails, hills, cycling stops...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("maps_grounding_input")
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val target = query.ifBlank { "Best running parks and trails near me" }
                                onSearch(target, deviceLat, deviceLng)
                            },
                            modifier = Modifier.testTag("maps_grounding_search_btn")
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search Google Maps", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        when (groundingState) {
            is UiState.Loading -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Searching live Google Maps places...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = groundingState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                val res = groundingState.data
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = res.answerText,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Coordinates: ${String.format("%.4f", res.latitude)}, ${String.format("%.4f", res.longitude)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (res.places.isNotEmpty()) {
                    item {
                        Text(
                            text = "Sources",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(res.places) { place ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = place.uri.isNotBlank()) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(place.uri)))
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    // Required exact Google Maps attribution per grounding guidelines
                                    Text(
                                        text = "Google Maps",
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (place.uri.isNotBlank()) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = "Open in Google Maps",
                                        tint = SignalCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            null -> {}
        }
    }
}

@SuppressLint("MissingPermission", "UnusedBoxWithConstraintsScope")
@Composable
private fun InteractiveRouteBuilder(
    workouts: List<WorkoutActivity>,
    onSaveRoute: (String, SportType, String, List<TrackPoint>, Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val workoutSeed = workouts.firstOrNull()?.decodedPoints?.firstOrNull()
    var centerLat by remember { mutableDoubleStateOf(workoutSeed?.lat ?: -6.2185) }
    var centerLng by remember { mutableDoubleStateOf(workoutSeed?.lng ?: 106.8020) }
    var zoomLevel by remember { mutableIntStateOf(14) }

    var selectedLayer by remember(darkTheme) {
        mutableStateOf(if (darkTheme) MapLayerType.DARK else MapLayerType.STANDARD)
    }
    var is3DView by remember { mutableStateOf(false) }
    var bearingDeg by remember { mutableFloatStateOf(0f) }

    val baseViewport = remember(centerLat, centerLng, zoomLevel) {
        MapProjection.computeViewportForCenter(
            centerLat = centerLat,
            centerLng = centerLng,
            zoom = zoomLevel
        )
    }
    var activeViewport by remember(baseViewport) { mutableStateOf(baseViewport) }
    val isTransformed = activeViewport != baseViewport || abs(bearingDeg) > 1f

    val trackPoints = remember { mutableStateListOf<TrackPoint>() }
    var routeName by remember { mutableStateOf("") }
    var selectedSport by remember { mutableStateOf(SportType.RUN) }
    var surfaceType by remember { mutableStateOf("Paved") }

    LaunchedEffect(Unit) {
        if (workoutSeed == null) {
            val hasLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) {
                runCatching {
                    LocationServices.getFusedLocationProviderClient(context).lastLocation
                        .addOnSuccessListener { loc ->
                            if (loc != null && trackPoints.isEmpty()) {
                                centerLat = loc.latitude
                                centerLng = loc.longitude
                            }
                        }
                }
            }
        }
    }

    val totalDistanceMeters = remember(trackPoints.size) {
        if (trackPoints.size < 2) 0.0
        else (1 until trackPoints.size).sumOf { idx ->
            val a = trackPoints[idx - 1]
            val b = trackPoints[idx]
            Formatters.haversineMeters(a.lat, a.lng, b.lat, b.lng)
        }
    }
    val totalElevationGain = remember(trackPoints.size) {
        if (trackPoints.size < 2) 0.0
        else (1 until trackPoints.size).sumOf { idx ->
            (trackPoints[idx].ele - trackPoints[idx - 1].ele).coerceAtLeast(0.0)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Interactive Route Map", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Tap to plot GPS points • Pinch/drag to zoom & pan",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    if (trackPoints.isNotEmpty()) {
                                        trackPoints.removeAt(trackPoints.lastIndex)
                                    }
                                },
                                enabled = trackPoints.isNotEmpty(),
                                modifier = Modifier.testTag("route_builder_undo_btn")
                            ) {
                                Icon(Icons.Rounded.Undo, contentDescription = "Undo waypoint")
                            }
                            IconButton(
                                onClick = { trackPoints.clear() },
                                enabled = trackPoints.isNotEmpty(),
                                modifier = Modifier.testTag("route_builder_clear_btn")
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear route")
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selectedLayer == MapLayerType.DARK || darkTheme) Color(0xFF10151E) else Color(0xFFECEAE4))
                    ) {
                        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(16f)
                        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(16f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .mapTransformGestures(
                                    gestureKey = Unit,
                                    allowOneFingerPan = true
                                ) { centroid, pan, zoom, rotation ->
                                    if (rotation != 0f) {
                                        bearingDeg = ((bearingDeg + rotation) % 360f)
                                    }
                                    var next = activeViewport
                                    if (zoom != 1f) {
                                        next = MapProjection.zoomAroundFocus(
                                            viewport = next,
                                            focusX = centroid.x,
                                            focusY = centroid.y,
                                            widthPx = widthPx,
                                            heightPx = heightPx,
                                            zoomFactor = zoom,
                                            bearingDeg = bearingDeg
                                        )
                                    }
                                    if (pan != Offset.Zero) {
                                        next = MapProjection.panViewport(next, pan.x, pan.y, bearingDeg)
                                    }
                                    activeViewport = next
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (is3DView) {
                                            rotationX = 32f
                                            cameraDistance = 24f * density.density
                                            scaleX = 1.16f
                                            scaleY = 1.16f
                                        }
                                        rotationZ = bearingDeg
                                    }
                            ) {
                                MapTileBackground(
                                    viewport = activeViewport,
                                    widthPx = widthPx,
                                    heightPx = heightPx,
                                    darkTheme = darkTheme,
                                    layerType = selectedLayer
                                )

                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(activeViewport) {
                                            detectTapGestures { tap ->
                                                val w = size.width.toFloat().coerceAtLeast(1f)
                                                val h = size.height.toFloat().coerceAtLeast(1f)
                                                val (lat, lng) = MapProjection.unproject(tap.x, tap.y, activeViewport, w, h)
                                                val ele = 18.0 + abs(sin((lat + lng) * 120.0)) * 65.0
                                                trackPoints.add(TrackPoint(lat = lat, lng = lng, ele = ele, hr = 0))
                                            }
                                        }
                                        .testTag("route_builder_canvas")
                                ) {
                                    val mapped = trackPoints.map { pt ->
                                        val (px, py) = MapProjection.project(pt.lat, pt.lng, activeViewport, size.width, size.height)
                                        Offset(px, py)
                                    }
                                    if (mapped.size >= 2) {
                                        val path = Path().apply {
                                            moveTo(mapped.first().x, mapped.first().y)
                                            for (i in 1 until mapped.size) {
                                                lineTo(mapped[i].x, mapped[i].y)
                                            }
                                        }
                                        drawPath(
                                            path,
                                            Color.Black.copy(alpha = if (selectedLayer == MapLayerType.DARK) 0.45f else 0.25f),
                                            style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                        drawPath(
                                            path,
                                            VeloOrange.copy(alpha = 0.28f),
                                            style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                        drawPath(
                                            path,
                                            VeloOrange,
                                            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                        )
                                    }
                                    mapped.forEachIndexed { index, pt ->
                                        val color = when {
                                            index == 0 -> EmeraldPeak
                                            index == mapped.lastIndex -> Color(0xFFEF4444)
                                            else -> SignalCyan
                                        }
                                        drawCircle(color, radius = 10f, center = pt)
                                        drawCircle(Color.White, radius = 4f, center = pt)
                                    }
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Zoom ${activeViewport.zoom}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        MapControlsOverlay(
                            layerType = selectedLayer,
                            onSelectLayer = { selectedLayer = it },
                            is3DView = is3DView,
                            onToggle3D = { is3DView = !is3DView },
                            bearingDeg = bearingDeg,
                            onResetNorth = { bearingDeg = 0f },
                            onZoomIn = { activeViewport = MapProjection.zoomViewport(activeViewport, 1.45f) },
                            onZoomOut = { activeViewport = MapProjection.zoomViewport(activeViewport, 0.70f) },
                            isTransformed = isTransformed,
                            onResetView = {
                                activeViewport = baseViewport
                                bearingDeg = 0f
                            },
                            testTagPrefix = "builder_map",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        )

                        MapAttributionBadge(
                            darkTheme = darkTheme,
                            layerType = selectedLayer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricCell("Distance", Formatters.formatDistanceKm(totalDistanceMeters), valueColor = VeloOrange)
                        MetricCell("Est. Elevation", Formatters.formatElevation(totalElevationGain))
                        MetricCell("Waypoints", "${trackPoints.size}")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        label = { Text("Route Name") },
                        placeholder = { Text("e.g., Sunset Trail Tempo Loop") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("route_name_input")
                    )
                    SportSelectorGrid(
                        selectedSport = selectedSport,
                        onSelect = { selectedSport = it }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Paved", "Trail", "Gravel", "Mixed").forEach { surface ->
                            FilterChip(
                                selected = surfaceType == surface,
                                onClick = { surfaceType = surface },
                                label = { Text(surface) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onSaveRoute(
                                routeName.ifBlank { "${selectedSport.label} Custom Loop" },
                                selectedSport,
                                surfaceType,
                                trackPoints.toList(),
                                totalDistanceMeters,
                                totalElevationGain
                            )
                            trackPoints.clear()
                            routeName = ""
                        },
                        enabled = trackPoints.size >= 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_custom_route_btn")
                    ) {
                        Text("Save Route to Cloud")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalHeatmapView(
    workouts: List<WorkoutActivity>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val allTracks = remember(workouts) {
        workouts.map { it.decodedPoints }.filter { it.size >= 2 }
    }
    val allPoints = remember(allTracks) { allTracks.flatten() }
    val totalKm = workouts.sumOf { it.distanceMeters } / 1000.0

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .testTag("maps_heatmap_pull_refresh")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = VeloOrange)
                            Spacer(Modifier.width(8.dp))
                            Text("Personal Activity Heatmap", style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Visualizes cumulative GPS track density across all ${workouts.size} recorded activities.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        MultiTrackMapCanvas(
                            tracks = allTracks,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell("GPS Tracks", "${allTracks.size}")
                            MetricCell("Cumulative Dist", "${String.format("%.1f", totalKm)} km", valueColor = VeloOrange)
                            MetricCell("Track Points", "${allPoints.size}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentsLeaderboardView(
    segments: List<SegmentRecord>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleStar: (SegmentRecord) -> Unit,
    onOpenAddSegment: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .testTag("maps_segments_pull_refresh")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("KOM & PR Segments", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Compare your personal records against local crown holders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onOpenAddSegment,
                    modifier = Modifier.testTag("add_segment_btn")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (segments.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, tint = SignalCyan)
                        Spacer(Modifier.height(6.dp))
                        Text("No Segments Tracked Yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Create a segment above or load the Sample Training Block on the Home tab to inspect KOM & PR leaderboards.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(segments, key = { it.id }) { seg ->
                val sport = SportType.fromRaw(seg.sportType)
                val gapSec = (seg.prTimeSeconds - seg.komTimeSeconds).coerceAtLeast(0)
                val isKom = seg.prTimeSeconds <= seg.komTimeSeconds

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                SportBadge(sport)
                                Spacer(Modifier.height(4.dp))
                                Text(seg.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isKom) {
                                    Surface(
                                        color = VeloOrange.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.EmojiEvents, contentDescription = "KOM Crown", tint = VeloOrange, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("KOM", style = MaterialTheme.typography.labelSmall, color = VeloOrange, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onToggleStar(seg) },
                                    modifier = Modifier.testTag("star_segment_${seg.id}")
                                ) {
                                    Icon(
                                        imageVector = if (seg.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = "Star segment",
                                        tint = if (seg.starred) VeloOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricCell("Distance", Formatters.formatDistanceKm(seg.distanceMeters))
                            MetricCell("Avg Grade", "${seg.avgGradePercent}%")
                            MetricCell("Your PR", Formatters.formatDuration(seg.prTimeSeconds.toLong()), valueColor = SignalCyan)
                            MetricCell("KOM", Formatters.formatDuration(seg.komTimeSeconds.toLong()), valueColor = VeloOrange)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isKom) {
                                "You hold the Crown! (${seg.attemptCount} attempts)"
                            } else {
                                "Gap to KOM: +${Formatters.formatDuration(gapSec.toLong())} • ${seg.attemptCount} attempts"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isKom) EmeraldPeak else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AddSegmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, SportType, Double, Double, Int, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf(SportType.RUN) }
    var distMeters by remember { mutableStateOf("1200") }
    var eleMeters by remember { mutableStateOf("24") }
    var prSec by remember { mutableStateOf("260") }
    var komSec by remember { mutableStateOf("225") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Segment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Segment Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SportSelectorGrid(selectedSport = sport, onSelect = { sport = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = distMeters,
                        onValueChange = { distMeters = it },
                        label = { Text("Meters") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = eleMeters,
                        onValueChange = { eleMeters = it },
                        label = { Text("Elev (m)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = prSec,
                        onValueChange = { prSec = it },
                        label = { Text("PR (sec)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = komSec,
                        onValueChange = { komSec = it },
                        label = { Text("KOM (sec)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.ifBlank { "${sport.label} Segment" },
                        sport,
                        distMeters.toDoubleOrNull() ?: 1000.0,
                        eleMeters.toDoubleOrNull() ?: 15.0,
                        prSec.toIntOrNull() ?: 260,
                        komSec.toIntOrNull() ?: 220
                    )
                },
                modifier = Modifier.testTag("confirm_add_segment_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
