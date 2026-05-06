package com.liorapps.archerytrainer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    data class Settings (
        val delaySec: Int    = ArcheryTrainerDefaults.DEFAULT_DELAY_SEC,
        val videoResolution: ArcheryTrainerDefaults.VideoResolution = ArcheryTrainerDefaults.VideoResolution.HD_1280x720(),
//        val videoWidth: Int  = VideoTrainerDefaults.VIDEO_WIDTH,
//        val videoHeight: Int = VideoTrainerDefaults.VIDEO_HEIGHT,
        val frameRate: Int   = ArcheryTrainerDefaults.FRAME_RATE,
        val bitRate: Int     = ArcheryTrainerDefaults.BIT_RATE,
        val dummyString: String = "Default dummy string",
        val dummyFloat: Float = 33.77f,
    )

    companion object {
        val DELAY_SEC_KEY = intPreferencesKey("delay_sec")
        val VIDEO_RESOLUTION_KEY = stringPreferencesKey("vid_res")
//        val VIDEO_WIDTH_KEY = intPreferencesKey("vid_width")
//        val VIDEO_HEIGH_KEY = intPreferencesKey("vid_height")
        val FRAME_RATE_KEY = intPreferencesKey("frame_rate")
        val BIT_RATE_KEY = intPreferencesKey("bit_rate")
        val DUMMY_STRING_KEY = stringPreferencesKey("dsk")
        val DUMMY_FLOAT_KEY = floatPreferencesKey("dfk")
    }

    // Expose settings as a Flow
    val settingsFlow: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            delaySec    = prefs[DELAY_SEC_KEY] ?: ArcheryTrainerDefaults.DEFAULT_DELAY_SEC,
            videoResolution = prefs[VIDEO_RESOLUTION_KEY] ?.let {
                Json.decodeFromString<ArcheryTrainerDefaults.VideoResolution>(it)
            } ?: ArcheryTrainerDefaults.VideoResolution.HD_1280x720(),
//            videoWidth  = prefs[VIDEO_WIDTH_KEY] ?: VideoTrainerDefaults.VIDEO_WIDTH,
//            videoHeight = prefs[VIDEO_HEIGH_KEY] ?: VideoTrainerDefaults.VIDEO_HEIGHT,
            frameRate   = prefs[FRAME_RATE_KEY] ?: ArcheryTrainerDefaults.FRAME_RATE,
            bitRate     = prefs[BIT_RATE_KEY] ?: ArcheryTrainerDefaults.BIT_RATE,
            dummyString = prefs[DUMMY_STRING_KEY] ?: "Default dummy string",
            dummyFloat  = prefs[DUMMY_FLOAT_KEY] ?: 77.33f,
        )
    }
//    val darkMode: Flow<Boolean> = dataStore.data.map { prefs -> prefs[DARK_MODE_KEY] ?: false }

    // Write settings
    suspend fun updateSettings(newSettings: Settings) {
        dataStore.edit { prefs ->
            prefs[DELAY_SEC_KEY]   = newSettings.delaySec
            prefs[VIDEO_RESOLUTION_KEY] = Json.encodeToString(newSettings.videoResolution)
//            prefs[VIDEO_WIDTH_KEY] = newSettings.videoWidth
//            prefs[VIDEO_HEIGH_KEY] = newSettings.videoHeight
            prefs[FRAME_RATE_KEY]  = newSettings.frameRate
            prefs[BIT_RATE_KEY]    = newSettings.bitRate
            prefs[DUMMY_STRING_KEY]    = newSettings.dummyString
            prefs[DUMMY_FLOAT_KEY]    = newSettings.dummyFloat
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
//    suspend fun setDarkMode(enabled: Boolean) {
//        dataStore.edit { prefs -> prefs[DARK_MODE_KEY] = enabled }
//    }
}