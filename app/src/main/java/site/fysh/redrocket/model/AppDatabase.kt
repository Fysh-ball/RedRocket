package site.fysh.redrocket.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Scenario::class, ResponseRecord::class, PastAlert::class, ContactSendHistory::class, LogEntry::class, BlockPhrase::class],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scenarioDao(): ScenarioDao
    abstract fun responseRecordDao(): ResponseRecordDao
    abstract fun pastAlertDao(): PastAlertDao
    abstract fun contactSendHistoryDao(): ContactSendHistoryDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun blockPhraseDao(): BlockPhraseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scenarios ADD COLUMN groups TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `app_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `eventType` TEXT NOT NULL,
                        `description` TEXT NOT NULL
                    )""".trimIndent()
                )
            }
        }

        // Adds `source` and `scenariosTriggered` to past_alerts.
        // These columns exist in the entity but were never added via migration, so devices that
        // created past_alerts before these fields were introduced have a mismatched schema.
        // The try/catch guards against the rare case where a fresh v8 install already has them.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE past_alerts ADD COLUMN source TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) { /* column already present */ }
                try {
                    database.execSQL("ALTER TABLE past_alerts ADD COLUMN scenariosTriggered TEXT NOT NULL DEFAULT ''")
                } catch (_: Exception) { /* column already present */ }
            }
        }

        // Creates block_phrases table and seeds it with the built-in English test phrases
        // so existing users retain their protection without any manual setup.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `block_phrases` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `phrase` TEXT NOT NULL
                    )"""
                )
                // Pre-populate with the hardcoded English test phrases
                listOf(
                    "this is a test",
                    "this is only a test",
                    "this is just a test",
                    "this is a drill",
                    "this is only a drill",
                    "this is just a drill",
                    "test of the alert system",
                    "test of the emergency"
                ).forEach { phrase ->
                    database.execSQL("INSERT INTO block_phrases (phrase) VALUES (?)", arrayOf(phrase))
                }
            }
        }

        /**
         * ╔══════════════════════════════════════════════════════════════╗
         * ║  MIGRATION CONTRACT - READ BEFORE BUMPING THE SCHEMA VERSION ║
         * ╠══════════════════════════════════════════════════════════════╣
         * ║  Every schema version bump REQUIRES:                         ║
         * ║    1. Increment `version` in @Database above.                ║
         * ║    2. Add a MIGRATION_N_M object (N = old, M = new).         ║
         * ║    3. Add the new migration to addMigrations() below.        ║
         * ║    4. Build once so Room generates a schema JSON under        ║
         * ║       app/schemas/ - commit that file to version control.    ║
         * ║                                                               ║
         * ║  DO NOT call fallbackToDestructiveMigration() without        ║
         * ║  explicitly listing the version numbers you intend to wipe.  ║
         * ║  Existing users on v6+ WILL lose all data if a migration     ║
         * ║  is missing - scenarios, contacts, history, everything.      ║
         * ║                                                               ║
         * ║  fallbackToDestructiveMigrationFrom(1..5): pre-beta versions ║
         * ║  never distributed publicly. Safe to destroy.                ║
         * ╚══════════════════════════════════════════════════════════════╝
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "emergency_app_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
