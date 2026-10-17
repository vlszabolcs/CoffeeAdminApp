package com.example.coffee.userapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AuthState {
    data object SigningIn : AuthState
    data class Failed(val message: String?) : AuthState
    data object SignedIn : AuthState
}

/** What the card reading screen shows. */
sealed interface ScanState {
    data object Waiting : ScanState
    data class Checking(val uid: String) : ScanState
    data class NotRegistered(val uid: String) : ScanState
    data class Failed(val message: String?) : ScanState
    /** A different card than the saved one was read; it replaces the old card only after confirmation. */
    data class ConfirmReplace(val old: SavedCard, val new: SavedCard) : ScanState
}

enum class NfcStatus { Unknown, Missing, Disabled, Enabled }

/**
 * The standalone user app: anonymous sign-in, one saved card read by NFC.
 * The app never registers or changes a user; it only checks that users/<UID> exists.
 */
class UserAppViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val users = FirebaseDatabase.getInstance().reference.child("users")
    private val store = CardStore(application)

    val card: StateFlow<SavedCard?> = store.card

    private val _auth = MutableStateFlow<AuthState>(AuthState.SigningIn)
    val authState: StateFlow<AuthState> = _auth.asStateFlow()

    /** The user asked to read another card while one is saved. */
    private val _replacing = MutableStateFlow(false)
    val replacing: StateFlow<Boolean> = _replacing.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Waiting)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    val nfcStatus = MutableStateFlow(NfcStatus.Unknown)

    /** NFC reading is on only while the card reading screen is shown. */
    val scanning: StateFlow<Boolean> = combine(card, replacing, authState) { card, replacing, auth ->
        auth == AuthState.SignedIn && (card == null || replacing)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        signIn()
    }

    fun signIn() {
        if (auth.currentUser != null) {
            _auth.value = AuthState.SignedIn
            return
        }
        _auth.value = AuthState.SigningIn
        viewModelScope.launch {
            _auth.value = try {
                auth.signInAnonymously().await()
                AuthState.SignedIn
            } catch (e: Exception) {
                AuthState.Failed(e.message)
            }
        }
    }

    fun startReplace() {
        _scan.value = ScanState.Waiting
        _replacing.value = true
    }

    fun cancelReplace() {
        _replacing.value = false
        _scan.value = ScanState.Waiting
    }

    /** Called from the NFC reader thread. */
    fun onCardRead(uid: String) {
        if (!scanning.value || _scan.value is ScanState.Checking || _scan.value is ScanState.ConfirmReplace) return
        _scan.value = ScanState.Checking(uid)
        viewModelScope.launch {
            _scan.value = try {
                val snapshot = users.child(uid).get().await()
                if (!snapshot.child("credit").exists()) {
                    ScanState.NotRegistered(uid)
                } else {
                    val found = SavedCard(uid, snapshot.child("name").value as? String)
                    val old = store.card.value
                    if (old == null || old.uid == uid) {
                        save(found)
                        ScanState.Waiting
                    } else {
                        ScanState.ConfirmReplace(old, found)
                    }
                }
            } catch (e: Exception) {
                ScanState.Failed(e.message)
            }
        }
    }

    fun confirmReplace() {
        val state = _scan.value as? ScanState.ConfirmReplace ?: return
        save(state.new)
        _scan.value = ScanState.Waiting
    }

    fun rejectReplace() {
        _scan.value = ScanState.Waiting
    }

    fun dismissScanMessage() {
        _scan.value = ScanState.Waiting
    }

    private fun save(card: SavedCard) {
        store.save(card)
        _replacing.value = false
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) cont.resumeWithException(error)
        else if (task.isCanceled) cont.cancel()
        else cont.resume(task.result)
    }
}
