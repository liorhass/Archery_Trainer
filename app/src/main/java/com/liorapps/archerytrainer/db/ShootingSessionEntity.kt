package com.liorapps.archerytrainer.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shooting_sessions")
data class ShootingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateTimeUtc: Long,                 // stored as epoch milliseconds (UTC)
    val comment: String = ""               // free text, up to 1000 chars
)
