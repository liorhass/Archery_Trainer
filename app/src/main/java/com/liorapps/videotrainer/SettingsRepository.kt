package com.liorapps.videotrainer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val DELAY_SEC_KEY = intPreferencesKey("delay_sec")
//        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    }

    // Expose settings as a Flows
    val delaySec: Flow<Int> = dataStore.data.map { prefs -> prefs[DELAY_SEC_KEY] ?: VideoTrainerDefaults.DEFAULT_DELAY_SEC }
//    val darkMode: Flow<Boolean> = dataStore.data.map { prefs -> prefs[DARK_MODE_KEY] ?: false }

    // Write settings
    suspend fun setDelaySec(delaySec: Int) {
        dataStore.edit { prefs -> prefs[DELAY_SEC_KEY] = delaySec }
    }
//    suspend fun setDarkMode(enabled: Boolean) {
//        dataStore.edit { prefs -> prefs[DARK_MODE_KEY] = enabled }
//    }
}