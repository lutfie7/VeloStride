package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.AppLanguage
import com.example.core.Formatters
import com.example.core.LocalAppLanguage
import com.example.core.LocalAppStrings
import com.example.core.LocalUnitSystem
import com.example.core.ThemePreferences
import com.example.core.UnitSystem
import com.example.core.appStringsFor
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.signOutUser
import com.example.data.repository.AthleteIntelligenceRepository
import com.example.data.repository.ElevationRepository
import com.example.data.repository.LiveVoiceCoachManager
import com.example.data.repository.VeloStrideRepository
import com.example.hardware.HealthConnectManager
import com.example.hardware.HuaweiBleManager
import com.example.hardware.WorkoutRecorderController
import com.example.ui.MainScaffold
import com.example.ui.MainViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.VeloStrideTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run synchronously before Firebase.ai / Firestore / Auth calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        val themePrefs = ThemePreferences(applicationContext)
        val isFirebaseReady = FirebaseApp.getApps(this).isNotEmpty()

        setContent {
            val isDarkTheme by themePrefs.isDarkMode.collectAsStateWithLifecycle(initialValue = true)
            val appLanguage by themePrefs.appLanguage.collectAsStateWithLifecycle(initialValue = AppLanguage.ENGLISH_US)
            val unitSystem by themePrefs.unitSystem.collectAsStateWithLifecycle(initialValue = UnitSystem.METRIC)
            val coroutineScope = rememberCoroutineScope()
            val credentialManager = remember(this) { CredentialManager.create(this) }

            Formatters.appLanguage = appLanguage
            Formatters.unitSystem = unitSystem
            SideEffect {
                Locale.setDefault(appLanguage.locale)
            }

            val strings = remember(appLanguage) { appStringsFor(appLanguage) }

            CompositionLocalProvider(
                LocalAppLanguage provides appLanguage,
                LocalUnitSystem provides unitSystem,
                LocalAppStrings provides strings
            ) {
                VeloStrideTheme(darkTheme = isDarkTheme) {
                    if (!isFirebaseReady) {
                        AuthScreen(
                            onAuthSuccess = {},
                            initialErrorMessage = "Konfigurasi Firebase (google-services.json) belum ada di APK ini. Tambahkan GitHub Secret GOOGLE_SERVICES_JSON di repositori GitHub Anda lalu jalankan ulang workflow Build VeloStride APK."
                        )
                    } else {
                        val currentUser by remember { Firebase.auth.authStateFlow() }
                            .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
                        val user = currentUser
                        if (user == null) {
                            AuthScreen(onAuthSuccess = {})
                        } else {
                            key(user.uid) {
                                val app = applicationContext
                                val vm: MainViewModel = viewModel(
                                    key = user.uid,
                                    factory = viewModelFactory {
                                        initializer {
                                            val bleManager = HuaweiBleManager(app)
                                            val healthConnect = HealthConnectManager(app)
                                            val recorder = WorkoutRecorderController(app, bleManager)
                                            val liveVoice = LiveVoiceCoachManager(app)
                                            MainViewModel(
                                                appContext = app,
                                                themePreferences = themePrefs,
                                                repository = VeloStrideRepository(app),
                                                aiRepository = AthleteIntelligenceRepository(),
                                                bleManager = bleManager,
                                                healthConnectManager = healthConnect,
                                                recorderController = recorder,
                                                currentUserId = user.uid,
                                                initialDisplayName = user.displayName ?: "VeloStride Athlete",
                                                initialPhotoUrl = user.photoUrl?.toString().orEmpty(),
                                                elevationRepository = ElevationRepository(),
                                                liveVoiceCoach = liveVoice
                                            )
                                        }
                                    }
                                )

                                MainScaffold(
                                    viewModel = vm,
                                    userEmail = user.email.orEmpty(),
                                    isDarkTheme = isDarkTheme,
                                    selectedLanguage = appLanguage,
                                    selectedUnitSystem = unitSystem,
                                    onToggleTheme = {
                                        coroutineScope.launch {
                                            themePrefs.setDarkMode(!isDarkTheme)
                                        }
                                    },
                                    onSelectLanguage = { lang ->
                                        coroutineScope.launch {
                                            themePrefs.setAppLanguage(lang)
                                        }
                                    },
                                    onSelectUnitSystem = { system ->
                                        coroutineScope.launch {
                                            themePrefs.setUnitSystem(system)
                                        }
                                    },
                                    onSignOut = {
                                        signOutUser(credentialManager, coroutineScope)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
