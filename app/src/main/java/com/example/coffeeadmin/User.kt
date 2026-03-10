package com.example.coffeeadmin

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String? = null,
    val name: String? = null,
    val credit: Any? = 0,
    val loan: Any? = 0
) {
    // No-argument constructor for Firebase
    constructor() : this(null, null, 0, 0)

    @get:Exclude
    val creditInt: Int
        get() = when (credit) {
            is Number -> credit.toInt()
            is Boolean -> if (credit) 1 else 0
            is String -> credit.toIntOrNull() ?: 0
            else -> 0
        }

    @get:Exclude
    val loanInt: Int
        get() = when (loan) {
            is Number -> loan.toInt()
            is Boolean -> if (loan) 1 else 0
            is String -> loan.toIntOrNull() ?: 0
            else -> 0
        }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "credit" to creditInt,
            "loan" to loanInt
        )
    }
}
