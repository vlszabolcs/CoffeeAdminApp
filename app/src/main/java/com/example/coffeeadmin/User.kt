package com.example.coffeeadmin

import androidx.compose.runtime.Immutable
import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@IgnoreExtraProperties
@Immutable
data class User(
    val uid: String? = null,
    val name: String? = null,
    val credit: Long? = 0L,
    val loan: Long? = 0L,
    // Legacy: last change time written by the old app. Only read as a fallback for users without a log.
    @get:PropertyName("update")
    val update: Long? = 0L,
    // Latest key of users/<UID>/log (epoch seconds). Filled from the snapshot, not stored under this name.
    @get:Exclude
    val lastLog: Long = 0L,
    // Anonymous auth uid of the user app's phone this card is bound to (users/<UID>/owner), null if none.
    val owner: String? = null
) {
    // No-argument constructor for Firebase
    constructor() : this(null, null, 0L, 0L, 0L, 0L)

    @get:Exclude
    val creditLong: Long
        get() = credit ?: 0L

    @get:Exclude
    val loanLong: Long
        get() = loan ?: 0L

    @get:Exclude
    val updateLong: Long
        get() = update ?: 0L

    // last credit change (purchase or admin change)
    @get:Exclude
    val lastActivity: Long
        get() = maxOf(lastLog, updateLong)

    @get:Exclude
    val creditInt: Int
        get() = creditLong.toInt()

    @get:Exclude
    val loanInt: Int
        get() = loanLong.toInt()

    @get:Exclude
    val formattedUpdateTime: String
        get() {
            if (lastActivity == 0L) return "Soha"
            return dateFormatter.format(Date(lastActivity * 1000))
        }

    companion object {
        private val dateFormatter = SimpleDateFormat("yyyy.MM.dd. HH:mm", Locale.getDefault())
    }
}
