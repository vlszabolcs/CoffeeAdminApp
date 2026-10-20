package com.example.coffeeadmin

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Local cache of users/<UID>/log (see CreditLog). Two users can have an entry in the same second,
// so the key is (userId, timestamp). userId is "" for legacy /Log entries that had no user.
@IgnoreExtraProperties
@Immutable
@Entity(tableName = "logs", primaryKeys = ["userId", "timestamp"])
data class LogEntry(
    val timestamp: Long = 0L,
    val actionValue: String = "0", // Store as string for Room compatibility
    val remainingCredit: Long? = 0L,
    val userId: String = ""
) {
    // No-argument constructor for Firebase
    constructor() : this(0L, "0", 0L, "")

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
            CreditLog.ACTION_PURCHASE -> "Kávé vásárlás"
            CreditLog.ACTION_TOP_UP -> "Kredit feltöltés"
            CreditLog.ACTION_INITIAL -> "Kezdő egyenleg"
            CreditLog.ACTION_TRANSFER -> "Átutalás"
            else -> "Egyéb művelet ($actionInt)"
        }

    companion object {
        private val dateFormatter = SimpleDateFormat("yyyy.MM.dd. HH:mm:ss", Locale.getDefault())
    }
}
