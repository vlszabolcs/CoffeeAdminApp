package com.example.coffeeadmin

/**
 * Credit history in the brewpass format: users/<UID>/log/<epoch seconds> = credit AFTER the change.
 * Purchases (firmware) and admin changes (this app) are logged the same way; the type of an entry
 * follows from the difference to the previous one.
 */
object CreditLog {
    const val ACTION_PURCHASE = 0
    const val ACTION_TOP_UP = 1
    const val ACTION_INITIAL = 2
    /** Credit transfer between two cards (the firmware marks it with users/<UID>/xfer/<same key>). */
    const val ACTION_TRANSFER = 3

    /** Next log key: epoch seconds, strictly greater than every existing key (same rule as the firmware). */
    fun nextKey(nowSeconds: Long, existingKeys: Iterable<String?>): Long {
        val last = existingKeys.mapNotNull { it?.toLongOrNull() }.maxOrNull() ?: 0L
        return maxOf(nowSeconds, last + 1)
    }

    /**
     * Classifies a user's raw log. [previousCredit] is the last known credit before the first entry
     * (e.g. from the legacy /Log cache); without it the first entry counts as the initial balance.
     * [transferKeys]: log keys that are credit transfers (users/<UID>/xfer), neither purchase nor top-up.
     */
    fun toEntries(userId: String, raw: Map<Long, Long>, previousCredit: Long?, transferKeys: Set<Long> = emptySet()): List<LogEntry> {
        var prev = previousCredit
        return raw.toSortedMap().map { (timestamp, credit) ->
            val action = when {
                timestamp in transferKeys -> ACTION_TRANSFER
                prev == null -> ACTION_INITIAL
                credit < prev!! -> ACTION_PURCHASE
                else -> ACTION_TOP_UP
            }
            prev = credit
            LogEntry(
                timestamp = timestamp,
                actionValue = action.toString(),
                remainingCredit = credit,
                userId = userId
            )
        }
    }
}
