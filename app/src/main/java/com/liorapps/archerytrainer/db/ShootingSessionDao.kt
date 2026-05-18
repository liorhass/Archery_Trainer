package com.liorapps.archerytrainer.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShootingSessionDao {

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Inserts a new shooting-session and returns its auto-generated ID.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShootingSession(shootingSession: ShootingSessionEntity): Long

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns all shooting-sessions ordered by date-time descending (newest first),
     * each enriched with the total number of shots across its shooting-sets.
     * Emits a new list whenever any shooting-session or shooting-set changes.
     */
    @Query(
        """
        SELECT
            ses.id,
            ses.dateTimeUtc,
            ses.comment,
            SUM(s.numberOfShots) AS totalShots
        FROM shooting_sessions ses
        LEFT JOIN shooting_sets s ON s.shootingSessionId = ses.id
        GROUP BY ses.id
        ORDER BY ses.dateTimeUtc DESC
    """
    )
    fun getAllShootingSessionsWithStats(): Flow<List<ShootingSessionWithStats>>

    /**
     * Returns a single shooting-session by ID, including the total number of shots
     * across all of its shooting-sets. Returns null if the shooting-session does not exist.
     */
    @Query(
        """
        SELECT
            ses.id,
            ses.dateTimeUtc,
            ses.comment,
            COALESCE(SUM(s.numberOfShots), 0) AS totalShots
        FROM shooting_sessions ses
        LEFT JOIN shooting_sets s ON s.shootingSessionId = ses.id
        WHERE ses.id = :shootingSessionId
        GROUP BY ses.id
    """
    )
    suspend fun getShootingSessionWithStats(shootingSessionId: Long): ShootingSessionWithStats?

    // ── Update ───────────────────────────────────────────────────────────────

    /**
     * Updates an existing shooting-session (matched by its ID).
     * Returns the number of rows affected (0 if the shooting-session was not found).
     */
    @Update
    suspend fun updateShootingSession(shootingSession: ShootingSessionEntity): Int

    // ── Delete ───────────────────────────────────────────────────────────────

    /**
     * Deletes a specific shooting-session by entity reference.
     * Because the FK is defined with CASCADE, all related shooting-sets are also deleted.
     */
    @Delete
    suspend fun deleteShootingSession(shootingSession: ShootingSessionEntity): Int

    /**
     * Deletes a shooting-session by its ID.
     * Returns the number of rows deleted (0 if the shooting-session was not found).
     */
    @Query("DELETE FROM shooting_sessions WHERE id = :shootingSessionId")
    suspend fun deleteShootingSessionById(shootingSessionId: Long): Int
}
