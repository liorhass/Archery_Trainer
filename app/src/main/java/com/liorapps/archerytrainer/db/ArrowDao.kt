package com.liorapps.archerytrainer.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArrowDao {

    /** Inserts a single arrow and returns its generated ID. */
    @Insert
    suspend fun insert(arrow: ArrowEntity): Long

    /** Deletes a single arrow by its entity (matched by primary key). */
    @Delete
    suspend fun delete(arrow: ArrowEntity)

    @Query("SELECT * FROM arrows")
    suspend fun getAllArrows(): List<ArrowEntity>

    /** Returns all arrows that belong to the given shootingSet, ordered by
     * date-time descending (newest first).
     * Emits a new list whenever any arrow in that set changes */
    @Query(
        """
        SELECT *
        FROM arrows
        WHERE shootingSetId = :shootingSetId
        ORDER BY dateTimeUtc DESC
    """
    )
    fun getArrowsForShootingSetSortedByDateTime(shootingSetId: Long): Flow<List<ArrowEntity>>

    /** Returns all arrows that belong to the given shootingSet, ordered by
     * score ascending (highest score first).
     * Emits a new list whenever any arrow in that set changes */
    @Query(
        """
        SELECT *
        FROM arrows
        WHERE shootingSetId = :shootingSetId
        ORDER BY score DESC
    """
    )
    fun getArrowsForShootingSetSortedByScore(shootingSetId: Long): Flow<List<ArrowEntity>>
}
