package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "velostride_prefs")

class ThemePreferences(private val context: Context) {
    private val appContext = context.applicationContext
    private val darkModeKey = booleanPreferencesKey("dark_mode_enabled")
    private val languageKey = stringPreferencesKey("app_language_tag")
    private val unitSystemKey = stringPreferencesKey("unit_system_code")

    val isDarkMode: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[darkModeKey] ?: true
    }

    val appLanguage: Flow<AppLanguage> = appContext.dataStore.data.map { prefs ->
        AppLanguage.fromTag(prefs[languageKey])
    }

    val unitSystem: Flow<UnitSystem> = appContext.dataStore.data.map { prefs ->
        UnitSystem.fromCode(prefs[unitSystemKey])
    }

    suspend fun setDarkMode(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[darkModeKey] = enabled
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        appContext.dataStore.edit { prefs ->
            prefs[languageKey] = language.tag
        }
    }

    suspend fun setUnitSystem(system: UnitSystem) {
        appContext.dataStore.edit { prefs ->
            prefs[unitSystemKey] = system.code
        }
    }
}
