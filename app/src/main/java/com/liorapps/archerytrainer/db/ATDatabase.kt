package com.liorapps.archerytrainer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ShootingSessionEntity::class, ShootingSetEntity::class, ArrowEntity::class],
    version = 2,
    exportSchema = true          // set to false if you don't need schema export files
)
abstract class ATDatabase : RoomDatabase() {

    abstract fun shootingSessionDao(): ShootingSessionDao
    abstract fun shootingSetDao(): ShootingSetDao
    abstract fun arrowDao(): ArrowDao

    companion object {
        @Volatile
        private var INSTANCE: ATDatabase? = null

        /**
         * Returns the singleton database instance, creating it on the very
         * first call (thread-safe via double-checked locking).
         *
         * Call this from your Application class, a Hilt module, or wherever
         * you manage dependency injection.
         */
        fun getInstance(context: Context): ATDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ATDatabase::class.java,
                    "archery_trainer_database"
                )
                    .addMigrations(MIGRATION_1_2) // .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}


/************    Usage examples   *************
/**
 * Example ViewModel that accesses the Room database directly.
 *
 * Replace AndroidViewModel with ViewModel + constructor injection if you are
 * using Hilt or another DI framework (recommended for larger projects).
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val eventDao = db.eventDao()
    private val setDao = db.setDao()

    // ── Events ────────────────────────────────────────────────────────────────

    /** A live stream of every event, newest-first, with shot totals. */
    val allEvents: Flow<List<EventWithStats>> = eventDao.getAllEventsWithStats()

    /** Insert a new event; returns its new ID. */
    suspend fun insertEvent(dateTimeUtc: Long, comment: String = ""): Long =
        eventDao.insertEvent(EventEntity(dateTimeUtc = dateTimeUtc, comment = comment))

    /** Fetch a single event (with shot total); returns null if not found. */
    suspend fun getEvent(eventId: Long): EventWithStats? =
        eventDao.getEventWithStats(eventId)

    /** Update an existing event's fields. */
    fun updateEvent(event: EventEntity) {
        viewModelScope.launch { eventDao.updateEvent(event) }
    }

    /** Delete an event (and all its sets via CASCADE). */
    fun deleteEvent(eventId: Long) {
        viewModelScope.launch { eventDao.deleteEventById(eventId) }
    }

    // ── Sets ──────────────────────────────────────────────────────────────────

    /** A live stream of every set, newest-first, with parent-event data. */
    val allSets: Flow<List<SetWithEvent>> = setDao.getAllSetsWithEvent()

    /** A live stream of every set that belongs to [eventId], newest-first. */
    fun setsForEvent(eventId: Long): Flow<List<SetWithEvent>> =
        setDao.getSetsForEvent(eventId)

    /** Insert a new set; returns its new ID. */
    suspend fun insertSet(
        eventId: Long,
        dateTimeUtc: Long,
        numberOfShots: Int,
        score: Int = -1
    ): Long = setDao.insertSet(
        SetEntity(
            eventId = eventId,
            dateTimeUtc = dateTimeUtc,
            numberOfShots = numberOfShots,
            score = score
        )
    )

    /** Fetch a single set (with parent-event data); returns null if not found. */
    suspend fun getSet(setId: Long): SetWithEvent? =
        setDao.getSetWithEvent(setId)

    /** Update an existing set's fields. */
    fun updateSet(set: SetEntity) {
        viewModelScope.launch { setDao.updateSet(set) }
    }

    /** Delete a set by ID. */
    fun deleteSet(setId: Long) {
        viewModelScope.launch { setDao.deleteSetById(setId) }
    }
}



 ***********/