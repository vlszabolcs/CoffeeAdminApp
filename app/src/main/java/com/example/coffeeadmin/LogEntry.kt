package com.example.coffeeadmin

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@IgnoreExtraProperties
@Immutable
@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey
    val timestamp: Long = 0L,
    val actionValue: String = "0", // Store as string for Room compatibility
    val remainingCredit: Long? = 0L,
    val userId: String? = null
) {
    // No-argument constructor for Firebase
    constructor() : this(0L, "0", 0L, null)

    // Firebase uses 'action', so we map it
    @get:Exclude
    val action: Any?
        get() = actionValue

    @get:Exclude
    val actionInt: Int
        get() = actionValue.toIntOrNull() ?: 0

    @get:Exclude
    val formattedTime: String
        get() {
            if (timestamp == 0L) return "Ismeretlen"
            // Assuming timestamp is in seconds if it's 10 digits
            val date = if (timestamp > 9999999999L) Date(timestamp) else Date(timestamp * 1000)
            return dateFormatter.format(date)
        }

    @get:Exclude
    val actionText: String
        get() = when (actionInt) {
            0 -> "Kávé vásárlás"
            1 -> "Kredit feltöltés"
            2 -> "Felhasználó létrehozás"
            else -> "Egyéb művelet ($actionInt)"
        }

    companion object {
        private val dateFormatter = SimpleDateFormat("yyyy.MM.dd. HH:mm:ss", Locale.getDefault())
    }
}
