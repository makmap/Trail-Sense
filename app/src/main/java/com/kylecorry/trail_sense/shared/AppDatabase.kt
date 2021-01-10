package com.kylecorry.trail_sense.shared

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kylecorry.trail_sense.navigation.domain.BeaconEntity
import com.kylecorry.trail_sense.navigation.domain.BeaconGroupEntity
import com.kylecorry.trail_sense.navigation.infrastructure.persistence.BeaconDao
import com.kylecorry.trail_sense.navigation.infrastructure.persistence.BeaconDatabaseMigrationWorker
import com.kylecorry.trail_sense.navigation.infrastructure.persistence.BeaconGroupDao
import com.kylecorry.trail_sense.tools.backtrack.domain.WaypointEntity
import com.kylecorry.trail_sense.tools.backtrack.infrastructure.persistence.WaypointDao
import com.kylecorry.trail_sense.tools.inventory.domain.InventoryItem
import com.kylecorry.trail_sense.tools.inventory.infrastructure.InventoryItemDao
import com.kylecorry.trail_sense.tools.notes.domain.Note
import com.kylecorry.trail_sense.tools.notes.infrastructure.NoteDao
import com.kylecorry.trail_sense.weather.domain.PressureReadingEntity
import com.kylecorry.trail_sense.weather.infrastructure.persistence.PressureDatabaseMigrationWorker
import com.kylecorry.trail_sense.weather.infrastructure.persistence.PressureReadingDao

/**
 * The Room database for this app
 */
@Database(
    entities = [InventoryItem::class, Note::class, WaypointEntity::class, PressureReadingEntity::class, BeaconEntity::class, BeaconGroupEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun waypointDao(): WaypointDao
    abstract fun pressureDao(): PressureReadingDao
    abstract fun beaconDao(): BeaconDao
    abstract fun beaconGroupDao(): BeaconGroupDao
    abstract fun noteDao(): NoteDao

    companion object {

        // For Singleton instantiation
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {

            val MIGRATION_1_2 = object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("CREATE TABLE IF NOT EXISTS `notes` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT, `contents` TEXT, `created` INTEGER NOT NULL)")
                }
            }

            val MIGRATION_2_3 = object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("CREATE TABLE IF NOT EXISTS `waypoints` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitude` REAL, `createdOn` INTEGER NOT NULL)")
                }
            }

            val MIGRATION_3_4 = object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("CREATE TABLE IF NOT EXISTS `pressures` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pressure` REAL NOT NULL, `altitude` REAL NOT NULL, `altitude_accuracy` REAL, `temperature` REAL NOT NULL, `time` INTEGER NOT NULL)")
                    val request =
                        OneTimeWorkRequestBuilder<PressureDatabaseMigrationWorker>().build()
                    WorkManager.getInstance(context).enqueue(request)
                }
            }

            val MIGRATION_4_5 = object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("CREATE TABLE IF NOT EXISTS `beacons` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `visible` INTEGER NOT NULL DEFAULT 1, `comment` TEXT DEFAULT NULL, `beacon_group_id` INTEGER DEFAULT NULL, `elevation` REAL DEFAULT NULL)")
                    database.execSQL("CREATE TABLE IF NOT EXISTS `beacon_groups` (`_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                    val request =
                        OneTimeWorkRequestBuilder<BeaconDatabaseMigrationWorker>().build()
                    WorkManager.getInstance(context).enqueue(request)
                }
            }

            return Room.databaseBuilder(context, AppDatabase::class.java, "inventory")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5
                )
                .build()
        }
    }
}