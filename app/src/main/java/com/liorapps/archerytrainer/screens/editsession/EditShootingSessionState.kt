package com.liorapps.archerytrainer.screens.editsession

import com.liorapps.archerytrainer.db.ArrowEntity
import com.liorapps.archerytrainer.db.ShootingSetWithSession

data class EditShootingSessionState(
    // Session data
    val sessionId: Long = EditShootingSessionViewModel.NEW_SESSION_ID, // -1 = not yet persisted to DB
    val sessionDateTimeUtc: Long = System.currentTimeMillis(),
    val comment: String = "",
    val sets: List<ShootingSetWithSession> = emptyList(),
    val currentSetId: Long? = null,
    val currentSetDateTimeUtc: Long = 0,
    val currentSetArrowsSortedByDateTime: List<ArrowEntity> = emptyList(),
    val currentSetArrowsSortedByScore: List<ArrowEntity> = emptyList(),

    val buttonValuesForNumOfArrowsInASet: List<Int> = EditShootingSessionViewModel.DEFAULT_BUTTON_VALUES, // Button grid configuration (12 values)
    val showEditCommentDialog: Boolean = false,
    val commentDraft: String = "",
    val secSinceLastAddSet: Int = -1,
    val showAddingSetTooSoonDialog: Boolean = false,
    val showScoreDialog: Boolean = false,
    val scoreDraft: String = "",
    val pendingArrowCount: Int? = null,   // the arrow-count waiting for a score entry
    val showEditButtonValueDialog: Boolean = false,
    val editingButtonIndex: Int = -1,
    val buttonValueDraft: String = "",

    val shootingSetsHaveArrows: Boolean = false,
    val shootingSetsHaveScore: Boolean = false,

    val activeTab: ActiveTab = ActiveTab.EDIT_SESSION,
) {
    // ── Derived stats ─────────────────────────────────────────────────────────
    val scoredSets: List<ShootingSetWithSession>
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

enum class ActiveTab {EDIT_SESSION, SETS_LIST,}