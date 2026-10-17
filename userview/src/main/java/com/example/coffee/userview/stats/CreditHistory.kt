package com.example.coffee.userview.stats

/** One credit change of a user, derived from users/<UID>/log. */
data class CreditEvent(
    /** Epoch seconds (the log key). */
    val timestamp: Long,
    val type: Type,
    /** Size of the change in Ft, always >= 0 (for [Type.INITIAL] the starting balance). */
    val amount: Long,
    /** Credit after the change. */
    val balance: Long
) {
    enum class Type { INITIAL, PURCHASE, TOP_UP }
}

/**
 * Classifies the brewpass credit log: users/<UID>/log/<epoch seconds> = credit AFTER the change.
 * The firmware (purchase) and the admin app (top-up, correction) log the same way, so the type
 * follows from the difference to the previous entry: decreased = purchase, increased = top-up.
 * The first entry has no previous value and counts as the starting balance. Entries without a
 * change carry no information and are skipped.
 */
object CreditHistory {
    fun classify(raw: Map<Long, Long>): List<CreditEvent> {
        val events = ArrayList<CreditEvent>(raw.size)
        var previous: Long? = null
        for ((timestamp, credit) in raw.toSortedMap()) {
            val prev = previous
            previous = credit
            val event = when {
                prev == null -> CreditEvent(timestamp, CreditEvent.Type.INITIAL, credit, credit)
                credit < prev -> CreditEvent(timestamp, CreditEvent.Type.PURCHASE, prev - credit, credit)
                credit > prev -> CreditEvent(timestamp, CreditEvent.Type.TOP_UP, credit - prev, credit)
                else -> null
            }
            event?.let(events::add)
        }
        return events
    }
}
