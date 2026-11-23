package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "velostride_prefs")

class ThemePreferences(private val context: Context) {
    private val appContext = context.applicationContext
    private val darkModeKey = booleanPreferencesKey("dark_mode_enabled")

    val isDarkMode: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[darkModeKey] ?: true
    }

    suspend fun setDarkMode(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[darkModeKey] = enabled
        }
    }
}
