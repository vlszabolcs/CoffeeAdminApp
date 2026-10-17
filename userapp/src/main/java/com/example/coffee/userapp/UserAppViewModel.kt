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
    data object Checking : ScanState
    /** Not registered, or already bound to another phone: the database does not tell which. */
    data object Refused : ScanState
    /** The saved card could not be claimed again at start (bound to another phone or deleted). */
    data object Lost : ScanState
    data class Failed(val message: String?) : ScanState
}

enum class NfcStatus { Unknown, Missing, Disabled, Enabled }

/**
 * The standalone user app: anonymous sign-in, one saved card read by NFC.
 * Reading a card binds it to this phone (users/<UID>/owner = auth uid); the database rules then let
 * only this phone read the card and buy with it. The app never registers a user or changes credit.
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
            onSignedIn()
            return
        }
        _auth.value = AuthState.SigningIn
        viewModelScope.launch {
            try {
                auth.signInAnonymously().await()
                onSignedIn()
            } catch (e: Exception) {
                _auth.value = AuthState.Failed(e.message)
            }
        }
    }

    /** Claims the saved card again: if the admin released it, this binds it back to this phone. */
    private fun onSignedIn() {
        _auth.value = AuthState.SignedIn
        val saved = store.card.value ?: return
        viewModelScope.launch {
            try {
                claim(saved.uid)
            } catch (e: Exception) {
                if (e.isPermissionDenied()) {
                    store.clear()
                    _scan.value = ScanState.Lost
                }
                // other errors (offline): keep the card, the user view shows the problem
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
        if (!scanning.value || _scan.value == ScanState.Checking) return
        _scan.value = ScanState.Checking
        viewModelScope.launch {
            _scan.value = try {
                claim(uid)
                val name = users.child(uid).child("name").get().await().value as? String
                val old = store.card.value
                store.save(SavedCard(uid, name))
                _replacing.value = false
                // the old card is free again for whoever reads it next; best effort
                if (old != null && old.uid != uid) users.child(old.uid).child("owner").removeValue()
                ScanState.Waiting
            } catch (e: Exception) {
                if (e.isPermissionDenied()) ScanState.Refused else ScanState.Failed(e.message)
            }
        }
    }

    fun dismissScanMessage() {
        _scan.value = ScanState.Waiting
    }

    /** Binds the card to this phone. Refused if the card is not registered or belongs to another phone. */
    private suspend fun claim(uid: String) {
        val authUid = auth.currentUser?.uid ?: error("not signed in")
        users.child(uid).child("owner").setValue(authUid).await()
    }
}

private fun Exception.isPermissionDenied(): Boolean =
    message?.contains("permission denied", ignoreCase = true) == true

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        if (error != null) cont.resumeWithException(error)
        else if (task.isCanceled) cont.cancel()
        else cont.resume(task.result)
    }
}
