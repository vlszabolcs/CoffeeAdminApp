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

    // the newest cached entry of every user (to sync only the new entries of users/<UID>/log)
    @Query(
        "SELECT l.* FROM logs l JOIN (SELECT userId, MAX(timestamp) AS ts FROM logs GROUP BY userId) m " +
            "ON l.userId = m.userId AND l.timestamp = m.ts"
    )
    suspend fun getLatestPerUser(): List<LogEntry>

    @Query("DELETE FROM logs")
    suspend fun deleteAll()
}
