package com.example.coffeeadmin

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.getValue

class UserViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    private val _userList = mutableStateOf<List<User>>(emptyList())
    val userList: State<List<User>> = _userList

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    val filteredUserList: State<List<User>> = derivedStateOf {
        val query = _searchQuery.value.lowercase()
        val allUsers = _userList.value
        
        val filtered = if (query.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { 
                (it.name?.lowercase()?.contains(query) == true) || 
                (it.uid?.lowercase()?.contains(query) == true)
            }
        }

        // Use seconds for consistency with Firebase example (10 digits)
        val nowSeconds = System.currentTimeMillis() / 1000
        val thirtyDaysInSeconds = 30L * 24 * 60 * 60
        val threshold = nowSeconds - thirtyDaysInSeconds
        
        // Active users: updated within the last 30 days
        val activeUsers = filtered.filter { it.updateLong > threshold }
        val inactiveUsers = filtered.filter { it.updateLong <= threshold }

        // Active users sorted by credit ascending
        val sortedActive = activeUsers.sortedBy { it.creditInt }
        
        // Inactive users at the end (sorted by update descending so most recent is first)
        val sortedInactive = inactiveUsers.sortedByDescending { it.updateLong }

        sortedActive + sortedInactive
    }

    private val _scannedUid = mutableStateOf<String?>(null)
    val scannedUid: State<String?> = _scannedUid

    private val _scannedUidForRegistration = mutableStateOf<String?>(null)
    val scannedUidForRegistration: State<String?> = _scannedUidForRegistration

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _successMessage = mutableStateOf<String?>(null)
    val successMessage: State<String?> = _successMessage

    private val _isLoggedIn = mutableStateOf(auth.currentUser != null)
    val isLoggedIn: State<Boolean> = _isLoggedIn

    private val _machineConfig = mutableStateOf(MachineConfig())
    val machineConfig: State<MachineConfig> = _machineConfig

    private val _income = mutableStateOf(0L)
    val income: State<Long> = _income

    private val _coffeeCount = mutableStateOf(0L)
    val coffeeCount: State<Long> = _coffeeCount

    private val _lowCoffeeWarning = mutableStateOf<String?>(null)
    val lowCoffeeWarning: State<String?> = _lowCoffeeWarning

    init {
        if (auth.currentUser != null) {
            fetchAllUsers()
            listenToMachineConfig()
            listenToIncome()
            listenToCoffeeCount()
        }
    }

    private fun listenToMachineConfig() {
        database.child("conf").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val config = snapshot.getValue<MachineConfig>()
                if (config != null) {
                    _machineConfig.value = config
                    checkCoffeeLevel()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = error.message
            }
        })
    }

    private fun listenToIncome() {
        database.child("stat").child("income").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _income.value = snapshot.getValue<Long>() ?: 0L
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = error.message
            }
        })
    }

    private fun listenToCoffeeCount() {
        database.child("stat").child("coffee").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _coffeeCount.value = snapshot.getValue<Long>() ?: 0L
                checkCoffeeLevel()
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = error.message
            }
        })
    }

    private fun checkCoffeeLevel() {
        val threshold = _machineConfig.value.coffeeThreshold ?: 0L
        val current = _coffeeCount.value
        if (current < threshold) {
            _lowCoffeeWarning.value = "Alacsony kávészint! Aktuális: $current, Küszöb: $threshold"
        } else {
            _lowCoffeeWarning.value = null
        }
    }

    fun clearLowCoffeeWarning() {
        _lowCoffeeWarning.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    fun updateMachineConfig(config: MachineConfig) {
        database.child("conf").setValue(config)
            .addOnSuccessListener {
                _successMessage.value = "Sikeresen frissítve!"
            }
            .addOnFailureListener {
                _error.value = "Sikertelen gép beállítás frissítés: ${it.message}"
            }
    }

    fun updateIncome(value: Long) {
        database.child("stat").child("income").setValue(value)
            .addOnSuccessListener {
                _successMessage.value = "Bevétel frissítve!"
            }
            .addOnFailureListener {
                _error.value = "Sikertelen bevétel frissítés: ${it.message}"
            }
    }

    fun updateCoffeeCount(value: Long) {
        database.child("stat").child("coffee").setValue(value)
            .addOnSuccessListener {
                _successMessage.value = "Kávé mennyiség frissítve!"
            }
            .addOnFailureListener {
                _error.value = "Sikertelen frissítés: ${it.message}"
            }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun login(email: String, pass: String) {
        _isLoading.value = true
        _error.value = null
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                _isLoggedIn.value = true
                _isLoading.value = false
                fetchAllUsers()
                listenToMachineConfig()
                listenToIncome()
                listenToCoffeeCount()
            }
            .addOnFailureListener {
                _error.value = it.message
                _isLoading.value = false
            }
    }

    fun logout() {
        auth.signOut()
        _isLoggedIn.value = false
        _userList.value = emptyList()
        _scannedUid.value = null
        _scannedUidForRegistration.value = null
        _searchQuery.value = ""
    }

    fun clearScannedUid() {
        _scannedUid.value = null
    }

    fun clearRegistrationUid() {
        _scannedUidForRegistration.value = null
    }

    fun fetchAllUsers() {
        _isLoading.value = true
        _error.value = null
        
        database.child("users").get()
            .addOnSuccessListener { snapshot ->
                val users = mutableListOf<User>()
                snapshot.children.forEach { child ->
                    val user = child.getValue<User>()
                    if (user != null) {
                        users.add(user.copy(uid = child.key))
                    }
                }
                _userList.value = users
                _isLoading.value = false
            }
            .addOnFailureListener { exception ->
                _error.value = exception.message
                _isLoading.value = false
            }
    }

    fun searchUserByUid(uid: String) {
        if (uid.isBlank()) return
        
        val user = _userList.value.find { it.uid == uid }
        if (user != null) {
            _scannedUid.value = uid
            _scannedUidForRegistration.value = null
        } else {
            _isLoading.value = true
            database.child("users").child(uid).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        _scannedUid.value = uid
                        _scannedUidForRegistration.value = null
                        fetchAllUsers() 
                    } else {
                        _scannedUidForRegistration.value = uid
                        _scannedUid.value = null
                    }
                    _isLoading.value = false
                }
                .addOnFailureListener {
                    _error.value = it.message
                    _isLoading.value = false
                }
        }
    }

    fun updateCredit(user: User, amount: Int) {
        val uid = user.uid ?: return
        val newCredit = user.creditInt + amount
        val now = System.currentTimeMillis() / 1000 // Seconds
        
        val updates = mapOf(
            "users/$uid/credit" to newCredit,
            "users/$uid/update" to now,
            "stat/income" to ServerValue.increment(amount.toLong())
        )
        
        database.updateChildren(updates)
            .addOnSuccessListener {
                fetchAllUsers() 
                _successMessage.value = "Kredit frissítve!"
            }
            .addOnFailureListener {
                _error.value = "Sikertelen frissítés: ${it.message}"
            }
    }

    fun updateUser(updatedUser: User) {
        val uid = updatedUser.uid ?: return
        _isLoading.value = true
        val now = System.currentTimeMillis() / 1000 // Seconds
        
        val oldUser = _userList.value.find { it.uid == uid }
        val creditDiff = if (oldUser != null) {
            updatedUser.creditLong - oldUser.creditLong
        } else {
            updatedUser.creditLong
        }

        val updates = mutableMapOf<String, Any?>(
            "users/$uid/name" to updatedUser.name,
            "users/$uid/credit" to updatedUser.creditLong,
            "users/$uid/loan" to updatedUser.loanLong,
            "users/$uid/update" to now
        )
        
        if (creditDiff != 0L) {
            updates["stat/income"] = ServerValue.increment(creditDiff)
        }

        database.updateChildren(updates)
            .addOnSuccessListener {
                _isLoading.value = false
                fetchAllUsers()
                _successMessage.value = "Felhasználó mentve!"
            }
            .addOnFailureListener {
                _error.value = "Sikertelen mentés: ${it.message}"
                _isLoading.value = false
            }
    }

    fun deleteUser(user: User) {
        val uid = user.uid ?: return
        _isLoading.value = true
        database.child("users").child(uid).removeValue()
            .addOnSuccessListener {
                _isLoading.value = false
                fetchAllUsers()
                _successMessage.value = "Felhasználó törölve!"
            }
            .addOnFailureListener {
                _error.value = "Sikertelen törlés: ${it.message}"
                _isLoading.value = false
            }
    }
}
