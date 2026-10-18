package com.example.coffee.userview.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * In-app purchase ("virtual card tap") paths. The firmware side is designed in
 * coffeeapp/CoffeeApp-terv.md; keep these in sync with the brewpass implementation.
 *
 *   remote/<machine>/req/<id>    = {cardUid, ts: server time}   written by the app
 *   remote/<machine>/status/<id> = {state, code, credit}        written by the machine
 *   remote/<machine>/online      = {ts, ready, ...}             written by the machine
 */
object RemotePaths {
    const val DEFAULT_MACHINE_ID = "machine1"
    const val ROOT = "remote"
    const val REQUEST = "req"
    const val STATUS = "status"
    const val ONLINE = "online"

    /** The machine counts as able to take requests if it reported within this time. */
    const val PRESENCE_MAX_AGE_MS = 3 * 60 * 1000L

    /** The app gives up waiting for the machine after this time. */
    const val VEND_TIMEOUT_MS = 30_000L
}

/** Progress of one in-app purchase, as reported by the machine. */
sealed interface VendProgress {
    /** The request is written, the machine has not answered yet. */
    data object Sent : VendProgress
    /** The machine took the request and is checking the credit. */
    data object Accepted : VendProgress
    /** The credit is charged, the machine got the credit: choose a drink on the machine. */
    data class Charged(val credit: Long?) : VendProgress
    data class Done(val credit: Long?) : VendProgress
    /** [code] is the firmware error code (E30, E31, E50 ...), null if the request itself failed. */
    data class Failed(val code: Int?, val message: String? = null) : VendProgress
    data object TimedOut : VendProgress

    val finished: Boolean get() = this is Done || this is Failed || this is TimedOut
}

class RemoteVendRepository(
    private val root: DatabaseReference = FirebaseDatabase.getInstance().reference,
    val machineId: String = RemotePaths.DEFAULT_MACHINE_ID
) {
    /**
     * Sends a purchase request for [cardUid] and follows its status until the machine reports
     * done or an error. The machine charges the credit itself, exactly as for a card tap.
     */
    fun requestVend(cardUid: String): Flow<VendProgress> =
        send(mapOf("cardUid" to cardUid, "ts" to ServerValue.TIMESTAMP))

    /**
     * Asks the machine to move [amount] Ft from [cardUid] to [toUid]. The machine reads and writes both cards;
     * it answers accepted -> done (credit = the sender's new credit) | error. Only send it if the machine's
     * presence says [MachinePresence.transfer]: an older firmware would run it as a purchase.
     */
    fun requestTransfer(cardUid: String, toUid: String, amount: Long): Flow<VendProgress> =
        send(mapOf("cardUid" to cardUid, "ts" to ServerValue.TIMESTAMP, "to" to toUid, "amount" to amount))

    private fun send(fields: Map<String, Any>): Flow<VendProgress> = callbackFlow {
        val machine = root.child(RemotePaths.ROOT).child(machineId)
        val request = machine.child(RemotePaths.REQUEST).push()
        val status = machine.child(RemotePaths.STATUS).child(request.key!!)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val progress = parseStatus(snapshot) ?: return
                trySend(progress)
                if (progress.finished) close()
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(VendProgress.Failed(null, error.message))
                close()
            }
        }
        status.addValueEventListener(listener)

        request.setValue(fields)
            .addOnSuccessListener { trySend(VendProgress.Sent) }
            .addOnFailureListener {
                trySend(VendProgress.Failed(null, it.message))
                close()
            }

        awaitClose { status.removeEventListener(listener) }
    }

    private fun parseStatus(snapshot: DataSnapshot): VendProgress? {
        if (!snapshot.exists()) return null
        val credit = (snapshot.child("credit").value as? Number)?.toLong()
        return when (snapshot.child("state").value as? String) {
            "accepted" -> VendProgress.Accepted
            "charged" -> VendProgress.Charged(credit)
            "done" -> VendProgress.Done(credit)
            "error" -> VendProgress.Failed((snapshot.child("code").value as? Number)?.toInt())
            else -> null
        }
    }
}
