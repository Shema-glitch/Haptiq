package com.haptiq.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecentSongs::class,
        SavedPresets::class,
        CalibrationProfile::class,
        Playlist::class,
        PlaylistSong::class,
        TrackEnergyMap::class,
        FavoriteSong::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun haptiqDao(): HaptiqDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Real migration — the v3→v4 destructive fallback wiped user data once;
         * every version hop from here on gets an explicit migration.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `track_energy_maps` (" +
                        "`songId` TEXT NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`frames` BLOB NOT NULL, " +
                        "`analyzedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (" +
                        "`songId` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haptiq_database"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
