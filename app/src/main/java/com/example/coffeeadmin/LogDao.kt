package com.example.coffeeadmin

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<LogEntry>)

    @Query("SELECT MAX(timestamp) FROM logs")
    suspend fun getMaxTimestamp(): Long?

    @Query("DELETE FROM logs")
    suspend fun deleteAll()
}
