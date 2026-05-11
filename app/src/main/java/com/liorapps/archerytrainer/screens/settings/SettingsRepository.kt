package com.liorapps.archerytrainer.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.liorapps.archerytrainer.ArcheryTrainerDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    // Expose settings as a Flow
    val settingsFlow: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            delaySec    = prefs[DELAY_SEC_KEY] ?: ArcheryTrainerDefaults.DEFAULT_DELAY_SEC,
            videoResolution = prefs[VIDEO_RESOLUTION_KEY] ?.let {
                Json.Default.decodeFromString<ArcheryTrainerDefaults.VideoResolution>(it)
            } ?: ArcheryTrainerDefaults.VideoResolution.HD_1280x720(),
//            videoWidth  = prefs[VIDEO_WIDTH_KEY] ?: VideoTrainerDefaults.VIDEO_WIDTH,
//            videoHeight = prefs[VIDEO_HEIGH_KEY] ?: VideoTrainerDefaults.VIDEO_HEIGHT,
            frameRate   = prefs[FRAME_RATE_KEY] ?: ArcheryTrainerDefaults.FRAME_RATE,
            bitRate     = prefs[BIT_RATE_KEY] ?: ArcheryTrainerDefaults.BIT_RATE,

            shootingSessionButtonValues = prefs[SHOOTING_SESSION_BUTTON_VALUES_KEY] ?: "1,2,3,4,5,6,7,8,9,10,11,12",
            dummyFloat  = prefs[DUMMY_FLOAT_KEY] ?: 77.33f,

            shootingSetsHaveScores  = prefs[SHOOTING_SETS_HAVE_SCORES_KEY] ?: false,
        )
    }
//    val darkMode: Flow<Boolean> = dataStore.data.map { prefs -> prefs[DARK_MODE_KEY] ?: false }

    data class Settings (
        val delaySec: Int    = ArcheryTrainerDefaults.DEFAULT_DELAY_SEC,
        val videoResolution: ArcheryTrainerDefaults.VideoResolution = ArcheryTrainerDefaults.VideoResolution.HD_1280x720(),
//        val videoWidth: Int  = VideoTrainerDefaults.VIDEO_WIDTH,
//        val videoHeight: Int = VideoTrainerDefaults.VIDEO_HEIGHT,
        val frameRate: Int   = ArcheryTrainerDefaults.FRAME_RATE,
        val bitRate: Int     = ArcheryTrainerDefaults.BIT_RATE,

        val shootingSessionButtonValues: String = "",
        val dummyFloat: Float = 33.77f,

        /** When true, tapping an Add-Set button opens a dialog to enter a score.
         * When false, a Set is inserted immediately with score = -1 (no score) */
        val shootingSetsHaveScores: Boolean = false,
    )

    companion object {
        val DELAY_SEC_KEY = intPreferencesKey("delay_sec")
        val VIDEO_RESOLUTION_KEY = stringPreferencesKey("vid_res")
//        val VIDEO_WIDTH_KEY = intPreferencesKey("vid_width")
//        val VIDEO_HEIGH_KEY = intPreferencesKey("vid_height")
        val FRAME_RATE_KEY = intPreferencesKey("frame_rate")
        val BIT_RATE_KEY = intPreferencesKey("bit_rate")
        val SHOOTING_SESSION_BUTTON_VALUES_KEY = stringPreferencesKey("ssbvk")
        val DUMMY_FLOAT_KEY = floatPreferencesKey("dfk")
        val SHOOTING_SETS_HAVE_SCORES_KEY = booleanPreferencesKey("sshsk")
    }

    // Write settings
    suspend fun updateSettings(newSettings: Settings) {
        dataStore.edit { prefs ->
            prefs[DELAY_SEC_KEY]   = newSettings.delaySec
            prefs[VIDEO_RESOLUTION_KEY] = Json.Default.encodeToString(newSettings.videoResolution)
//            prefs[VIDEO_WIDTH_KEY] = newSettings.videoWidth
//            prefs[VIDEO_HEIGH_KEY] = newSettings.videoHeight
            prefs[FRAME_RATE_KEY]  = newSettings.frameRate
            prefs[BIT_RATE_KEY]    = newSettings.bitRate
            prefs[SHOOTING_SESSION_BUTTON_VALUES_KEY]    = newSettings.shootingSessionButtonValues
            prefs[DUMMY_FLOAT_KEY]    = newSettings.dummyFloat
            prefs[SHOOTING_SETS_HAVE_SCORES_KEY] = newSettings.shootingSetsHaveScores
        }
    }
    suspend fun setDelaySec(delaySec: Int) {
        dataStore.edit { prefs -> prefs[DELAY_SEC_KEY] = delaySec }
    }
//    suspend fun setVideoWidth(videoWidth: Int) {
//        dataStore.edit { prefs -> prefs[VIDEO_WIDTH_KEY] = videoWidth }
//    }
//    suspend fun setVideoHeight(videoHeight: Int) {
//        dataStore.edit { prefs -> prefs[VIDEO_HEIGH_KEY] = videoHeight }
//    }
    suspend fun setFrameRate(frameRate: Int) {
        dataStore.edit { prefs -> prefs[FRAME_RATE_KEY] = frameRate }
    }
    suspend fun setBitRate(bitRate: Int) {
        dataStore.edit { prefs -> prefs[BIT_RATE_KEY] = bitRate }
    }
    suspend fun setShootingSessionButtonValues(buttonValues: String) {
        dataStore.edit { prefs -> prefs[SHOOTING_SESSION_BUTTON_VALUES_KEY] = buttonValues }
    }
//    suspend fun setDarkMode(enabled: Boolean) {
//        dataStore.edit { prefs -> prefs[DARK_MODE_KEY] = enabled }
//    }
}