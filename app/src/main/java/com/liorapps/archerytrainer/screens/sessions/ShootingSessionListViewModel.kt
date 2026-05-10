package com.liorapps.archerytrainer.screens.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liorapps.archerytrainer.db.ATDatabase
import com.liorapps.archerytrainer.db.ShootingSessionWithStats
import com.liorapps.archerytrainer.navigation.ATNavKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

sealed class ShootingSessionListUiState {
    /** Initial state while the first DB emission hasn't arrived yet. */
    data object Loading : ShootingSessionListUiState()

    /** The database returned a non-empty list. */
    data class Success(val sessions: List<ShootingSessionWithStats>) : ShootingSessionListUiState()

    /** The database returned an empty list. */
    data object Empty : ShootingSessionListUiState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class ShootingSessionListViewModel(application: Application) : AndroidViewModel(application) {

    private val db         = ATDatabase.getInstance(application)
    private val sessionDao = db.shootingSessionDao()

    // Navigation events are sent through a buffered Channel so that events
    // fired before the screen starts collecting are not lost.
    private val _navigationEvent = Channel<ATNavKey>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    val uiState: StateFlow<ShootingSessionListUiState> = sessionDao.getAllShootingSessionsWithStats()
        .map { sessions ->
            if (sessions.isEmpty()) ShootingSessionListUiState.Empty
            else ShootingSessionListUiState.Success(sessions)
        }
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = ShootingSessionListUiState.Loading
        )

    // Called when the user taps the FAB.
    fun onNewShootingSessionClick() {
        viewModelScope.launch {
            _navigationEvent.send(ATNavKey.EditShootingSession(-1)) // -1 = create new shooting session
        }
    }

    // Called when the user taps an existing session card.
    fun onSessionClick(sessionId: Long) {
        viewModelScope.launch {
            _navigationEvent.send(ATNavKey.EditShootingSession(sessionId))
        }
    }

    // Called after the user confirms the delete dialog.
    fun onDeleteConfirmed(session: ShootingSessionWithStats) {
        viewModelScope.launch {
            sessionDao.deleteShootingSessionById(session.id)
        }
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ShootingSessionListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ShootingSessionListViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}