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
    @get:PropertyName("update")
    val update: Long? = 0L
) {
    // No-argument constructor for Firebase
    constructor() : this(null, null, 0L, 0L, 0L)

    @get:Exclude
    val creditLong: Long
        get() = credit ?: 0L

    @get:Exclude
    val loanLong: Long
        get() = loan ?: 0L

    @get:Exclude
    val updateLong: Long
        get() = update ?: 0L

    @get:Exclude
    val creditInt: Int
        get() = creditLong.toInt()

    @get:Exclude
    val loanInt: Int
        get() = loanLong.toInt()

    @get:Exclude
    val formattedUpdateTime: String
        get() {
            if (updateLong == 0L) return "Soha"
            return dateFormatter.format(Date(updateLong * 1000))
        }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "credit" to creditLong,
            "loan" to loanLong,
            "update" to updateLong
        )
    }

    companion object {
        private val dateFormatter = SimpleDateFormat("yyyy.MM.dd. HH:mm", Locale.getDefault())
    }
}
