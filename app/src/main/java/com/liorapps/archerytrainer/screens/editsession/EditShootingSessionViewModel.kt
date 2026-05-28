package com.liorapps.archerytrainer.screens.editsession

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liorapps.archerytrainer.ScrLockManagerImpl
import com.liorapps.archerytrainer.db.ATDatabase
import com.liorapps.archerytrainer.db.ArrowEntity
import com.liorapps.archerytrainer.db.ShootingSessionEntity
import com.liorapps.archerytrainer.db.ShootingSetEntity
import com.liorapps.archerytrainer.db.ShootingSetWithSession
import com.liorapps.archerytrainer.screens.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class EditShootingSessionViewModel (
    val sessionId: Long,
    application: Application,
    val settingsRepo: SettingsRepository,
) : AndroidViewModel(application) {

    companion object {
        /** Default arrow counts shown on the 12 Add-Set buttons. */
        val DEFAULT_BUTTON_VALUES = listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 18)

        const val NEW_SESSION_ID = -1L
    }

    private val db = ATDatabase.getInstance(application)
    private val shootingSessionDao = db.shootingSessionDao()
    private val shootingSetDao = db.shootingSetDao()
    private val arrowDao = db.arrowDao()
    val settingsFlow: StateFlow<SettingsRepository.Settings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, SettingsRepository.Settings())
    private val _uiStateFlow = MutableStateFlow(
        EditShootingSessionState( sessionId = sessionId )
    )
    val uiStateFlow: StateFlow<EditShootingSessionState> =
        combine(_uiStateFlow, settingsFlow) { uiState, settings ->
            uiState.copy(
                shootingSetsHaveArrows = settings.shootingSetsHaveArrows,
                shootingSetsHaveScore = settings.shootingSetsHaveScores
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EditShootingSessionState(sessionId = sessionId)
        )

    // One-shot delta events (fired whenever the user adds a new set)
    // A Channel (not StateFlow) is used here intentionally:
    //   • Each send() produces exactly one event even under rapid increments.
    //   • Late collectors don't replay old events.
    //   • BUFFERED capacity means fast senders never block the ViewModel.
    private val _moreShotsEventChannel = Channel<Int>(Channel.BUFFERED)
    val moreShotsEvents = _moreShotsEventChannel.receiveAsFlow()

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
                _uiStateFlow.update { it.copy(buttonValuesForNumOfArrowsInASet = values) }
            }
        }
    }

    private fun loadExistingSession() {
        viewModelScope.launch {
            val session = shootingSessionDao
                .getShootingSessionWithStats(sessionId)
                ?: return@launch
            _uiStateFlow.update {
                it.copy(
                    sessionId = session.id,
                    sessionDateTimeUtc = session.dateTimeUtc,
                    comment = session.comment,
                )
            }
        }
    }

    private var setsObserverJob: Job? = null
    private fun observeSets(sessionId: Long) {
        setsObserverJob?.cancel() // Cancel previous observer
        setsObserverJob = viewModelScope.launch {
            shootingSetDao
                .getShootingSetsForShootingSession(sessionId)
                .collect { sets -> _uiStateFlow.update { it.copy(sets = sets) } }
        }
    }

    // Simple wrapper to pass data cleanly through the flow chain
    data class ArrowsListsWrapper(
        val listSortedByDateTime: List<ArrowEntity>,
        val listSortedByScore:    List<ArrowEntity>
    )
    private var arrowsObserverJob: Job? = null
    // There are two flows emitting lists of arrows by the DB (two different sort criteria). Since
    // they always change together, we collect them in a single coroutine and use combine() to
    // change the uiState only once
    private fun observeArrows(setId: Long) {
        arrowsObserverJob?.cancel() // Cancel previous observer
        arrowsObserverJob = viewModelScope.launch {
            combine(
                arrowDao.getArrowsForShootingSetSortedByDateTime(setId),
                arrowDao.getArrowsForShootingSetSortedByScore(setId)
            ) { listSortedByDateTime, listSortedByScore ->
                ArrowsListsWrapper(listSortedByDateTime, listSortedByScore)
            }.collect { combinedFlows ->
                _uiStateFlow.update { it.copy(
                    currentSetArrowsSortedByDateTime = combinedFlows.listSortedByDateTime,
                    currentSetArrowsSortedByScore = combinedFlows.listSortedByScore
                ) }
            }
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
        _uiStateFlow.value.sessionId.let { if (it != -1L) return it }

        val now = System.currentTimeMillis()
        val newId = shootingSessionDao.insertShootingSession(
            ShootingSessionEntity(
                dateTimeUtc = now,
                comment = _uiStateFlow.value.comment,
            )
        )
//        Timber.d("ensureSessionCreated(): Inserted new session ID=$newId")
        _uiStateFlow.update { it.copy(sessionId = newId, sessionDateTimeUtc = now) }
        observeSets(newId)
        return newId
    }

    var timeOfPreviousAddSet = 0L  // To prevent adding sets in quick succession
    /** Add-Set-with-arrows-number button */
    fun onAddSetWithNumOfShotsButtonTapped(arrowCount: Int) {
        val now = System.currentTimeMillis()
        when {
            settingsFlow.value.shootingSetsHaveScores -> {
                // Open score dialog; the set will be inserted after the user confirms. See onScoreConfirmed()
                _uiStateFlow.update {
                    it.copy(
                        showScoreDialog = true,
                        scoreDraft = "",
                        pendingArrowCount = arrowCount,
                    )
                }
            }
            ((settingsFlow.value.timeBetweenSetsForTooSoonWarn > 0) &&
                    ((now - timeOfPreviousAddSet)/1000 < settingsFlow.value.timeBetweenSetsForTooSoonWarn)) -> {
                // Open an "Are you sure?" dialog; the set will be inserted after the user confirms.
                // See onAddingSetTooSoonConfirmed()
                val secSinceLastAddSet = ((now - timeOfPreviousAddSet) / 1000).toInt()
                _uiStateFlow.update {
                    it.copy(
                        showAddingSetTooSoonDialog = true,
                        secSinceLastAddSet = secSinceLastAddSet,
                        pendingArrowCount = arrowCount
                    )
                }
            }
            else -> {
                viewModelScope.launch { addSet(arrowCount, score = -1) }
            }
        }
    }

    /** Add-Set button (adding an empty set) */
    fun onAddSetButtonTapped() {
        val now = System.currentTimeMillis()
        when {
            ((settingsFlow.value.timeBetweenSetsForTooSoonWarn > 0) &&
                    ((now - timeOfPreviousAddSet)/1000 < settingsFlow.value.timeBetweenSetsForTooSoonWarn)) -> {
                // Open an "Are you sure?" dialog; the set will be inserted after the user confirms.
                // See onAddingSetTooSoonConfirmed()
                val secSinceLastAddSet = ((now - timeOfPreviousAddSet) / 1000).toInt()
                _uiStateFlow.update {
                    it.copy(
                        showAddingSetTooSoonDialog = true,
                        secSinceLastAddSet = secSinceLastAddSet,
                        pendingArrowCount = 0
                    )
                }
            }
            else -> {
                viewModelScope.launch { addSet(arrowCount = 0, score = -1) }
            }
        }
    }

    /** returns the new set's ID */
    private suspend fun addSet(arrowCount: Int, score: Int): Long {
//        Timber.d("addSet() arrowCount=$arrowCount, score=$score")
        // session is not created until the first set is created
        val sessionId = ensureSessionCreated()
        val now = System.currentTimeMillis()
        val setId = shootingSetDao.insertShootingSet(
            ShootingSetEntity(
                shootingSessionId = sessionId,
                dateTimeUtc = now,
                numberOfShots = arrowCount,
                score = score,
            )
        )
        _uiStateFlow.update { it.copy( currentSetId = setId, currentSetDateTimeUtc = now ) }
        if (arrowCount > 0) {
            _moreShotsEventChannel.send(arrowCount) // fires an event for the floater "+N"
        }
        observeArrows(setId)
        timeOfPreviousAddSet = System.currentTimeMillis()
        return setId
    }

    // Enter-Score dialog
    fun onScoreDraftChanged(value: String) {
        _uiStateFlow.update { it.copy(scoreDraft = value) }
    }
    fun onScoreConfirmed() {
        val score = _uiStateFlow.value.scoreDraft
            .toIntOrNull()?.takeIf { it in 0..999 } ?: return
        val arrowCount = _uiStateFlow.value.pendingArrowCount ?: return
        _uiStateFlow.update {
            it.copy(showScoreDialog = false, pendingArrowCount = null, scoreDraft = "")  // Close the dialog
        }
        viewModelScope.launch { addSet(arrowCount, score) }
    }
    fun onScoreDismissed() {
        _uiStateFlow.update {
            it.copy(showScoreDialog = false, pendingArrowCount = null, scoreDraft = "")
        }
    }

    fun onAddingSetTooSoonConfirmed() {
        val arrowCount = _uiStateFlow.value.pendingArrowCount ?: return
        _uiStateFlow.update {
            it.copy(showAddingSetTooSoonDialog = false, pendingArrowCount = null)  // Close the dialog
        }
        viewModelScope.launch { addSet(arrowCount, -1) }
    }
    fun onAddingSetTooSoonCanceled() {
        _uiStateFlow.update {
            it.copy(showAddingSetTooSoonDialog = false, pendingArrowCount = null)
        }
    }

    // Edit-Comment dialog
    fun onEditCommentClicked() {
        _uiStateFlow.update {
            it.copy(
                showEditCommentDialog = true,
                commentDraft = it.comment,
            )
        }
    }

    fun onLockScreenClicked() {
        ScrLockManagerImpl.lockScreen(getApplication())
    }

    /** Called on every keystroke; enforces the 1000-character hard cap. */
    fun onCommentDraftChanged(value: String) {
        if (value.length <= 1000) {
            _uiStateFlow.update { it.copy(commentDraft = value) }
        }
    }

    fun onCommentConfirmed() {
//        val trimmed = _uiState.value.commentDraft.trim().takeIf { it.isNotBlank() }
        val trimmed = _uiStateFlow.value.commentDraft.trim()
        _uiStateFlow.update { it.copy(comment = trimmed, showEditCommentDialog = false) }

        // Persist only if the session row already exists.
        // For a brand-new session the comment is held in-memory and will be written to
        // the DB inside the first ensureSessionCreated() call when the first set is created.
        val sessionId = _uiStateFlow.value.sessionId
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
        _uiStateFlow.update { it.copy(showEditCommentDialog = false) }
    }

    // Edit-Button-Value dialog
    fun onSetButtonLongPressed(index: Int) {
        _uiStateFlow.update {
            it.copy(
                showEditButtonValueDialog = true,
                editingButtonIndex = index,
                buttonValueDraft = it.buttonValuesForNumOfArrowsInASet[index].toString(),
            )
        }
    }

    fun onButtonValueDraftChanged(value: String) {
        _uiStateFlow.update { it.copy(buttonValueDraft = value) }
    }

    fun onButtonValueConfirmed() {
        val newValue = _uiStateFlow.value.buttonValueDraft
            .toIntOrNull()?.takeIf { it in 1..999 } ?: return
        val index = _uiStateFlow.value.editingButtonIndex.takeIf { it >= 0 } ?: return

        val updated = _uiStateFlow.value.buttonValuesForNumOfArrowsInASet
            .toMutableList()
            .also { it[index] = newValue }

        _uiStateFlow.update {
            it.copy(
                buttonValuesForNumOfArrowsInASet = updated,
                showEditButtonValueDialog = false,
                editingButtonIndex = -1,
                buttonValueDraft = "",
            )
        }

        viewModelScope.launch {
            settingsRepo.setShootingSessionButtonValues(updated.joinToString(","))
        }
    }

    fun onButtonValueDismissed() {
        _uiStateFlow.update {
            it.copy(
                showEditButtonValueDialog = false,
                editingButtonIndex = -1,
                buttonValueDraft = "",
            )
        }
    }

    fun onSetActiveTab(newActiveTab: ActiveTab) {
        _uiStateFlow.update {
            it.copy(
                activeTab = newActiveTab,
            )
        }
    }

    // Called when the user taps an existing set card
    fun onSetClick(setId: Long) {
        Timber.d("onSetClicked(): SetID=$setId")
//        viewModelScope.launch {
//            _navigationEvent.send(ATNavKey.EditShootingSession(sessionId))
//        }
    }

    // Called after the user confirms the delete-set dialog
    fun onDeleteSetConfirmed(set: ShootingSetWithSession) {
        viewModelScope.launch {
            shootingSetDao.deleteShootingSetById(set.id)
        }
    }

    fun onAddArrowToCurrentSet(score: Int) {
        Timber.d("#### onAddArrowToCurrentSet() score=$score")
        val currentSetId = _uiStateFlow.value.currentSetId
        if (currentSetId == null) {
            Timber.e("#### onAddArrowToCurrentSet() currentSetId=null")
            return
        }
        viewModelScope.launch {
            arrowDao.insert( ArrowEntity(
                shootingSetId = currentSetId,
                dateTimeUtc = System.currentTimeMillis(),
                score = score,
            ))
        }
    }

    fun onArrowClicked(arrow: ArrowEntity) {
        Timber.d("#### onArrowClicked() arrowId=${arrow.id}")
    }
    fun onArrowSwiped(arrow: ArrowEntity) {
        Timber.d("#### onArrowSwiped() arrowId=${arrow.id}")
        viewModelScope.launch {
            arrowDao.delete(arrow)
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
