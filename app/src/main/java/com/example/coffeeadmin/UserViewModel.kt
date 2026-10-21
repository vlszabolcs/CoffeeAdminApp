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
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.getValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

// /Log/<epoch> = {"e": event, "d": details} - device status log of the brewpass firmware
data class DeviceEvent(
    val timestamp: Long,
    val event: String,
    val detail: String
)

// Offline cache: the listeners only download changes and the last state is shown at once on start.
// It must be enabled before the first use of the database, once per process.
private val firebaseDatabase: FirebaseDatabase by lazy {
    FirebaseDatabase.getInstance().apply { setPersistenceEnabled(true) }
}

class UserViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val DEVICE_EVENT_LIMIT = 50
    }

    private val auth = FirebaseAuth.getInstance()
    private val database = firebaseDatabase.reference
    private val logDao = AppDatabase.getDatabase(application).logDao()
    private val context = application.applicationContext

    // live listeners while logged in: removed on logout, so a new login does not register them twice
    private val activeListeners = mutableListOf<Pair<Query, ValueEventListener>>()

    private val _userList = mutableStateOf<List<User>>(emptyList())
    val userList: State<List<User>> = _userList

    private val _logList = mutableStateOf<List<LogEntry>>(emptyList())
    val logList: State<List<LogEntry>> = _logList

    // /stat/free/<YYYYMMDD> = number of free-mode purchases that day
    private val _freeStats = mutableStateOf<Map<String, Int>>(emptyMap())

    private val _deviceEvents = mutableStateOf<List<DeviceEvent>>(emptyList())
    val deviceEvents: State<List<DeviceEvent>> = _deviceEvents

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
        
        val activeUsers = filtered.filter { it.lastActivity > threshold }
        val inactiveUsers = filtered.filter { it.lastActivity <= threshold }

        val sortedActive = activeUsers.sortedBy { it.creditInt }
        val sortedInactive = inactiveUsers.sortedByDescending { it.lastActivity }

        sortedActive + sortedInactive
    }

    // ÚJ: Felhasználónkénti kávészám (a listához)
    val userCoffeeCounts: State<Map<String, Int>> = derivedStateOf {
        _logList.value
            .filter { it.userId.isNotEmpty() && it.actionInt == CreditLog.ACTION_PURCHASE }
            .groupingBy { it.userId }
            .eachCount()
    }

    // purchases per day (epoch day), computed once per log change for the chart
    private val purchasesByDay: State<Map<Long, Int>> = derivedStateOf {
        _logList.value
            .filter { it.actionInt == CreditLog.ACTION_PURCHASE }
            .groupingBy { epochDay(it.timestamp) }
            .eachCount()
    }

    // ÚJ: Szűrt statisztika a napló nézethez
    val logStats: State<List<UserLogStat>> = derivedStateOf {
        val namesByUid = _userList.value.associate { it.uid to it.name }
        val selectedDay = _selectedDate.value?.let { epochDay(it) }

        val perUser = _logList.value
            .filter { it.userId.isNotEmpty() && (selectedDay == null || epochDay(it.timestamp) == selectedDay) }
            .groupBy { it.userId }
            .map { (uid, logs) ->
                val name = namesByUid[uid] ?: uid
                val coffeeCount = logs.count { it.actionInt == CreditLog.ACTION_PURCHASE }
                val lastAction = logs.maxOfOrNull { it.timestamp } ?: 0L
                UserLogStat(uid, name, coffeeCount, lastAction)
            }

        // free-mode purchases are not tied to a user: one extra row from /stat/free
        val freeCount = if (selectedDay == null) _freeStats.value.values.sum() else freeCountOn(LocalDate.ofEpochDay(selectedDay))
        val freeRow = UserLogStat("", context.getString(R.string.free_coffee), freeCount, 0L)

        (perUser + freeRow)
            .filter { it.coffeeCount > 0 }
            .sortedByDescending { it.coffeeCount }
    }

    // ÚJ: Adatok a grafikonhoz (utolsó 7 nap)
    val chartData: State<List<DailyStat>> = derivedStateOf {
        val perDay = purchasesByDay.value
        lastSevenDays().map { day -> DailyStat(dayLabel(day), (perDay[day.toEpochDay()] ?: 0) + freeCountOn(day)) }
    }

    fun getUserChartData(userId: String?): List<DailyStat> {
        if (userId == null) return emptyList()
        val perDay = _logList.value
            .filter { it.userId == userId && it.actionInt == CreditLog.ACTION_PURCHASE }
            .groupingBy { epochDay(it.timestamp) }
            .eachCount()
        return lastSevenDays().map { day -> DailyStat(dayLabel(day), perDay[day.toEpochDay()] ?: 0) }
    }

    private fun lastSevenDays(): List<LocalDate> {
        val today = LocalDate.now()
        return (6 downTo 0).map { today.minusDays(it.toLong()) }
    }

    private fun dayLabel(day: LocalDate): String =
        if (day == LocalDate.now()) context.getString(R.string.today) else "${day.dayOfMonth}."

    private fun freeCountOn(day: LocalDate): Int =
        _freeStats.value[day.format(DateTimeFormatter.BASIC_ISO_DATE)] ?: 0

    // log timestamps are epoch seconds, the date picker gives milliseconds
    private fun epochDay(timestamp: Long): Long {
        val millis = if (timestamp > 9999999999L) timestamp else timestamp * 1000
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
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
        if (activeListeners.isNotEmpty()) return
        listenToUsers()
        listenToMachineConfig()
        listenToIncome()
        listenToFreeStats()
        listenToDeviceEvents()
    }

    private fun stopListening() {
        activeListeners.forEach { (query, listener) -> query.removeEventListener(listener) }
        activeListeners.clear()
    }

    override fun onCleared() {
        stopListening()
    }

    private fun listen(query: Query, onData: (DataSnapshot) -> Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onData(snapshot)

            override fun onCancelled(error: DatabaseError) {
                _error.value = error.message
                _isLoading.value = false
            }
        }
        query.addValueEventListener(listener)
        activeListeners += query to listener
    }

    // live user list: purchases of the machine and changes of this app show up without reloading
    private fun listenToUsers() {
        _isLoading.value = true
        listen(database.child("users")) { snapshot ->
            val users = mutableListOf<User>()
            val logs = mutableMapOf<String, Map<Long, Long>>()
            val transfers = mutableMapOf<String, Set<Long>>()
            snapshot.children.forEach { child ->
                val uid = child.key ?: return@forEach
                val user = child.getValue<User>()
                if (user != null) {
                    val log = readCreditLog(child.child("log"))
                    logs[uid] = log
                    transfers[uid] = child.child("xfer").children.mapNotNull { it.key?.toLongOrNull() }.toSet()
                    users.add(user.copy(uid = uid, lastLog = log.keys.maxOrNull() ?: 0L))
                }
            }
            _userList.value = users
            _isLoading.value = false
            syncLogCache(logs, transfers)
        }
    }

    private fun listenToMachineConfig() {
        listen(database.child("conf")) { snapshot ->
            val config = snapshot.getValue<MachineConfig>()
            if (config != null) {
                _machineConfig.value = config
                checkCoffeeLevel()
            }
        }
    }

    private fun listenToIncome() {
        listen(database.child("stat").child("income")) { snapshot ->
            _income.value = snapshot.getValue<Long>() ?: 0L
        }
    }

    private fun listenToFreeStats() {
        listen(database.child("stat").child("free")) { snapshot ->
            _freeStats.value = snapshot.children.mapNotNull { child ->
                val day = child.key ?: return@mapNotNull null
                val count = (child.value as? Number)?.toInt() ?: return@mapNotNull null
                day to count
            }.toMap()
        }
    }

    // /Log is the device status log since the brewpass firmware (purchases are under users/<UID>/log)
    private fun listenToDeviceEvents() {
        listen(database.child("Log").orderByKey().limitToLast(DEVICE_EVENT_LIMIT)) { snapshot ->
            _deviceEvents.value = snapshot.children.mapNotNull { child ->
                val timestamp = child.key?.toLongOrNull() ?: return@mapNotNull null
                // older entries in the old purchase format have no "e" field
                val event = child.child("e").value?.toString() ?: return@mapNotNull null
                DeviceEvent(timestamp, event, child.child("d").value?.toString() ?: "")
            }.sortedByDescending { it.timestamp }
        }
    }

    private fun checkCoffeeLevel() {
        val beans = _machineConfig.value.coffeeBeans
        val current = beans.inStock
        val threshold = beans.threshold
        if (current < threshold) {
            _lowCoffeeWarning.value = context.getString(R.string.low_coffee_warning_format, current, threshold)
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
        // the firmware rejects a price <= 0 (E60) and would keep the old one
        if (config.priceLong <= 0L) {
            _error.value = context.getString(R.string.error_machine_update_format, "price <= 0")
            return
        }
        // only the fields of this app: /conf is watched by the machine, other keys stay as they are
        val beans = config.coffeeBeans
        val updates = mapOf<String, Any?>(
            "free" to config.free,
            "price" to config.priceLong,
            "coffeeThreshold" to config.coffeeThreshold,
            "coffeeBeans/inStock" to beans.inStock,
            "coffeeBeans/price" to beans.price,
            "coffeeBeans/threshold" to beans.threshold
        )
        database.child("conf").updateChildren(updates)
            .addOnSuccessListener {
                _successMessage.value = context.getString(R.string.success_updated)
            }
            .addOnFailureListener {
                _error.value = context.getString(R.string.error_machine_update_format, it.message)
            }
    }

    fun updateIncome(value: Long) {
        database.child("stat").child("income").setValue(value)
            .addOnSuccessListener {
                _successMessage.value = context.getString(R.string.success_income_updated)
            }
            .addOnFailureListener {
                _error.value = context.getString(R.string.error_income_update_format, it.message)
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
        stopListening()
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

    private fun readCreditLog(log: DataSnapshot): Map<Long, Long> =
        log.children.mapNotNull { entry ->
            val timestamp = entry.key?.toLongOrNull() ?: return@mapNotNull null
            val credit = (entry.value as? Number)?.toLong() ?: return@mapNotNull null
            timestamp to credit
        }.toMap()

    // users/<UID>/log -> local Room cache (the statistics are computed from the cache).
    // Only the entries newer than the last cached one of the user are classified and written.
    private fun syncLogCache(logs: Map<String, Map<Long, Long>>, transfers: Map<String, Set<Long>>) {
        viewModelScope.launch {
            val latest = logDao.getLatestPerUser().associateBy { it.userId }
            val entries = logs.flatMap { (uid, log) ->
                val last = latest[uid]
                val newLog = if (last == null) log else log.filterKeys { it > last.timestamp }
                if (newLog.isEmpty()) return@flatMap emptyList()
                CreditLog.toEntries(uid, newLog, last?.remainingCredit, transfers[uid].orEmpty())
            }
            if (entries.isNotEmpty()) {
                logDao.insertLogs(entries)
            }
        }
    }

    /**
     * Changes a user in ONE transaction, the same way as the firmware: the credit and its log entry
     * users/<UID>/log/<epoch> = new credit are written together, so the device sees every change.
     * [newCredit] gets the current credit from the server and returns the new one (null = unchanged).
     * [onSuccess] gets the credit difference (for the income).
     */
    private fun runUserTransaction(
        uid: String,
        mustExist: Boolean,
        fields: Map<String, Any?> = emptyMap(),
        newCredit: (Long) -> Long?,
        onSuccess: (Long) -> Unit,
        onFailure: (String?) -> Unit
    ) {
        var delta = 0L
        database.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                delta = 0L
                // null may just be an empty local cache: the server rejects it and calls again with the real data
                if (mustExist && currentData.value == null) {
                    return Transaction.success(currentData)
                }
                fields.forEach { (key, value) -> currentData.child(key).value = value }

                val log = currentData.child("log")
                val current = (currentData.child("credit").value as? Number)?.toLong() ?: 0L
                val target = newCredit(current)
                // a new user always gets a first entry, so later changes can be classified
                if (target != null && (target != current || !log.hasChildren())) {
                    val now = System.currentTimeMillis() / 1000
                    val key = CreditLog.nextKey(now, log.children.map { it.key })
                    currentData.child("credit").value = target
                    log.child(key.toString()).value = target
                    delta = target - current
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                when {
                    error != null -> onFailure(error.message)
                    !committed -> onFailure(null)
                    mustExist && currentData?.exists() != true -> onFailure(context.getString(R.string.error_user_not_found))
                    else -> onSuccess(delta)
                }
            }
        })
    }

    private fun addIncome(delta: Long) {
        if (delta != 0L) {
            database.child("stat").child("income").setValue(ServerValue.increment(delta))
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
        if (amount == 0) return

        // relative to the CURRENT server value: the machine may have charged a purchase meanwhile
        runUserTransaction(
            uid = uid,
            mustExist = true,
            newCredit = { current -> current + amount },
            onSuccess = { delta ->
                addIncome(delta)
                _successMessage.value = context.getString(R.string.success_credit_updated)
            },
            onFailure = {
                _error.value = context.getString(R.string.error_update_format, it ?: "")
            }
        )
    }

    fun updateUser(updatedUser: User, oldUid: String? = null) {
        val uid = updatedUser.uid ?: return
        _isLoading.value = true

        val effectiveOldUid = oldUid ?: uid
        val oldUser = _userList.value.find { it.uid == effectiveOldUid }

        val onSuccess: (Long) -> Unit = { delta ->
            addIncome(delta)
            _isLoading.value = false
            _successMessage.value = context.getString(R.string.success_user_saved)
        }
        val onFailure: (String?) -> Unit = {
            _error.value = context.getString(R.string.error_save_format, it ?: "")
            _isLoading.value = false
        }

        // UID changed: move the whole node (log included) in one multi-path update
        if (oldUid != null && oldUid != uid) {
            moveUser(oldUid, updatedUser, onSuccess, onFailure)
            return
        }

        // The dialog shows the credit as it was when it opened. It is only written if the admin changed
        // it, so a purchase made meanwhile is not overwritten by a name-only edit.
        val creditEdited = oldUser == null || updatedUser.creditLong != oldUser.creditLong
        runUserTransaction(
            uid = uid,
            mustExist = false,
            fields = mapOf("name" to updatedUser.name, "loan" to updatedUser.loanLong),
            newCredit = { current -> if (creditEdited) updatedUser.creditLong else current },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    private fun moveUser(oldUid: String, updatedUser: User, onSuccess: (Long) -> Unit, onFailure: (String?) -> Unit) {
        val uid = updatedUser.uid ?: return
        database.child("users").child(oldUid).get()
            .addOnSuccessListener { snapshot ->
                val log = readCreditLog(snapshot.child("log")).mapKeys { it.key.toString() }.toMutableMap<String, Any?>()
                val current = (snapshot.child("credit").value as? Number)?.toLong() ?: 0L
                val target = updatedUser.creditLong
                if (target != current || log.isEmpty()) {
                    val now = System.currentTimeMillis() / 1000
                    log[CreditLog.nextKey(now, log.keys).toString()] = target
                }

                @Suppress("UNCHECKED_CAST")
                val node = (snapshot.value as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
                node["name"] = updatedUser.name
                node["credit"] = target
                node["loan"] = updatedUser.loanLong
                node["log"] = log
                node.remove("update")

                database.updateChildren(mapOf("users/$oldUid" to null, "users/$uid" to node))
                    .addOnSuccessListener { onSuccess(target - current) }
                    .addOnFailureListener { onFailure(it.message) }
            }
            .addOnFailureListener { onFailure(it.message) }
    }

    // Unbinds the card from the user app's phone (lost phone, reinstalled app), so it can be read in again.
    fun releaseOwner(user: User) {
        val uid = user.uid ?: return
        database.child("users").child(uid).child("owner").removeValue()
            .addOnSuccessListener { _successMessage.value = context.getString(R.string.success_owner_released) }
            .addOnFailureListener { _error.value = context.getString(R.string.error_save_format, it.message ?: "") }
    }

    /**
     * Makes a one-time pairing code for the card (pair/<code> = {card, exp}) and releases its phone in the
     * same update, so the user app that enters the code can bind it. Expired codes are removed meanwhile.
     */
    fun createPairCode(user: User, onCreated: (code: String, expiresAt: Long) -> Unit) {
        val uid = user.uid ?: return
        val onFailure: (String?) -> Unit = { _error.value = context.getString(R.string.error_pair_code_format, it ?: "") }
        database.child(PairCode.PATH).get()
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()
                val existing = snapshot.children.map { it.key }.toSet()
                val code = PairCode.generate(existing)
                val expiresAt = now + PairCode.VALID_MS
                val updates = mutableMapOf<String, Any?>(
                    "${PairCode.PATH}/$code" to mapOf("card" to uid, "exp" to expiresAt),
                    "users/$uid/owner" to null
                )
                snapshot.children.forEach { child ->
                    val exp = (child.child("exp").value as? Number)?.toLong() ?: 0L
                    if (exp < now) updates["${PairCode.PATH}/${child.key}"] = null
                }
                database.updateChildren(updates)
                    .addOnSuccessListener { onCreated(code, expiresAt) }
                    .addOnFailureListener { onFailure(it.message) }
            }
            .addOnFailureListener { onFailure(it.message) }
    }

    fun deleteUser(user: User) {
        val uid = user.uid ?: return
        _isLoading.value = true
        database.child("users").child(uid).removeValue()
            .addOnSuccessListener {
                _isLoading.value = false
                _successMessage.value = context.getString(R.string.success_user_deleted)
            }
            .addOnFailureListener {
                _error.value = context.getString(R.string.error_delete_format, it.message)
                _isLoading.value = false
            }
    }

    // Deletes the device status log (/Log). The purchase history lives in users/<UID>/log and stays.
    fun deleteLogs() {
        database.child("Log").removeValue()
            .addOnSuccessListener {
                _deviceEvents.value = emptyList()
                _successMessage.value = context.getString(R.string.success_logs_deleted)
            }
            .addOnFailureListener {
                _error.value = context.getString(R.string.error_logs_delete_format, it.message)
            }
    }
}
