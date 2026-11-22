package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AthleteProfile
import com.example.data.model.SportType
import com.example.data.model.UiState
import com.example.ui.components.SportSelectorGrid
import com.example.ui.screens.ActivityDetailScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.ManualWorkoutDialog
import com.example.ui.screens.MapsRoutesScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.RecordScreen
import com.example.ui.screens.WearablesScreen

object VeloRoutes {
    const val FEED = "feed"
    const val MAPS = "maps"
    const val RECORD = "record"
    const val PROGRESS = "progress"
    const val DEVICES = "devices"
    const val DETAIL = "detail/{workoutId}"

    fun detailRoute(workoutId: String) = "detail/$workoutId"
}

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val gearState by viewModel.gearState.collectAsStateWithLifecycle()
    val profile = (profileState as? UiState.Success)?.data ?: AthleteProfile()
    val activeGear = (gearState as? UiState.Success)?.data?.filter { !it.retired }.orEmpty()

    var showManualDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.autoSyncBandWorkoutsIfPermitted()
    }

    val tabs = remember {
        listOf(
            NavTabItem(VeloRoutes.FEED, R.string.nav_feed, Icons.Rounded.Home, "nav_tab_feed"),
            NavTabItem(VeloRoutes.MAPS, R.string.nav_maps, Icons.Rounded.Explore, "nav_tab_maps"),
            NavTabItem(VeloRoutes.RECORD, R.string.nav_record, Icons.Rounded.RadioButtonChecked, "nav_tab_record"),
            NavTabItem(VeloRoutes.PROGRESS, R.string.nav_progress, Icons.Rounded.Insights, "nav_tab_progress"),
            NavTabItem(VeloRoutes.DEVICES, R.string.nav_devices, Icons.Rounded.Watch, "nav_tab_devices")
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isDetailScreen = currentRoute?.startsWith("detail/") == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isDetailScreen) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier.testTag("toggle_theme_btn")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "Toggle theme"
                                )
                            }
                            IconButton(
                                onClick = { showProfileDialog = true },
                                modifier = Modifier.testTag("athlete_profile_btn")
                            ) {
                                if (profile.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = profile.photoUrl,
                                        contentDescription = "Athlete profile",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(Icons.Rounded.Person, contentDescription = "Athlete settings")
                                }
                            }
                            IconButton(
                                onClick = onSignOut,
                                modifier = Modifier.testTag("sign_out_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = stringResource(R.string.sign_out)
                                )
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (currentRoute == VeloRoutes.FEED) {
                    FloatingActionButton(
                        onClick = { showManualDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_log_manual_workout")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Log activity manually")
                    }
                }
            },
            bottomBar = {
                if (!useRail && !isDetailScreen) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isDetailScreen) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        tabs.forEach { tab ->
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = VeloRoutes.FEED
                    ) {
                        composable(VeloRoutes.FEED) {
                            FeedScreen(
                                viewModel = viewModel,
                                onOpenWorkoutDetail = { id ->
                                    navController.navigate(VeloRoutes.detailRoute(id))
                                },
                                onNavigateToRecord = { navigateToTab(VeloRoutes.RECORD) },
                                onNavigateToDevices = { navigateToTab(VeloRoutes.DEVICES) },
                                onOpenManualEntry = { showManualDialog = true }
                            )
                        }
                        composable(VeloRoutes.MAPS) {
                            MapsRoutesScreen(viewModel = viewModel)
                        }
                        composable(VeloRoutes.RECORD) {
                            RecordScreen(
                                viewModel = viewModel,
                                onWorkoutSaved = { newId ->
                                    navController.navigate(VeloRoutes.detailRoute(newId))
                                },
                                onNavigateToDevices = { navigateToTab(VeloRoutes.DEVICES) },
                                onOpenManualEntry = { showManualDialog = true }
                            )
                        }
                        composable(VeloRoutes.PROGRESS) {
                            ProgressScreen(viewModel = viewModel)
                        }
                        composable(VeloRoutes.DEVICES) {
                            WearablesScreen(viewModel = viewModel)
                        }
                        composable(
                            route = VeloRoutes.DETAIL,
                            arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
                        ) { entry ->
                            val workoutId = entry.arguments?.getString("workoutId").orEmpty()
                            ActivityDetailScreen(
                                workoutId = workoutId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        ManualWorkoutDialog(
            gearOptions = activeGear,
            defaultSport = SportType.fromRaw(profile.preferredSport),
            onDismiss = { showManualDialog = false },
            onSave = { title, sport, distKm, mins, eleM, avgHr, rpe, gear ->
                showManualDialog = false
                viewModel.saveManualWorkout(
                    title = title,
                    sportType = sport,
                    distanceKm = distKm,
                    durationMinutes = mins,
                    elevationMeters = eleM,
                    avgHr = avgHr,
                    perceivedExertion = rpe,
                    gear = gear,
                    onSaved = {}
                )
            }
        )
    }

    if (showProfileDialog) {
        AthleteProfileDialog(
            profile = profile,
            onDismiss = { showProfileDialog = false },
            onSave = { name, goalKm, maxHr, restHr, ftp, sport ->
                showProfileDialog = false
                viewModel.updateProfileTargets(name, goalKm, maxHr, restHr, ftp, sport)
            }
        )
    }
}

@Composable
private fun AthleteProfileDialog(
    profile: AthleteProfile,
    onDismiss: () -> Unit,
    onSave: (String, Double, Int, Int, Int, SportType) -> Unit,
) {
    var name by remember(profile) { mutableStateOf(profile.displayName) }
    var goalKm by remember(profile) { mutableStateOf(profile.weeklyDistanceGoalKm.toInt().toString()) }
    var maxHr by remember(profile) { mutableStateOf(profile.maxHr.toString()) }
    var restHr by remember(profile) { mutableStateOf(profile.restingHr.toString()) }
    var ftp by remember(profile) { mutableStateOf(profile.ftpWatts.toString()) }
    var preferredSport by remember(profile) { mutableStateOf(SportType.fromRaw(profile.preferredSport)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Athlete Thresholds & Goals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = goalKm,
                        onValueChange = { goalKm = it },
                        label = { Text("Weekly Goal (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = ftp,
                        onValueChange = { ftp = it },
                        label = { Text("FTP (Watts)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxHr,
                        onValueChange = { maxHr = it },
                        label = { Text("Max HR (bpm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = restHr,
                        onValueChange = { restHr = it },
                        label = { Text("Rest HR (bpm)") },
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
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.ifBlank { "Athlete" },
                        goalKm.toDoubleOrNull() ?: 40.0,
                        maxHr.toIntOrNull() ?: 192,
                        restHr.toIntOrNull() ?: 54,
                        ftp.toIntOrNull() ?: 230,
                        preferredSport
                    )
                },
                modifier = Modifier.testTag("save_profile_targets_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
