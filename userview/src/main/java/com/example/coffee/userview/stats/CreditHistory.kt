package com.example.coffee.userview.stats

/** One credit change of a user, derived from users/<UID>/log. */
data class CreditEvent(
    /** Epoch seconds (the log key). */
    val timestamp: Long,
    val type: Type,
    /** Size of the change in Ft, always >= 0 (for [Type.INITIAL] the starting balance). */
    val amount: Long,
    /** Credit after the change. */
    val balance: Long,
    /** Transfers: the other card's owner (name), if known. */
    val peer: String? = null
) {
    enum class Type { INITIAL, PURCHASE, TOP_UP, TRANSFER_OUT, TRANSFER_IN }
}

/** users/<UID>/xfer/<log key>: the log entry with the same key is a credit transfer. */
data class TransferMark(
    /** Negative: sent, positive: received. */
    val amount: Long,
    val peer: String?
)

/**
 * Classifies the brewpass credit log: users/<UID>/log/<epoch seconds> = credit AFTER the change.
 * The firmware (purchase) and the admin app (top-up, correction) log the same way, so the type
 * follows from the difference to the previous entry: decreased = purchase, increased = top-up.
 * The first entry has no previous value and counts as the starting balance. Entries without a
 * change carry no information and are skipped. Entries marked in [transfers] (same key) are credit
 * transfers between two cards instead.
 */
object CreditHistory {
    fun classify(raw: Map<Long, Long>, transfers: Map<Long, TransferMark> = emptyMap()): List<CreditEvent> {
        val events = ArrayList<CreditEvent>(raw.size)
        var previous: Long? = null
        for ((timestamp, credit) in raw.toSortedMap()) {
            val prev = previous
            previous = credit
            val transfer = transfers[timestamp]
            val event = when {
                transfer != null -> CreditEvent(
                    timestamp,
                    if (transfer.amount < 0) CreditEvent.Type.TRANSFER_OUT else CreditEvent.Type.TRANSFER_IN,
                    kotlin.math.abs(transfer.amount),
                    credit,
                    transfer.peer
                )
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
