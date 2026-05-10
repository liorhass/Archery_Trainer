package com.liorapps.archerytrainer.screens.editsession

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liorapps.archerytrainer.db.ATDatabase
import com.liorapps.archerytrainer.db.ShootingSessionEntity
import com.liorapps.archerytrainer.db.ShootingSetEntity
import com.liorapps.archerytrainer.db.ShootingSetWithSession
import com.liorapps.archerytrainer.screens.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class EditShootingSessionUiState(
    // Session data
    val sessionId: Long = EditShootingSessionViewModel.NEW_SESSION_ID, // -1 = not yet persisted to DB
    val sessionDateTimeUtc: Long = System.currentTimeMillis(),
    val comment: String = "",
    val sets: List<ShootingSetWithSession> = emptyList(),

    val buttonValues: List<Int> = EditShootingSessionViewModel.DEFAULT_BUTTON_VALUES, // Button grid configuration (12 values)
    // Edit-comment dialog
    val showEditCommentDialog: Boolean = false,
    val commentDraft: String = "",
    // Enter-score dialog
    val showScoreDialog: Boolean = false,
    val scoreDraft: String = "",
    val pendingArrowCount: Int? = null,   // the arrow-count waiting for a score entry
    // Edit-button-value dialog
    val showEditButtonDialog: Boolean = false,
    val editingButtonIndex: Int = -1,
    val buttonValueDraft: String = "",
) {
    // ── Derived stats ─────────────────────────────────────────────────────────
    private val scoredSets: List<ShootingSetWithSession>
        get() = sets.filter { it.score != -1 }

    val totalArrows: Int
        get() = sets.sumOf { it.numberOfShots }

    val totalScoredArrows: Int
        get() = scoredSets.sumOf { it.numberOfShots }

    val totalScore: Int
        get() = scoredSets.sumOf { it.score }

    val hasAnyScore: Boolean
        get() = scoredSets.isNotEmpty()

    val averageScore: Float
        get() = if (scoredSets.isNotEmpty()) totalScore.toFloat() / totalScoredArrows.toFloat() else 0f

    // ── Dialog-input validation ───────────────────────────────────────────────

    val isScoreInputValid: Boolean
        get() = scoreDraft.toIntOrNull()?.let { it in 0..999 } == true

    val isButtonValueInputValid: Boolean
        get() = buttonValueDraft.toIntOrNull()?.let { it in 1..999 } == true
}

class EditShootingSessionViewModel (
    val sessionId: Long,
    application: Application,
    val settingsRepo: SettingsRepository,
) : AndroidViewModel(application) {

    companion object {
        /**
         * When true, tapping an Add-Set button opens a dialog to enter a score.
         * When false, a Set is inserted immediately with score = -1 (no score).
         */
        const val SETS_HAVE_SCORE = false

        /** Default arrow counts shown on the 12 Add-Set buttons. */
        val DEFAULT_BUTTON_VALUES = listOf(3, 6, 9, 12, 15, 18, 20, 24, 30, 36, 48, 60)

        private val BUTTON_VALUES_KEY = stringPreferencesKey("arrow_button_values")

        const val NAV_ARG_SESSION_ID = "sessionId"
        const val NEW_SESSION_ID = -1L
    }

    private val db = ATDatabase.getInstance(application)
    private val shootingSessionDao = db.shootingSessionDao()
    private val shootingSetDao = db.shootingSetDao()
    val settingsFlow: StateFlow<SettingsRepository.Settings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, SettingsRepository.Settings())

    private val _uiState = MutableStateFlow(
        EditShootingSessionUiState( sessionId = sessionId, )
    )
    val uiState: StateFlow<EditShootingSessionUiState> = _uiState.asStateFlow()

    init {
        loadButtonValues()
        if (sessionId != NEW_SESSION_ID) {
            loadExistingSession()
            observeSets(sessionId)
        }
    }

    private fun loadButtonValues() {
        viewModelScope.launch {
            settingsFlow.map { settings ->
                settings.shootingSessionButtonValues
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .takeIf { it.size == 12 }
                    ?: DEFAULT_BUTTON_VALUES
            }
            .collect { values ->
                _uiState.update { it.copy(buttonValues = values) }
            }
        }
    }

    private fun loadExistingSession() {
        viewModelScope.launch {
            val session = shootingSessionDao
                .getShootingSessionWithStats(sessionId)
                ?: return@launch
            _uiState.update {
                it.copy(
                    sessionId = session.id,
                    sessionDateTimeUtc = session.dateTimeUtc,
                    comment = session.comment,
                )
            }
        }
    }

    private fun observeSets(sessionId: Long) {
        viewModelScope.launch {
            shootingSetDao
                .getShootingSetsForShootingSession(sessionId)
                .collect { sets -> _uiState.update { it.copy(sets = sets) } }
        }
    }

    /**
     * Lazy session creation - Ensures a ShootingSession row exists in the database. On first
     * call for a new session, inserts the row (carrying any in-memory comment) and starts
     * observing the session's sets. Subsequent calls return the cached ID immediately.
     */
    private suspend fun ensureSessionCreated(): Long {
//        Timber.d("ensureSessionCreated(): _uiState.value.sessionId=${_uiState.value.sessionId}")
//        _uiState.value.sessionId?.let { return it }
        _uiState.value.sessionId.let { if (it != -1L) return it }

        val now = System.currentTimeMillis()
        val newId = shootingSessionDao.insertShootingSession(
            ShootingSessionEntity(
                dateTimeUtc = now,
                comment = _uiState.value.comment,
            )
        )
//        Timber.d("ensureSessionCreated(): Inserted new session ID=$newId")
        _uiState.update { it.copy(sessionId = newId, sessionDateTimeUtc = now) }
        observeSets(newId)
        return newId
    }

    /** Add-Set button */
    fun onSetButtonTapped(arrowCount: Int) {
        if (SETS_HAVE_SCORE) {
            // Open score dialog; the set will be inserted after the user confirms.
            _uiState.update {
                it.copy(
                    showScoreDialog = true,
                    scoreDraft = "",
                    pendingArrowCount = arrowCount,
                )
            }
        } else {
            viewModelScope.launch { addSet(arrowCount, score = -1) }
        }
    }

    private suspend fun addSet(arrowCount: Int, score: Int) {
//        Timber.d("addSet() arrowCount=$arrowCount, score=$score")
        // session is not created until the first set is created
        val sessionId = ensureSessionCreated()
//        Timber.d("addSet() sessionId=$sessionId")
        shootingSetDao.insertShootingSet(
            ShootingSetEntity(
                shootingSessionId = sessionId,
                dateTimeUtc = System.currentTimeMillis(),
                numberOfShots = arrowCount,
                score = score,
            )
        )
    }

    // Edit-Comment dialog
    fun onEditCommentClicked() {
        _uiState.update {
            it.copy(
                showEditCommentDialog = true,
                commentDraft = it.comment,
            )
        }
    }

    /** Called on every keystroke; enforces the 1000-character hard cap. */
    fun onCommentDraftChanged(value: String) {
        if (value.length <= 1000) {
            _uiState.update { it.copy(commentDraft = value) }
        }
    }

    fun onCommentConfirmed() {
//        val trimmed = _uiState.value.commentDraft.trim().takeIf { it.isNotBlank() }
        val trimmed = _uiState.value.commentDraft.trim()
        _uiState.update { it.copy(comment = trimmed, showEditCommentDialog = false) }

        // Persist only if the session row already exists.
        // For a brand-new session the comment is held in-memory and will be written to
        // the DB inside the first ensureSessionCreated() call when the first set is created.
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            val existing = shootingSessionDao
                .getShootingSessionWithStats(sessionId) ?: return@launch
            shootingSessionDao.updateShootingSession(
                ShootingSessionEntity(
                    id = sessionId,
                    dateTimeUtc = existing.dateTimeUtc,
                    comment = trimmed,
                )
            )
        }
    }

    fun onCommentDismissed() {
        _uiState.update { it.copy(showEditCommentDialog = false) }
    }

    // Enter-Score dialog
    fun onScoreDraftChanged(value: String) {
        _uiState.update { it.copy(scoreDraft = value) }
    }

    fun onScoreConfirmed() {
        val score = _uiState.value.scoreDraft
            .toIntOrNull()?.takeIf { it in 0..999 } ?: return
        val arrowCount = _uiState.value.pendingArrowCount ?: return
        _uiState.update {
            it.copy(showScoreDialog = false, pendingArrowCount = null, scoreDraft = "")
        }
        viewModelScope.launch { addSet(arrowCount, score) }
    }

    fun onScoreDismissed() {
        _uiState.update {
            it.copy(showScoreDialog = false, pendingArrowCount = null, scoreDraft = "")
        }
    }

    // Edit-Button-Value dialog
    fun onSetButtonLongPressed(index: Int) {
        _uiState.update {
            it.copy(
                showEditButtonDialog = true,
                editingButtonIndex = index,
                buttonValueDraft = it.buttonValues[index].toString(),
            )
        }
    }

    fun onButtonValueDraftChanged(value: String) {
        _uiState.update { it.copy(buttonValueDraft = value) }
    }

    fun onButtonValueConfirmed() {
        val newValue = _uiState.value.buttonValueDraft
            .toIntOrNull()?.takeIf { it in 1..999 } ?: return
        val index = _uiState.value.editingButtonIndex.takeIf { it >= 0 } ?: return

        val updated = _uiState.value.buttonValues
            .toMutableList()
            .also { it[index] = newValue }

        _uiState.update {
            it.copy(
                buttonValues = updated,
                showEditButtonDialog = false,
                editingButtonIndex = -1,
                buttonValueDraft = "",
            )
        }

        viewModelScope.launch {
            settingsRepo.setShootingSessionButtonValues(updated.joinToString(","))
        }
    }

    fun onButtonValueDismissed() {
        _uiState.update {
            it.copy(
                showEditButtonDialog = false,
                editingButtonIndex = -1,
                buttonValueDraft = "",
            )
        }
    }


    class Factory(
        private val sessionId: Long,
        private val application: Application,
        private val settingsRepo: SettingsRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EditShootingSessionViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return EditShootingSessionViewModel(sessionId, application, settingsRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
