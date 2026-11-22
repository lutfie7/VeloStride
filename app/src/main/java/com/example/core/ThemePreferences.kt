package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "velostride_prefs")

class ThemePreferences(private val context: Context) {
    private val appContext = context.applicationContext
    private val darkModeKey = booleanPreferencesKey("dark_mode_enabled")
    private val autoSyncBandKey = booleanPreferencesKey("auto_sync_band_workouts")
    private val lastBandSyncMsKey = longPreferencesKey("last_band_auto_sync_ms")

    val isDarkMode: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[darkModeKey] ?: true
    }

    val isAutoSyncBandEnabled: Flow<Boolean> = appContext.dataStore.data.map { prefs ->
        prefs[autoSyncBandKey] ?: true
    }

    val lastBandAutoSyncMs: Flow<Long> = appContext.dataStore.data.map { prefs ->
        prefs[lastBandSyncMsKey] ?: 0L
    }

    suspend fun setDarkMode(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[darkModeKey] = enabled
        }
    }

    suspend fun setAutoSyncBandEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[autoSyncBandKey] = enabled
        }
    }

    suspend fun setLastBandAutoSyncMs(timestampMs: Long) {
        appContext.dataStore.edit { prefs ->
            prefs[lastBandSyncMsKey] = timestampMs
        }
    }
}
