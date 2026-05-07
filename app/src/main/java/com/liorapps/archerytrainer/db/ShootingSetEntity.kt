package com.liorapps.archerytrainer.db


import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shooting_sets",
    foreignKeys = [
        ForeignKey(
            entity = ShootingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["shootingSessionId"],
            onDelete = ForeignKey.CASCADE  // deleting a shootingSession cascades to its shootingSets
        )
    ],
    indices = [Index("shootingSessionId")]  // index for fast look-ups by shootingSession
)
data class ShootingSetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val shootingSessionId: Long,               // mandatory FK → shootingSession.id
    val dateTimeUtc: Long,                     // stored as epoch milliseconds (UTC)
    val numberOfShots: Int,                    // mandatory
    val score: Int = -1                        // default –1  (not yet recorded)
)
