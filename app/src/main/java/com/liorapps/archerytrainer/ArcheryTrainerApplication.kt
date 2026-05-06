package com.liorapps.archerytrainer

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import timber.log.Timber
import kotlin.getValue

class ArcheryTrainerApplication: Application() {

    // DataStore singleton
    val dataStore by preferencesDataStore(name = "settings")

    // Repository singleton
    val settingsRepository by lazy {
        SettingsRepository(dataStore)
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
//            Timber.plant(Timber.DebugTree())
            Timber.plant(LineNumberDebugTree())
        }
    }

    // Link to source file in the log. From: https://stackoverflow.com/a/49216400/1071117
    class LineNumberDebugTree : Timber.DebugTree() {
        override fun createStackElementTag(element: StackTraceElement): String? {
//return "(${element.fileName}:${element.lineNumber})#${element.methodName}"
            return "(${element.fileName}:${element.lineNumber})"
        }
    }
}
