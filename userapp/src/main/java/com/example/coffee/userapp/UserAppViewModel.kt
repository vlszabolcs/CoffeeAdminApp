package com.example.coffee.userapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.coffee.userview.data.MachinePresence
import com.example.coffee.userview.data.RemotePaths
import com.example.coffee.userview.data.RemoteVendRepository
import com.example.coffee.userview.data.UserState
import com.example.coffee.userview.data.UserViewRepository
import com.example.coffee.userview.data.VendProgress
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
@OptIn(ExperimentalCoroutinesApi::class)
class UserAppViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val users = FirebaseDatabase.getInstance().reference.child("users")
    private val store = CardStore(application)
    private val userRepository = UserViewRepository()
    private val vendRepository = RemoteVendRepository(machineId = userRepository.machineId)

    val card: StateFlow<SavedCard?> = store.card

    private val _auth = MutableStateFlow<AuthState>(AuthState.SigningIn)
    val authState: StateFlow<AuthState> = _auth.asStateFlow()

    /** The user asked to read another card while one is saved. */
    private val _replacing = MutableStateFlow(false)
    val replacing: StateFlow<Boolean> = _replacing.asStateFlow()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Waiting)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    val nfcStatus = MutableStateFlow(NfcStatus.Unknown)

    private val _transfer = MutableStateFlow<TransferState?>(null)
    val transfer: StateFlow<TransferState?> = _transfer.asStateFlow()
    private var transferJob: Job? = null

    /** Collected only after sign-in (the screens that need these exist only then). */
    val presence: StateFlow<MachinePresence?> =
        userRepository.observePresence().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val credit: StateFlow<Long?> = card.flatMapLatest { saved ->
        if (saved == null) flowOf(null)
        else userRepository.observeUser(saved.uid).map { (it as? UserState.Ready)?.user?.credit }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** NFC reading is on only while a card is expected: card reading screen, or a transfer's recipient. */
    val scanning: StateFlow<Boolean> = combine(card, replacing, authState, transfer) { card, replacing, auth, transfer ->
        auth == AuthState.SignedIn && (card == null || replacing || transfer is TransferState.WaitingForCard)
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
        val transfer = _transfer.value
        if (transfer is TransferState.WaitingForCard) {
            onRecipientRead(uid, transfer.amount)
            return
        }
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

    fun startTransfer() {
        if (_transfer.value == null) _transfer.value = TransferState.EnterAmount
    }

    fun confirmTransferAmount(amount: Long) {
        _transfer.value = TransferState.WaitingForCard(amount)
    }

    /** Closes the dialog; a request already sent is not withdrawn, only no longer followed. */
    fun closeTransfer() {
        transferJob?.cancel()
        transferJob = null
        _transfer.value = null
    }

    private fun onRecipientRead(uid: String, amount: Long) {
        val sender = store.card.value ?: return
        if (uid == sender.uid) {
            _transfer.value = TransferState.WaitingForCard(amount, ownCard = true)
            return
        }
        _transfer.value = TransferState.Sending(amount, VendProgress.Sent)
        transferJob = viewModelScope.launch {
            val finished = withTimeoutOrNull(RemotePaths.VEND_TIMEOUT_MS) {
                vendRepository.requestTransfer(sender.uid, uid, amount).collect { next ->
                    // the late local write confirmation must not hide an answer of the machine
                    if (next != VendProgress.Sent) _transfer.value = TransferState.Sending(amount, next)
                }
                true
            }
            if (finished == null) _transfer.value = TransferState.Sending(amount, VendProgress.TimedOut)
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
