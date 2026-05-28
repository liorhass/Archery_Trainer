package com.liorapps.archerytrainer.screens.importexport

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.liorapps.archerytrainer.BuildConfig
import com.liorapps.archerytrainer.db.ATDatabase
import com.liorapps.archerytrainer.db.ArrowEntity
import com.liorapps.archerytrainer.db.ShootingSessionEntity
import com.liorapps.archerytrainer.db.ShootingSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface OperationResult {
    data class Success(val message: String) : OperationResult
    data class Failure(val message: String)   : OperationResult
}
sealed class DbExportResult {
    /** @param displayName A human-readable file name (or full path on API < 29) suitable for showing in a Snackbar. */
    data class Success(val displayName: String) : DbExportResult()
    data class Failure(val exception: Exception) : DbExportResult()
}
sealed class DbImportResult {
    /** @param sessionsImported Number of ShootingSession rows inserted
     *  @param setsImported     Number of ShootingSet rows inserted */
    data class Success(
        val sessionsImported: Int,
        val setsImported: Int,
        val arrowsImported: Int,
    ) : DbImportResult()

    /** The file's exportFormatVersion is higher than [CURRENT_EXPORT_FORMAT_VERSION].
     *  The import was aborted - no data was written.
     *  @param version The unsupported version found in the file */
    data class UnsupportedFormatVersion(val version: Int) : DbImportResult()

    data class Failure(val exception: Exception) : DbImportResult()
}


data class ImportExportUiState(
    val exportResult: OperationResult?   = null,
    val importResult: OperationResult?   = null,
    val shouldOpenFilePicker: Boolean    = false,
)

class ImportExportViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ImportExportUiState())
    val uiState: StateFlow<ImportExportUiState> = _uiState.asStateFlow()

    private val exportJson = Json {
        prettyPrint = true       // Human-readable output (negligible size cost for this data)
        ignoreUnknownKeys = true // Survive importing a file that was exported by a NEWER version of the app that added new JSON fields
    }

    val db: ATDatabase = ATDatabase.getInstance(application)
    val sessionDao     = db.shootingSessionDao()
    val setDao         = db.shootingSetDao()
    val arrowDao       = db.arrowDao()

    /** User tapped "Export to File". ViewModel runs the export and updates [ImportExportUiState.exportResult]. */
    fun onExportClicked() {
        Timber.d("#### onExportClicked()")
        viewModelScope.launch {
            val exportResult = exportDatabase()
            val operationResult = when (exportResult) {
                is DbExportResult.Success -> {
                    OperationResult.Success("Data exported to file: ${exportResult.displayName} (in the \"Downloads\" directory)")
                }
                is DbExportResult.Failure -> {
                    OperationResult.Failure("Data export failed: ${exportResult.exception.message}")
                }
            }
            _uiState.update { it.copy(exportResult = operationResult) }
        }
    }

    /** User tapped "Import from File". Signal the UI to open the file picker */
    fun onImportClicked() {
        _uiState.update { it.copy(shouldOpenFilePicker = true) }
    }
    /** UI confirms the file picker was launched; resets the one-shot flag. */
    fun onFilePickerLaunched() {
        _uiState.update { it.copy(shouldOpenFilePicker = false) }
    }

    /** Called when the user selected a file for import in the file-picker. */
    fun onImportFileSelected(uri: Uri) {
        Timber.d("#### onImportFileSelected() URI=${uri}")
        viewModelScope.launch {
            val importResult = importDatabase(uri)
            val operationResult = when (importResult) {
                is DbImportResult.Success -> {
                    OperationResult.Success("Imported ${importResult.sessionsImported} sessions, ${importResult.setsImported} sets")
                }
                is DbImportResult.Failure -> {
                    OperationResult.Failure("Data import failed: ${importResult.exception.message}")
                }
                is DbImportResult.UnsupportedFormatVersion -> {
                    OperationResult.Failure("Data import failed: File format version (${importResult.version}) is newer than what we support")
                }
            }
            _uiState.update { it.copy(importResult = operationResult) }
        }
    }

    fun onExportResultDismissed() { _uiState.update { it.copy(exportResult = null) } }
    fun onImportResultDismissed() { _uiState.update { it.copy(importResult = null) } }

// ---------------------------------------------------------------------------
// EXPORT
// ---------------------------------------------------------------------------
    suspend fun exportDatabase(): DbExportResult = withContext(Dispatchers.IO) {
        try {
            val sessions   = sessionDao.getAllShootingSessions()
            val sets       = setDao.getAllShootingSetsRawData()
            val arrows     = arrowDao.getAllArrows()

            val exportDto = DatabaseExportDto(
                metadata = ExportMetadata(
                    exportFormatVersion = CURRENT_EXPORT_FORMAT_VERSION,
                    dbSchemaVersion = db.openHelper.readableDatabase.version,
                    exportedAt = System.currentTimeMillis(),
                    appVersion = BuildConfig.VERSION_NAME
                ),
                shootingSessions = sessions.map { it.toDto() },
                shootingSets = sets.map { it.toDto() },
                arrows = arrows.map { it.toDto() },
            )

            val jsonString = exportJson.encodeToString(exportDto)
            val fileName = "archery_trainer_export_${fileNameTimestamp()}.json"

            val displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeToDownloadsQ(fileName, jsonString)
            } else {
                writeToDownloadsLegacy(fileName, jsonString)
            }

            DbExportResult.Success(displayName)
        } catch (e: Exception) {
            DbExportResult.Failure(e)
        }
    }

    // API 29+: use MediaStore - no storage permissions required
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDownloadsQ(fileName: String, content: String): String {
        val resolver = getApplication<Application>().contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)     // reserve the entry while writing
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("MediaStore did not return a Uri for the new file")

        resolver.openOutputStream(uri)
            ?.use { stream -> stream.write(content.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Could not open output stream for $uri")

        // Publish the file so it is visible to other apps / the user
        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)

        return fileName  // returned to show in the UI
    }

// API < 29: write directly to the public Downloads directory.
// Requires WRITE_EXTERNAL_STORAGE in the manifest; the UI layer must obtain
// the permission before calling exportDatabase().
    @Suppress("DEPRECATION")
    private fun writeToDownloadsLegacy(fileName: String, content: String): String {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file.absolutePath
    }

// ---------------------------------------------------------------------------
// IMPORT
// ---------------------------------------------------------------------------
    // uri comes from the UI layer's file picker (ACTION_OPEN_DOCUMENT / GetContent).
    // No storage permissions are needed when reading through ContentResolver this way.
    suspend fun importDatabase(uri: Uri): DbImportResult = withContext(Dispatchers.IO) {
        try {
            // 1. Read the file
            val resolver = getApplication<Application>().contentResolver
            val jsonString = resolver.openInputStream(uri)
                ?.use { stream -> stream.bufferedReader(Charsets.UTF_8).readText() }
                ?: throw IOException("Could not open input stream for $uri")

            // 2. Parse
            val exportDto = exportJson.decodeFromString<DatabaseExportDto>(jsonString)

            // 3. Guard against files produced by a future (incompatible) app version
            if (exportDto.metadata.exportFormatVersion > CURRENT_EXPORT_FORMAT_VERSION) {
                return@withContext DbImportResult.UnsupportedFormatVersion(
                    exportDto.metadata.exportFormatVersion
                )
            }

            // 4. Merge into the DB inside a single transaction so a mid-import
            //    failure leaves the database completely untouched
            var sessionsImported = 0
            var setsImported = 0
            var arrowsImported = 0

            db.withTransaction {
                // Map: original id in the JSON → newly assigned id in this database
                val sessionIdMap = mutableMapOf<Long, Long>()
                val setIdMap = mutableMapOf<Long, Long>()

                for (dto in exportDto.shootingSessions) {
                    val newId = sessionDao.insertShootingSession(
                        ShootingSessionEntity(
                            id = 0,                 // 0 → Room auto-generates a new id
                            dateTimeUtc = dto.dateTimeUtc,
                            comment = dto.comment
                        )
                    )
                    sessionIdMap[dto.id] = newId
                    sessionsImported++
                }

                for (dto in exportDto.shootingSets) {
                    val remappedSessionId = sessionIdMap[dto.shootingSessionId]
                        ?: throw IllegalStateException(
                            "ShootingSet (original id=${dto.id}) references " +
                                    "unknown session id ${dto.shootingSessionId}. " +
                                    "The export file may be corrupt."
                        )

                    val newId = setDao.insertShootingSet(
                        ShootingSetEntity(
                            id = 0,                             // 0 -> Room auto-generates
                            shootingSessionId = remappedSessionId,
                            dateTimeUtc = dto.dateTimeUtc,
                            numberOfShots = dto.numberOfShots,
                            score = dto.score
                        )
                    )
                    setIdMap[dto.id] = newId
                    setsImported++
                }

                for (dto in exportDto.arrows) {
                    val remappedSetId = setIdMap[dto.shootingSetId]
                        ?: throw IllegalStateException(
                            "Arrow (original id=${dto.id}) references " +
                                    "unknown set id ${dto.shootingSetId}. " +
                                    "The export file may be corrupt."
                        )

                    arrowDao.insert(
                        ArrowEntity(
                            id = 0,                             // 0 -> Room auto-generates
                            shootingSetId = remappedSetId,
                            score = dto.score,
                            dateTimeUtc = dto.dateTimeUtc,
                        )
                    )
                    arrowsImported++
                }
            }

            DbImportResult.Success(sessionsImported, setsImported, arrowsImported)
        } catch (e: Exception) {
            DbImportResult.Failure(e)
        }
    }

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

    /** Converts Entity → DTO for export. Kept private to the ViewModel. */
    private fun ShootingSessionEntity.toDto() = ShootingSessionDto(
        id = id,
        dateTimeUtc = dateTimeUtc,
        comment = comment
    )

    private fun ShootingSetEntity.toDto() = ShootingSetDto(
        id = id,
        shootingSessionId = shootingSessionId,
        dateTimeUtc = dateTimeUtc,
        numberOfShots = numberOfShots,
        score = score
    )

    private fun ArrowEntity.toDto() = ArrowDto(
        id = id,
        shootingSetId = shootingSetId,
        score = score,
        dateTimeUtc = dateTimeUtc
    )

    /** Produces a filesystem-safe timestamp string, e.g. "2025-03-01_14-05-30". */
    private fun fileNameTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

//    private fun loadAppInfo() {
//        val context = getApplication<Application>()
//        try {
//            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
//            val versionName = packageInfo.versionName ?: "Unknown"
//            val versionCode = packageInfo.longVersionCode.toString()
//
//            val buildTime = BuildConfig.BUILD_TIME  // e.g. 1747137000000L
//            val formattedBuildDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
//                .format(Date(buildTime))
//            // With the modern API (API 26+)
////            val instant = Instant.ofEpochMilli(buildTime)
////                .atZone(ZoneId.systemDefault())
////                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
//
//            _uiState.update { ImportExportUiState(
//                versionName = versionName,
//                versionCode = versionCode,
//                buildDateTime = formattedBuildDateTime,
//                githubUrl = APP_GITHUB_URL
//            ) }
//        } catch (e: PackageManager.NameNotFoundException) {
//            // Fallback state if package info cannot be retrieved
//            _uiState.update { ImportExportUiState(
//                versionName = "Error",
//                versionCode = "Error",
//                buildDateTime = "Error",
//                githubUrl = APP_GITHUB_URL
//            ) }
//        }
//    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ImportExportViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ImportExportViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

}
