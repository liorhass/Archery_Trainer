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

    /** Get the raw shooting_sets table data. The number-of-shots and score are taken as-is,
     * not taking arrows into account. Should only be used for DB export */
    @Query("SELECT * FROM shooting_sets")
    suspend fun getAllShootingSetsRawData(): List<ShootingSetEntity>

//    /**
//     * Returns all shootingSets, ordered by date-time descending, each carrying its
//     * parent shootingSession's date-time and comment.
//     * Emits a new list whenever any shootingSet or shootingSession changes.
//     */
//    @Query(
//        """
//        SELECT
//            s.id,
//            s.shootingSessionId,
//            s.dateTimeUtc,
//            s.numberOfShots,
//            s.score,
//            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
//            e.comment       AS shootingSessionComment
//        FROM shooting_sets s
//        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
//        ORDER BY s.dateTimeUtc DESC
//    """
//    )
//    fun getAllShootingSetsWithShootingSession(): Flow<List<ShootingSetWithSession>>
    /**
     * Returns all shootingSets, ordered by date-time descending, each carrying its
     * parent shootingSession's date-time and comment.
     *
     * numberOfShots = number of arrows in the set when arrows exist, otherwise the stored numberOfShots.
     * score         = SUM of arrow scores when arrows exist, otherwise the stored score.
     *
     * Emits a new list whenever any shootingSet, shootingSession, or arrow changes.
     */
    @Query(
        """
        SELECT
            s.id,
            s.shootingSessionId,
            s.dateTimeUtc,
            CASE WHEN COUNT(a.id) > 0 THEN COUNT(a.id) ELSE s.numberOfShots END AS numberOfShots,
            CASE WHEN COUNT(a.id) > 0 THEN SUM(a.score) ELSE s.score END   AS score,
            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
            e.comment       AS shootingSessionComment
        FROM shooting_sets s
        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
        LEFT JOIN arrows a ON a.shootingSetId = s.id
        GROUP BY s.id
        ORDER BY s.dateTimeUtc DESC
    """
    )
    fun getAllShootingSetsWithShootingSession(): Flow<List<ShootingSetWithSession>>

//    /**
//     * Returns a single shootingSet by ID, carrying its parent shootingSession's data.
//     * Returns null if no shootingSet with that ID exists.
//     */
//    @Query(
//        """
//        SELECT
//            s.id,
//            s.shootingSessionId,
//            s.dateTimeUtc,
//            s.numberOfShots,
//            s.score,
//            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
//            e.comment       AS shootingSessionComment
//        FROM shooting_sets s
//        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
//        WHERE s.id = :shootingSetId
//    """
//    )
//    suspend fun getShootingSetWithShootingSession(shootingSetId: Long): ShootingSetWithSession?
    /**
     * Returns a single shootingSet by ID, carrying its parent shootingSession's data.
     * Returns null if no shootingSet with that ID exists.
     *
     * numberOfShots = number of arrows in the set when arrows exist, otherwise the stored numberOfShots.
     * score         = SUM of arrow scores when arrows exist, otherwise the stored score.
     */
    @Query(
        """
        SELECT
            s.id,
            s.shootingSessionId,
            s.dateTimeUtc,
            CASE WHEN COUNT(a.id) > 0 THEN COUNT(a.id) ELSE s.numberOfShots END AS numberOfShots,
            CASE WHEN COUNT(a.id) > 0 THEN SUM(a.score) ELSE s.score END        AS score,
            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
            e.comment       AS shootingSessionComment
        FROM shooting_sets s
        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
        LEFT JOIN arrows a ON a.shootingSetId = s.id
        WHERE s.id = :shootingSetId
        GROUP BY s.id
    """
    )
    suspend fun getShootingSetWithShootingSession(shootingSetId: Long): ShootingSetWithSession?

//    /**
//     * Returns all shootingSets that belong to the given shootingSession, ordered by date-time
//     * descending, each carrying the parent shootingSession's data.
//     * Emits a new list whenever any of the matching shootingSets changes.
//     */
//    @Query(
//        """
//        SELECT
//            s.id,
//            s.shootingSessionId,
//            s.dateTimeUtc,
//            s.numberOfShots,
//            s.score,
//            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
//            e.comment       AS shootingSessionComment
//        FROM shooting_sets s
//        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
//        WHERE s.shootingSessionId = :shootingSessionId
//        ORDER BY s.dateTimeUtc DESC
//    """
//    )
//    fun getShootingSetsForShootingSession(shootingSessionId: Long): Flow<List<ShootingSetWithSession>>
    /**
     * Returns all shootingSets that belong to the given shootingSession, ordered by date-time
     * descending, each carrying the parent shootingSession's data.
     *
     * numberOfShots = number of arrows in the set when arrows exist, otherwise the stored numberOfShots.
     * score         = SUM of arrow scores when arrows exist, otherwise the stored score.
     *
     * Emits a new list whenever any of the matching shootingSets or their arrows change.
     */
    @Query(
        """
        SELECT
            s.id,
            s.shootingSessionId,
            s.dateTimeUtc,
            CASE WHEN COUNT(a.id) > 0 THEN COUNT(a.id) ELSE s.numberOfShots END AS numberOfShots,
            CASE WHEN COUNT(a.id) > 0 THEN SUM(a.score) ELSE s.score END   AS score,
            e.dateTimeUtc   AS shootingSessionDateTimeUtc,
            e.comment       AS shootingSessionComment
        FROM shooting_sets s
        INNER JOIN shooting_sessions e ON e.id = s.shootingSessionId
        LEFT JOIN arrows a ON a.shootingSetId = s.id
        WHERE s.shootingSessionId = :shootingSessionId
        GROUP BY s.id
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
