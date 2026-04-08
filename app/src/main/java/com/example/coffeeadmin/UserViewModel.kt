package com.example.coffeeadmin

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.getValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.util.Calendar
import java.util.Date

data class UserLogStat(
    val userId: String,
    val userName: String,
    val coffeeCount: Int,
    val lastAction: Long
)

data class DailyStat(
    val dateLabel: String,
    val count: Int
)

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val logDao = AppDatabase.getDatabase(application).logDao()

    private val _userList = mutableStateOf<List<User>>(emptyList())
    val userList: State<List<User>> = _userList

    private val _logList = mutableStateOf<List<LogEntry>>(emptyList())
    val logList: State<List<LogEntry>> = _logList

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    // Dátum szűrés állapota (null = összes)
    private val _selectedDate = mutableStateOf<Long?>(null)
    val selectedDate: State<Long?> = _selectedDate

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

        val nowSeconds = System.currentTimeMillis() / 1000
        val thirtyDaysInSeconds = 30L * 24 * 60 * 60
        val threshold = nowSeconds - thirtyDaysInSeconds
        
        val activeUsers = filtered.filter { it.updateLong > threshold }
        val inactiveUsers = filtered.filter { it.updateLong <= threshold }

        val sortedActive = activeUsers.sortedBy { it.creditInt }
        val sortedInactive = inactiveUsers.sortedByDescending { it.updateLong }

        sortedActive + sortedInactive
    }

    // ÚJ: Felhasználónkénti kávészám (a listához)
    val userCoffeeCounts: State<Map<String, Int>> = derivedStateOf {
        _logList.value
            .filter { it.userId != null && it.actionInt == 0 }
            .groupBy { it.userId!! }
            .mapValues { it.value.size }
    }

    // ÚJ: Szűrt statisztika a napló nézethez
    val logStats: State<List<UserLogStat>> = derivedStateOf {
        val users = _userList.value
        val selected = _selectedDate.value
        
        _logList.value
            .filter { it.userId != null }
            .filter { entry ->
                if (selected == null) true
                else isSameDay(entry.timestamp, selected)
            }
            .groupBy { it.userId!! }
            .map { (uid, logs) ->
                val name = users.find { it.uid == uid }?.name ?: uid
                val coffeeCount = logs.count { it.actionInt == 0 }
                val lastAction = logs.maxOfOrNull { it.timestamp } ?: 0L
                UserLogStat(uid, name, coffeeCount, lastAction)
            }
            .filter { it.coffeeCount > 0 }
            .sortedByDescending { it.coffeeCount }
    }

    // ÚJ: Adatok a grafikonhoz (utolsó 7 nap)
    val chartData: State<List<DailyStat>> = derivedStateOf {
        val cal = Calendar.getInstance()
        val results = mutableListOf<DailyStat>()
        
        for (i in 0..6) {
            val date = cal.timeInMillis
            val count = _logList.value.count { it.actionInt == 0 && isSameDay(it.timestamp, date) }
            val label = if (i == 0) "Ma" else "${cal.get(Calendar.DAY_OF_MONTH)}."
            results.add(0, DailyStat(label, count))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        results
    }

    private fun isSameDay(ts1: Long, ts2: Long): Boolean {
        val d1 = Calendar.getInstance().apply { timeInMillis = if (ts1 > 9999999999L) ts1 else ts1 * 1000 }
        val d2 = Calendar.getInstance().apply { timeInMillis = ts2 }
        return d1.get(Calendar.YEAR) == d2.get(Calendar.YEAR) &&
               d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR)
    }

    fun setSelectedDate(timestamp: Long?) {
        _selectedDate.value = timestamp
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

    val coffeeCount: State<Double> = derivedStateOf { _machineConfig.value.coffeeBeans.inStock }

    private val _lowCoffeeWarning = mutableStateOf<String?>(null)
    val lowCoffeeWarning: State<String?> = _lowCoffeeWarning

    init {
        if (auth.currentUser != null) {
            startListening()
        }
        
        viewModelScope.launch {
            logDao.getAllLogs().collectLatest { logs ->
                _logList.value = logs
            }
        }
    }

    private fun startListening() {
        fetchAllUsers()
        listenToMachineConfig()
        listenToIncome()
        listenToLogs()
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

    private fun listenToLogs() {
        viewModelScope.launch {
            val lastTimestamp = logDao.getMaxTimestamp() ?: 0L
            
            database.child("Log")
                .orderByKey()
                .startAfter(lastTimestamp.toString())
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val newLogs = mutableListOf<LogEntry>()
                        snapshot.children.forEach { child ->
                            val timestamp = child.key?.toLongOrNull() ?: 0L
                            val map = child.value as? Map<*, *>
                            if (map != null) {
                                val action = map["action"]?.toString() ?: "0"
                                val remainingCredit = (map["remainingCredit"] as? Number)?.toLong() ?: 0L
                                val userId = map["userId"]?.toString()
                                
                                newLogs.add(LogEntry(
                                    timestamp = timestamp,
                                    actionValue = action,
                                    remainingCredit = remainingCredit,
                                    userId = userId
                                ))
                            }
                        }
                        
                        if (newLogs.isNotEmpty()) {
                            viewModelScope.launch {
                                logDao.insertLogs(newLogs)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        _error.value = error.message
                    }
                })
        }
    }

    private fun checkCoffeeLevel() {
        val beans = _machineConfig.value.coffeeBeans
        val current = beans.inStock
        val threshold = beans.threshold
        if (current < threshold) {
            _lowCoffeeWarning.value = "Alacsony kávészint! Aktuális: $current kg, Küszöb: $threshold kg"
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
                startListening()
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
        // _logList.value = emptyList() // Keep logs
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

    fun deleteLogs() {
        database.child("Log").removeValue()
            .addOnSuccessListener {
                viewModelScope.launch {
                    logDao.deleteAll()
                }
                _successMessage.value = "Naplók törölve!"
            }
            .addOnFailureListener {
                _error.value = "Sikertelen napló törlés: ${it.message}"
            }
    }
}
