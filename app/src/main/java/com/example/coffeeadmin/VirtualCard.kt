package com.example.coffeeadmin

import java.security.SecureRandom

/**
 * Virtual card: a users/<UID> entry with no physical RFID card, used only from the user app.
 * Its UID is 16 random digits. Real card UIDs are 8, 14 or 20 hex characters (4, 7 or 10 bytes),
 * so a virtual UID never equals one, and it still fits the users/<UID> format of the firmware
 * and the database rules (8-20 characters of 0-9A-F). The machine does not need to know the type.
 */
object VirtualCard {
    const val LENGTH = 16
    private val random = SecureRandom()

    fun isVirtual(uid: String?): Boolean = uid != null && uid.length == LENGTH && uid.all { it in '0'..'9' }

    /** A new virtual UID that is not in [taken]. */
    fun generate(taken: Set<String?> = emptySet()): String {
        while (true) {
            val uid = digits(LENGTH)
            if (uid !in taken) return uid
        }
    }

    internal fun digits(count: Int): String = buildString { repeat(count) { append(random.nextInt(10)) } }
}

/**
 * One-time code that lets a user app get a card without reading it (virtual card, or the card is not at hand):
 * pair/<code> = {card, exp}. The user app reads it, binds the card and deletes the code.
 */
object PairCode {
    const val PATH = "pair"
    const val LENGTH = 8
    /** the database rules allow at most 15 minutes, the rest is room for a phone clock that is a bit off */
    const val VALID_MS = 10L * 60 * 1000

    fun generate(taken: Set<String?> = emptySet()): String {
        while (true) {
            val code = VirtualCard.digits(LENGTH)
            if (code !in taken) return code
        }
    }
}
