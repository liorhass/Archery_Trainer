package com.liorapps.archerytrainer.db

/**
 * Returned when querying a single ShootingSet:
 * carries the set's own data plus its parent session's date-time and comment.
 */
data class ShootingSetWithSession(
    // ── ShootingSet fields ──────────────────────────────────────────────────────────
    val id: Long,
    val shootingSessionId: Long,
    val dateTimeUtc: Long,
    val numberOfShots: Int,
    val score: Int,

    // ── Parent-session fields ──────────────────────────────────────────────────
    val shootingSessionDateTimeUtc: Long,
    val shootingSessionComment: String
)

/**
 * Returned when querying a single ShootingSession:
 * carries the session's own data plus the aggregate number-of-shots across all
 * related shootingSets (null when no shootingSets exist yet).
 */
data class ShootingSessionWithStats(
    val id: Long,
    val dateTimeUtc: Long,
    val comment: String,
    val totalShots: Int?                       // SUM(numberOfShots); null if no sets
)
