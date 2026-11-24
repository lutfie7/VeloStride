package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.UnitSystem
import com.example.core.rememberAppStrings
import com.example.data.model.AthleteProfile
import com.example.data.model.SportType
import com.example.data.model.UiState
import com.example.ui.components.AthleteProfileDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.VeloStrideWordmark
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
    val labelSelector: (com.example.core.AppStrings) -> String,
    val icon: ImageVector,
    val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    viewModel: MainViewModel,
    userEmail: String = "",
    isDarkTheme: Boolean,
    selectedLanguage: AppLanguage = AppLanguage.ENGLISH_US,
    selectedUnitSystem: UnitSystem = UnitSystem.METRIC,
    onToggleTheme: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onSelectUnitSystem: (UnitSystem) -> Unit = {},
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = rememberAppStrings()

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val gearState by viewModel.gearState.collectAsStateWithLifecycle()
    val profile = (profileState as? UiState.Success)?.data ?: AthleteProfile()
    val activeGear = (gearState as? UiState.Success)?.data?.filter { !it.retired }.orEmpty()

    var showManualDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val tabs = remember {
        listOf(
            NavTabItem(VeloRoutes.FEED, { it.navFeed }, Icons.Rounded.Home, "nav_tab_feed"),
            NavTabItem(VeloRoutes.MAPS, { it.navMaps }, Icons.Rounded.Explore, "nav_tab_maps"),
            NavTabItem(VeloRoutes.RECORD, { it.navRecord }, Icons.Rounded.RadioButtonChecked, "nav_tab_record"),
            NavTabItem(VeloRoutes.PROGRESS, { it.navProgress }, Icons.Rounded.Insights, "nav_tab_progress"),
            NavTabItem(VeloRoutes.DEVICES, { it.navDevices }, Icons.Rounded.Watch, "nav_tab_devices")
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
                            VeloStrideWordmark(
                                fontSize = 22.sp,
                                onDarkSurface = isDarkTheme,
                                showCrest = true,
                                crestSize = 22.dp
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier.testTag("toggle_theme_btn")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = strings.toggleThemeTooltip
                                )
                            }
                            IconButton(
                                onClick = { showProfileDialog = true },
                                modifier = Modifier.testTag("athlete_profile_btn")
                            ) {
                                if (profile.photoUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = profile.photoUrl,
                                        contentDescription = strings.profileTooltip,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Icon(Icons.Rounded.Person, contentDescription = strings.profileTooltip)
                                }
                            }
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier.testTag("open_settings_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = strings.settingsTooltip
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
                        Icon(Icons.Rounded.Add, contentDescription = strings.manualEntryBtn)
                    }
                }
            },
            bottomBar = {
                if (!useRail && !isDetailScreen) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val label = tab.labelSelector(strings)
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
                            val label = tab.labelSelector(strings)
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

    if (showSettingsDialog) {
        SettingsDialog(
            profile = profile,
            userEmail = userEmail,
            isDarkTheme = isDarkTheme,
            selectedLanguage = selectedLanguage,
            selectedUnitSystem = selectedUnitSystem,
            onToggleTheme = onToggleTheme,
            onSelectLanguage = onSelectLanguage,
            onSelectUnitSystem = onSelectUnitSystem,
            onOpenProfileTargets = { showProfileDialog = true },
            onSignOut = onSignOut,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showProfileDialog) {
        AthleteProfileDialog(
            profile = profile,
            onDismiss = { showProfileDialog = false },
            onSave = { payload ->
                showProfileDialog = false
                viewModel.updateProfileTargets(
                    displayName = payload.displayName,
                    photoUrl = payload.photoUrl,
                    birthDateIso = payload.birthDateIso,
                    gender = payload.gender,
                    heightCm = payload.heightCm,
                    weightKg = payload.weightKg,
                    weeklyGoalKm = payload.weeklyGoalKm,
                    maxHr = payload.maxHr,
                    restingHr = payload.restingHr,
                    ftpWatts = payload.ftpWatts,
                    preferredSport = payload.preferredSport
                )
            }
        )
    }
}
