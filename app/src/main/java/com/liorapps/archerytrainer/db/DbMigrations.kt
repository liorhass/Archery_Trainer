package com.liorapps.archerytrainer.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrates the database from version 1 to version 2.
 *
 * What changed: a new `arrows` table is added. No existing tables are altered,
 * so all existing data is preserved as-is. Old shooting-sets that have no arrows
 * will continue to use their stored `numberOfShots` and `score` values, because
 * the DAO queries fall back to those stored values when no arrows exist.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `arrows` (
                `id`             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `shootingSetId`  INTEGER NOT NULL,
                `score`          INTEGER NOT NULL,
                `dateTimeUtc`    INTEGER NOT NULL,
                FOREIGN KEY(`shootingSetId`)
                    REFERENCES `shooting_sets`(`id`)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_arrows_shootingSetId`
            ON `arrows` (`shootingSetId`)
            """.trimIndent()
        )
    }
}
