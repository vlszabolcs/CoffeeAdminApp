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

    // the last cached credit of a user before a given time (to classify the first entry of users/<UID>/log)
    @Query("SELECT remainingCredit FROM logs WHERE userId = :userId AND timestamp < :before ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCreditBefore(userId: String, before: Long): Long?

    @Query("DELETE FROM logs")
    suspend fun deleteAll()
}
