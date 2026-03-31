package site.fysh.redrocket.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Scenario::class, ResponseRecord::class, PastAlert::class, ContactSendHistory::class, LogEntry::class],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scenarioDao(): ScenarioDao
    abstract fun responseRecordDao(): ResponseRecordDao
    abstract fun pastAlertDao(): PastAlertDao
    abstract fun contactSendHistoryDao(): ContactSendHistoryDao
    abstract fun logEntryDao(): LogEntryDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "emergency_app_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
