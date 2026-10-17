package com.example.coffee.userview.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A user as the firmware sees it: users/<UID>. */
data class CardUser(
    val uid: String,
    val name: String?,
    val credit: Long,
    /** users/<UID>/log: epoch seconds -> credit after the change */
    val log: Map<Long, Long>
)

sealed interface UserState {
    data object Loading : UserState
    data object NotFound : UserState
    data class Failed(val message: String?) : UserState
    data class Ready(val user: CardUser) : UserState
}

/** /conf as far as a user is concerned. */
data class MachineConfig(val price: Long = 0, val free: Boolean = false)

/** remote/<machine>/online, refreshed by the firmware while it accepts in-app purchases. */
data class MachinePresence(val lastSeenMs: Long, val ready: Boolean)

/**
 * Reads only the paths a user may read under the database rules (users/<UID>, conf, remote/...),
 * never the whole /users list, so the same code works in the standalone user app with anonymous auth.
 */
class UserViewRepository(
    private val root: DatabaseReference = FirebaseDatabase.getInstance().reference,
    val machineId: String = RemotePaths.DEFAULT_MACHINE_ID
) {
    fun observeUser(cardUid: String): Flow<UserState> = observe(root.child("users").child(cardUid)) { result ->
        result.fold(
            onSuccess = { snapshot ->
                val credit = (snapshot.child("credit").value as? Number)?.toLong()
                if (!snapshot.exists() || credit == null) {
                    UserState.NotFound
                } else {
                    UserState.Ready(
                        CardUser(
                            uid = cardUid,
                            name = snapshot.child("name").value as? String,
                            credit = credit,
                            log = readLog(snapshot.child("log"))
                        )
                    )
                }
            },
            onFailure = { UserState.Failed(it.message) }
        )
    }

    fun observeConfig(): Flow<MachineConfig?> = observe(root.child("conf")) { result ->
        result.getOrNull()?.let { snapshot ->
            MachineConfig(
                price = (snapshot.child("price").value as? Number)?.toLong() ?: 0L,
                free = snapshot.child("free").value as? Boolean ?: false
            )
        }
    }

    fun observePresence(): Flow<MachinePresence?> =
        observe(root.child(RemotePaths.ROOT).child(machineId).child(RemotePaths.ONLINE)) { result ->
            result.getOrNull()?.takeIf { it.exists() }?.let { snapshot ->
                MachinePresence(
                    lastSeenMs = epochMs((snapshot.child("ts").value as? Number)?.toLong() ?: 0L),
                    ready = snapshot.child("ready").value as? Boolean ?: false
                )
            }
        }

    /** ServerValue.TIMESTAMP gives milliseconds; a value in seconds (firmware epoch) is accepted too. */
    private fun epochMs(ts: Long): Long = if (ts in 1 until 100_000_000_000L) ts * 1000 else ts

    private fun readLog(log: DataSnapshot): Map<Long, Long> =
        log.children.mapNotNull { entry ->
            val key = entry.key?.toLongOrNull() ?: return@mapNotNull null
            val value = (entry.value as? Number)?.toLong() ?: return@mapNotNull null
            key to value
        }.toMap()

    private fun <T> observe(ref: DatabaseReference, map: (Result<DataSnapshot>) -> T): Flow<T> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(map(Result.success(snapshot)))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(map(Result.failure(error.toException())))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
