package com.example.coffeeadmin

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LogEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao

    companion object {
        // v2: key (userId, timestamp) instead of timestamp alone - the brewpass firmware logs per user
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE logs_new (timestamp INTEGER NOT NULL, actionValue TEXT NOT NULL, " +
                        "remainingCredit INTEGER, userId TEXT NOT NULL, PRIMARY KEY(userId, timestamp))"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO logs_new (timestamp, actionValue, remainingCredit, userId) " +
                        "SELECT timestamp, actionValue, remainingCredit, IFNULL(userId, '') FROM logs"
                )
                db.execSQL("DROP TABLE logs")
                db.execSQL("ALTER TABLE logs_new RENAME TO logs")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coffee_admin_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
