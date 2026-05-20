package com.liorapps.archerytrainer.db


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShootingSetDao {

    // ── Create ───────────────────────────────────────────────────────────────
    /**
     * Inserts a new shootingSet and returns its auto-generated ID.
     * Fails (throws) if the referenced shootingSessionId does not exist.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShootingSet(shootingSetEntity: ShootingSetEntity): Long

    // ── Read ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM shooting_sets")
    fun getAllShootingSets(): List<ShootingSetEntity>

    /**
     * Returns all shootingSets, ordered by date-time descending, each carrying its
     * parent shootingSession's date-time and comment.
     * Emits a new list whenever any shootingSet or shootingSession changes.
     */
    @Query(
        """
        SELECT
            s.id,
            s.shootingSessionId,
            s.dateTimeUtc,
            s.numberOfShots,
            s.score,
            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
            e.comment       AS shootingSessionComment
        FROM shooting_sets s
        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
        ORDER BY s.dateTimeUtc DESC
    """
    )
    fun getAllShootingSetsWithShootingSession(): Flow<List<ShootingSetWithSession>>

    /**
     * Returns a single shootingSet by ID, carrying its parent shootingSession's data.
     * Returns null if no shootingSet with that ID exists.
     */
    @Query(
        """
        SELECT
            s.id,
            s.shootingSessionId,
            s.dateTimeUtc,
            s.numberOfShots,
            s.score,
            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
            e.comment       AS shootingSessionComment
        FROM shooting_sets s
        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
        WHERE s.id = :shootingSetId
    """
    )
    suspend fun getShootingSetWithShootingSession(shootingSetId: Long): ShootingSetWithSession?

    /**
     * Returns all shootingSets that belong to the given shootingSession, ordered by date-time
     * descending, each carrying the parent shootingSession's data.
     * Emits a new list whenever any of the matching shootingSets changes.
     */
    @Query(
        """
        SELECT
            s.id,
            s.shootingSessionId,
            s.dateTimeUtc,
            s.numberOfShots,
            s.score,
            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
            e.comment       AS shootingSessionComment
        FROM shooting_sets s
        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
        WHERE s.shootingSessionId = :shootingSessionId
        ORDER BY s.dateTimeUtc DESC
    """
    )
    fun getShootingSetsForShootingSession(shootingSessionId: Long): Flow<List<ShootingSetWithSession>>

    // ── Update ───────────────────────────────────────────────────────────────

    /**
     * Updates an existing shootingSet (matched by its ID).
     * Returns the number of rows affected (0 if the shootingSet was not found).
     */
    @Update
    suspend fun updateShootingSet(shootingSetEntity: ShootingSetEntity): Int

    // ── Delete ───────────────────────────────────────────────────────────────

    /**
     * Deletes a specific shootingSet by entity reference.
     */
    @Delete
    suspend fun deleteShootingSet(shootingSetEntity: ShootingSetEntity): Int

    /**
     * Deletes a shootingSet by its ID.
     * Returns the number of rows deleted (0 if the shootingSet was not found).
     */
    @Query("DELETE FROM shooting_sets WHERE id = :shootingSetId")
    suspend fun deleteShootingSetById(shootingSetId: Long): Int
}
