package com.example.coffee.userapp

import com.example.coffee.userview.data.VendProgress

/** Rules of a credit transfer; the firmware (PurchasePolicy.h) and the database rules check the same. */
object TransferRules {
    const val STEP = 100L
    const val MAX = 100_000L

    /** Whole hundreds, and the sender's credit may not go negative. */
    fun valid(amount: Long, credit: Long): Boolean =
        amount >= STEP && amount <= MAX && amount % STEP == 0L && amount <= credit
}

/** The transfer dialog: amount, then the recipient's card, then what the machine reports. */
sealed interface TransferState {
    data object EnterAmount : TransferState
    /** [ownCard]: the sender's own card was tapped instead of the recipient's. */
    data class WaitingForCard(val amount: Long, val ownCard: Boolean = false) : TransferState
    data class Sending(val amount: Long, val progress: VendProgress) : TransferState
}
