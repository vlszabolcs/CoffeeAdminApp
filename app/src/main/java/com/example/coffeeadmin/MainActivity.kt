package com.example.coffeeadmin

import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.coffee.userview.ui.UserViewDialog
import com.example.coffeeadmin.ui.theme.CoffeeAdminTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private lateinit var viewModel: UserViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[UserViewModel::class.java]
        
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        setContent {
            CoffeeAdminTheme {
                val isLoggedIn by viewModel.isLoggedIn

                if (isLoggedIn) {
                    MainScreen(viewModel)
                } else {
                    LoginScreen(onLogin = { email, pass -> viewModel.login(email, pass) })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
        ) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            }
            tag?.id?.let { id ->
                val uid = id.joinToString("") { "%02X".format(it) }
                if (::viewModel.isInitialized) {
                    viewModel.searchUserByUid(uid)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: UserViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.nav_users),
        stringResource(R.string.nav_machine),
        stringResource(R.string.nav_logs)
    )
    val scannedUid by viewModel.scannedUid
    val scannedUidForRegistration by viewModel.scannedUidForRegistration
    val lowCoffeeWarning by viewModel.lowCoffeeWarning
    var nfcEditUser by remember { mutableStateOf<User?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.logout))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(title) },
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.Person
                                    1 -> Icons.Default.Settings
                                    else -> Icons.AutoMirrored.Filled.List
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> UserListScreen(viewModel)
                1 -> MachineScreen(viewModel)
                2 -> LogScreen(viewModel)
            }

            // Low Coffee Popup
            lowCoffeeWarning?.let { message ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearLowCoffeeWarning() },
                    title = { Text(stringResource(R.string.warning)) },
                    text = { Text(message) },
                    confirmButton = {
                        Button(onClick = { viewModel.clearLowCoffeeWarning() }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }
        }
    }

    // Handle scanned UID for existing user - Show Quick Credit View
    scannedUid?.let { uid ->
        val user = viewModel.userList.value.find { it.uid == uid }
        if (user != null) {
            QuickCreditDialog(
                user = user,
                viewModel = viewModel,
                onDismiss = { viewModel.clearScannedUid() },
                onConfirm = { amount ->
                    viewModel.updateCredit(user, amount)
                    viewModel.clearScannedUid()
                },
                onEdit = {
                    nfcEditUser = user
                    viewModel.clearScannedUid()
                }
            )
        }
    }

    // Full edit dialog triggered from NFC Fast View
    nfcEditUser?.let { user ->
        UserDetailDialog(
            user = user,
            onDismiss = { nfcEditUser = null },
            onSave = { updatedUser ->
                viewModel.updateUser(updatedUser, oldUid = user.uid)
                nfcEditUser = null
            },
            onDelete = {
                viewModel.deleteUser(user)
                nfcEditUser = null
            }
        )
    }

    // Handle scanned UID for new user - Offer registration
    scannedUidForRegistration?.let { uid ->
        UserDetailDialog(
            user = User(uid = uid),
            isNewUser = true,
            onDismiss = { viewModel.clearRegistrationUid() },
            onSave = { newUser ->
                viewModel.updateUser(newUser)
                viewModel.clearRegistrationUid()
            }
        )
    }
}

@Composable
fun LoginScreen(onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { onLogin(email, password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.login_button))
        }
    }
}

@Composable
fun UserListScreen(viewModel: UserViewModel) {
    val users by viewModel.filteredUserList
    val searchQuery by viewModel.searchQuery
    val userCoffeeCounts by viewModel.userCoffeeCounts
    var showEditDialog by remember { mutableStateOf<User?>(null) }
    var quickCreditUser by remember { mutableStateOf<User?>(null) }
    var showAddUserDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = { showAddUserDialog = true },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.register_user_title))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(users, key = { it.uid ?: "" }) { user ->
                UserRow(
                    user = user,
                    coffeeCount = userCoffeeCounts[user.uid] ?: 0,
                    onClick = { quickCreditUser = user }
                )
            }
        }
    }

    quickCreditUser?.let { user ->
        QuickCreditDialog(
            user = user,
            viewModel = viewModel,
            onDismiss = { quickCreditUser = null },
            onConfirm = { amount ->
                viewModel.updateCredit(user, amount)
                quickCreditUser = null
            },
            onEdit = {
                showEditDialog = user
                quickCreditUser = null
            }
        )
    }

    showEditDialog?.let { user ->
        UserDetailDialog(
            user = user,
            onDismiss = { showEditDialog = null },
            onSave = { updatedUser ->
                viewModel.updateUser(updatedUser, oldUid = user.uid)
                showEditDialog = null
            },
            onDelete = {
                viewModel.deleteUser(user)
                showEditDialog = null
            }
        )
    }

    if (showAddUserDialog) {
        UserDetailDialog(
            user = User(),
            isNewUser = true,
            onDismiss = { showAddUserDialog = false },
            onSave = { newUser ->
                viewModel.updateUser(newUser)
                showAddUserDialog = false
            }
        )
    }
}

@Composable
fun UserRow(user: User, coffeeCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = user.name ?: stringResource(R.string.unknown), style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(R.string.balance_format, user.creditInt), style = MaterialTheme.typography.bodyMedium)
                if (user.loanInt > 0) {
                    Text(text = stringResource(R.string.loan_format, user.loanInt), color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (coffeeCount > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(text = stringResource(R.string.total_coffee_format, coffeeCount), modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
fun UserDetailDialog(
    user: User,
    isNewUser: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (User) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var uid by remember { mutableStateOf(user.uid ?: "") }
    var name by remember { mutableStateOf(user.name ?: "") }
    var credit by remember { mutableStateOf(user.creditInt.toString()) }
    var loan by remember { mutableStateOf(user.loanInt.toString()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNewUser) stringResource(R.string.register_user_title) else stringResource(R.string.edit_user_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = uid, 
                    onValueChange = { uid = it }, 
                    label = { Text(stringResource(R.string.card_uid)) }, 
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true
                )
                TextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth())
                TextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text(stringResource(R.string.credit_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = loan,
                    onValueChange = { loan = it },
                    label = { Text(stringResource(R.string.loan_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(user.copy(
                    uid = uid,
                    name = name,
                    credit = credit.toLongOrNull() ?: 0L,
                    loan = loan.toLongOrNull() ?: 0L
                ))
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (!isNewUser && onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_user_message)) },
            confirmButton = {
                Button(onClick = { 
                    onDelete?.invoke()
                    showDeleteConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun MachineScreen(viewModel: UserViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.machineConfig
    val income by viewModel.income
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val successMessage by viewModel.successMessage
    val context = LocalContext.current

    var showThresholdDialog by remember { mutableStateOf(false) }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.free_coffee))
                        Switch(
                            checked = config.free,
                            onCheckedChange = { viewModel.updateMachineConfig(config.copy(free = it)) }
                        )
                    }

                    NumberSettingItem(
                        name = stringResource(R.string.portion_price),
                        value = config.priceInt.toDouble(),
                        unit = stringResource(R.string.currency_unit),
                        onSave = { newValue ->
                            viewModel.updateMachineConfig(config.copy(price = newValue.toLong()))
                        }
                    )

                    NumberSettingItem(
                        name = stringResource(R.string.total_income),
                        value = income.toDouble(),
                        unit = stringResource(R.string.currency_unit),
                        onSave = { newValue ->
                            viewModel.updateIncome(newValue.toLong())
                        }
                    )

                    HorizontalDivider()
                    Text(text = stringResource(R.string.coffee_bean_stock), style = MaterialTheme.typography.titleMedium)

                    NumberSettingItem(
                        name = stringResource(R.string.in_stock),
                        value = config.coffeeBeans.inStock,
                        unit = stringResource(R.string.kg_unit),
                        onSave = { newValue ->
                            viewModel.updateMachineConfig(
                                config.copy(coffeeBeans = config.coffeeBeans.copy(inStock = newValue))
                            )
                        },
                        secondaryAction = {
                            IconButton(onClick = { showThresholdDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.edit_threshold))
                            }
                        }
                    )

                    NumberSettingItem(
                        name = stringResource(R.string.kg_unit_price),
                        value = config.coffeeBeans.price,
                        unit = stringResource(R.string.currency_unit),
                        onSave = { newValue ->
                            viewModel.updateMachineConfig(
                                config.copy(coffeeBeans = config.coffeeBeans.copy(price = newValue))
                            )
                        }
                    )

                    error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showThresholdDialog) {
        var thresholdValue by remember { mutableStateOf(config.coffeeBeans.threshold.toString()) }
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = { Text(stringResource(R.string.set_coffee_threshold_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.set_threshold_message))
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = thresholdValue,
                        onValueChange = { thresholdValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    thresholdValue.toDoubleOrNull()?.let {
                        viewModel.updateMachineConfig(
                            config.copy(coffeeBeans = config.coffeeBeans.copy(threshold = it))
                        )
                        showThresholdDialog = false
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showThresholdDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun LogScreen(viewModel: UserViewModel, modifier: Modifier = Modifier) {
    val chartData by viewModel.chartData
    val selectedDate by viewModel.selectedDate
    val logStats by viewModel.logStats
    val deviceEvents by viewModel.deviceEvents
    val isLoading by viewModel.isLoading
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd.", Locale.getDefault()) }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            // Grafikon Szekció
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.consumption_last_7_days), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    CoffeeChart(data = chartData)
                }
            }

            // Szűrés és Fejléc
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = stringResource(R.string.date_summary), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (selectedDate == null) stringResource(R.string.all_time) else dateFormatter.format(Date(selectedDate!!)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val cal = Calendar.getInstance()
                            selectedDate?.let { cal.timeInMillis = it }
                            DatePickerDialog(context, { _, y, m, d ->
                                val newCal = Calendar.getInstance()
                                newCal.set(y, m, d)
                                viewModel.setSelectedDate(newCal.timeInMillis)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }
                    )
                }
                
                Row {
                    if (selectedDate != null) {
                        IconButton(onClick = { viewModel.setSelectedDate(null) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_filter))
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_logs), tint = Color.Red)
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (logStats.isEmpty()) {
            item {
                Box(modifier = Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.select_date_for_stats))
                }
            }
        } else {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            items(logStats, key = { it.userId }) { stat ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    LogStatRow(stat)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        item {
            DeviceEventsCard(events = deviceEvents)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_logs)) },
            text = { Text(stringResource(R.string.confirm_delete_logs_message)) },
            confirmButton = {
                Button(onClick = { 
                    viewModel.deleteLogs()
                    showDeleteConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun DeviceEventsCard(events: List<DeviceEvent>) {
    val timeFormatter = remember { SimpleDateFormat("yyyy.MM.dd. HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.device_events), style = MaterialTheme.typography.titleMedium)
            if (events.isEmpty()) {
                Text(text = stringResource(R.string.no_device_events), style = MaterialTheme.typography.bodySmall)
            }
            events.forEach { event ->
                Column {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = event.event,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (event.event == "error") MaterialTheme.colorScheme.error else Color.Unspecified
                        )
                        Text(
                            text = timeFormatter.format(Date(event.timestamp * 1000)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (event.detail.isNotEmpty()) {
                        Text(text = event.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun LogStatRow(stat: UserLogStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stat.userName, 
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge, 
                fontWeight = FontWeight.Bold
            )
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(text = "${stat.coffeeCount} db", modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
fun QuickCreditDialog(
    user: User, 
    viewModel: UserViewModel,
    onDismiss: () -> Unit, 
    onConfirm: (Int) -> Unit, 
    onEdit: (() -> Unit)? = null
) {
    var amountText by remember { mutableStateOf("0") }
    var showStats by remember { mutableStateOf(false) }
    var showUserView by remember { mutableStateOf(false) }
    val logList by viewModel.logList

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${user.name}")
                Row {
                    if (user.uid != null) {
                        IconButton(onClick = { showUserView = true }) {
                            Icon(Icons.Default.Person, contentDescription = stringResource(com.example.coffee.userview.R.string.uv_open))
                        }
                    }
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                    }
                }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.current_balance_format, user.creditInt))
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { 
                            val current = amountText.toIntOrNull() ?: 0
                            amountText = (current - 500).toString()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.width(80.dp)
                    ) {
                        Text("-500")
                    }

                    TextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(100.dp).padding(horizontal = 8.dp),
                        textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp),
                        singleLine = true
                    )

                    Button(
                        onClick = { 
                            val current = amountText.toIntOrNull() ?: 0
                            amountText = (current + 500).toString()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.width(80.dp)
                    ) {
                        Text("+500")
                    }
                }

                if (showStats) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.past_7_days_consumption), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    val userChartData = remember(user.uid, logList) { viewModel.getUserChartData(user.uid) }
                    CoffeeChart(data = userChartData)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                amountText.toIntOrNull()?.let { onConfirm(it) }
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showStats = !showStats }) {
                    Icon(
                        imageVector = if (showStats) Icons.AutoMirrored.Filled.ShowChart else Icons.Default.BarChart,
                        contentDescription = stringResource(R.string.stats),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )

    if (showUserView) {
        user.uid?.let { uid -> UserViewDialog(cardUid = uid, onDismiss = { showUserView = false }) }
    }
}

@Composable
fun CoffeeChart(data: List<DailyStat>) {
    val maxCount = (data.maxOfOrNull { it.count } ?: 5).coerceAtLeast(1)
    val todayLabel = stringResource(R.string.today)
    
    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { stat ->
            val barHeightFactor = stat.count.toFloat() / maxCount
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(barHeightFactor)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (stat.dateLabel == todayLabel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    )
                }
                Text(text = stat.count.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = stat.dateLabel, fontSize = 8.sp)
            }
        }
    }
}

@Composable
fun NumberSettingItem(
    name: String, 
    value: Double, 
    unit: String, 
    onSave: (Double) -> Unit,
    secondaryAction: @Composable (() -> Unit)? = null
) {
    var textValue by remember { mutableStateOf(if (value % 1 == 0.0) value.toLong().toString() else value.toString()) }
    var isEditing by remember { mutableStateOf(false) }

    // Update textValue if value changes externally and we are not editing
    LaunchedEffect(value) {
        if (!isEditing) {
            textValue = if (value % 1 == 0.0) value.toLong().toString() else value.toString()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, modifier = Modifier.weight(1f))
        
        if (isEditing) {
            TextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.width(100.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            IconButton(onClick = {
                textValue.toDoubleOrNull()?.let {
                    onSave(it)
                    isEditing = false
                }
            }) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${if (value % 1 == 0.0) value.toLong() else value} $unit")
                secondaryAction?.invoke()
                IconButton(onClick = { isEditing = true }) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                }
            }
        }
    }
}
