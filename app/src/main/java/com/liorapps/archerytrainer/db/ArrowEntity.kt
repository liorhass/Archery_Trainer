package com.liorapps.archerytrainer.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "arrows",
    foreignKeys = [
        ForeignKey(
            entity = ShootingSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["shootingSetId"],
            onDelete = ForeignKey.CASCADE  // deleting a shootingSet cascades to its arrows
        )
    ],
    indices = [Index("shootingSetId")]  // index for fast look-ups by shootingSet
)
data class ArrowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val shootingSetId: Long,       // mandatory FK → shootingSet.id
    val score: Int,                // mandatory, 0–11
    val dateTimeUtc: Long          // stored as epoch milliseconds (UTC)
)
