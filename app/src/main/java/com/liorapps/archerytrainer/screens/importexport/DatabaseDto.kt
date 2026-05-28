package com.liorapps.archerytrainer.screens.importexport

import android.adservices.adid.AdId
import kotlinx.serialization.Serializable

// Bump this ONLY when the structure of the JSON file itself changes
// (e.g. a field is renamed, removed, or the shape of the envelope changes).
// It is independent of Room's database schema version.
const val CURRENT_EXPORT_FORMAT_VERSION = 1

// ---------------------------------------------------------------------------
// Metadata
// ---------------------------------------------------------------------------

@Serializable
data class ExportMetadata(
    val exportFormatVersion: Int,   // version of this JSON format
    val dbSchemaVersion: Int,       // Room DB version at export time
    val exportedAt: Long,           // epoch milliseconds (UTC)
    val appVersion: String          // BuildConfig.VERSION_NAME, for diagnostics
)

// ---------------------------------------------------------------------------
// Per-table DTOs
// Keep every field from the corresponding Entity.
// Future fields added to an Entity should also be added here.
// Fields added to a DTO but absent in older JSON files are handled
// automatically by Json { ignoreUnknownKeys = true } and Kotlin default values.
// ---------------------------------------------------------------------------

@Serializable
data class ShootingSessionDto(
    val id: Long,           // original DB id — used only to reconstruct FKs on import
    val dateTimeUtc: Long,
    val comment: String = ""
)

@Serializable
data class ShootingSetDto(
    val id: Long,                   // original DB id — discarded on import
    val shootingSessionId: Long,    // original FK — remapped on import
    val dateTimeUtc: Long,
    val numberOfShots: Int,
    val score: Int = -1
)

@Serializable
data class ArrowDto(
    val id: Long,            // original DB id — used only to reconstruct FKs on import
    val shootingSetId: Long, // original FK — remapped on import
    val score: Int,
    val dateTimeUtc: Long,
)

// ---------------------------------------------------------------------------
// Root export envelope
// ---------------------------------------------------------------------------

@Serializable
data class DatabaseExportDto(
    val metadata: ExportMetadata,
    val shootingSessions: List<ShootingSessionDto>,
    val shootingSets: List<ShootingSetDto>,
    val arrows: List<ArrowDto>,
)
