package com.liorapps.archerytrainer.screens.about

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.liorapps.archerytrainer.screens.editsession.EditShootingSessionViewModel
import com.liorapps.archerytrainer.screens.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AboutViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    init {
        loadAppInfo()
    }

    private fun loadAppInfo() {
        val context = getApplication<Application>()
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = packageInfo.longVersionCode.toString()

            _uiState.update { AboutUiState(
                versionName = versionName,
                versionCode = versionCode,
                githubUrl = "https://github.com/your-username/TimeShift"
            ) }
        } catch (e: PackageManager.NameNotFoundException) {
            // Fallback state if package info cannot be retrieved
            _uiState.update { AboutUiState(
                versionName = "Error",
                versionCode = "Error",
                githubUrl = "https://github.com/your-username/TimeShift"
            ) }
        }
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AboutViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AboutViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

data class AboutUiState(
    val versionName: String = "",
    val versionCode: String = "",
    val githubUrl: String = ""
)