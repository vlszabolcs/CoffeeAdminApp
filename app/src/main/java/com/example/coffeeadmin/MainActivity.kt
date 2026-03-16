package com.example.coffeeadmin

import android.app.PendingIntent
import android.content.*
import android.net.*
import android.nfc.*
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.coffeeadmin.ui.theme.CoffeeAdminTheme
import kotlinx.coroutines.launch

enum class AdminScreen {
    LIST, MACHINE
}

class MainActivity : ComponentActivity() {
    private val viewModel: UserViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    private var currentScreenState = mutableStateOf(AdminScreen.LIST)
    private var isNetworkAvailable = mutableStateOf(true)
    private var isNfcEnabled = mutableStateOf(true)

    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isNetworkAvailable.value = true
        }

        override fun onLost(network: Network) {
            isNetworkAvailable.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Initial check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isNetworkAvailable.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "Az eszköz nem támogatja az NFC-t", Toast.LENGTH_LONG).show()
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        enableEdgeToEdge()
        setContent {
            CoffeeAdminTheme {
                val isLoggedIn by viewModel.isLoggedIn
                var currentScreen by currentScreenState
                val hasInternet by isNetworkAvailable
                val nfcOn by isNfcEnabled

                Box(modifier = Modifier.fillMaxSize()) {
                    // Main App Content
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!isLoggedIn) {
                            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                LoginScreen(
                                    viewModel = viewModel,
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        } else {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                bottomBar = {
                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = currentScreen == AdminScreen.LIST,
                                            onClick = { currentScreen = AdminScreen.LIST },
                                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Lista") },
                                            label = { Text("Lista") }
                                        )
                                        NavigationBarItem(
                                            selected = currentScreen == AdminScreen.MACHINE,
                                            onClick = { currentScreen = AdminScreen.MACHINE },
                                            icon = { Icon(Icons.Default.Settings, contentDescription = "Gép") },
                                            label = { Text("Gép") }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                when (currentScreen) {
                                    AdminScreen.LIST -> UserListScreen(
                                        viewModel = viewModel,
                                        nfcEnabled = nfcOn,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                    AdminScreen.MACHINE -> MachineScreen(
                                        viewModel = viewModel,
                                        modifier = Modifier.padding(innerPadding)
                                    )
                                }
                            }
                        }
                    }

                    // Centered Warnings Overlay
                    ConnectivityWarning(hasInternet = hasInternet)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let {
            isNfcEnabled.value = it.isEnabled
            it.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            @Suppress("DEPRECATION")
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val uid = bytesToHexString(it.id)
                handleScannedUid(uid)
            }
        }
    }

    private fun handleScannedUid(uid: String) {
        if (viewModel.isLoggedIn.value) {
            currentScreenState.value = AdminScreen.LIST
            viewModel.searchUserByUid(uid)
            Toast.makeText(this, "Kártya beolvasva: $uid", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Beolvasáshoz előbb jelentkezz be!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bytesToHexString(src: ByteArray): String {
        val stringBuilder = StringBuilder("")
        if (src.isEmpty()) return ""
        for (i in src.indices) {
            val v = src[i].toInt() and 0xFF
            val hv = Integer.toHexString(v).uppercase()
            if (hv.length < 2) {
                stringBuilder.append(0)
            }
            stringBuilder.append(hv)
        }
        return stringBuilder.toString()
    }
}

@Composable
fun ConnectivityWarning(hasInternet: Boolean) {
    if (!hasInternet) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .zIndex(10f)
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Red),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Nincs internetkapcsolat!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NfcWarning(nfcEnabled: Boolean) {
    val context = LocalContext.current
    AnimatedVisibility(visible = !nfcEnabled) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA500)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.clickable {
                try {
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                } catch (e: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (e2: Exception) {
                        Toast.makeText(context, "Nem sikerült megnyitni a beállításokat", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Az NFC nincs bekapcsolva!\nKattints ide a bekapcsoláshoz.",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: UserViewModel, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Coffee Admin Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Jelszó") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboardController?.hide()
                viewModel.login(email, password)
            })
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.login(email, password)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Bejelentkezés")
            }
        }
        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun UserListScreen(viewModel: UserViewModel, nfcEnabled: Boolean, modifier: Modifier = Modifier) {
    val userList by viewModel.filteredUserList
    val isLoading by viewModel.isLoading
    val searchQuery by viewModel.searchQuery
    val scannedUid by viewModel.scannedUid
    val registrationUid by viewModel.scannedUidForRegistration
    val successMessage by viewModel.successMessage

    var expandedUserId by remember { mutableStateOf<String?>(null) }
    var userToEdit by remember { mutableStateOf<User?>(null) }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(scannedUid) {
        scannedUid?.let { uid ->
            val index = userList.indexOfFirst { it.uid == uid }
            if (index != -1) {
                expandedUserId = uid
                scope.launch {
                    listState.animateScrollToItem(index)
                }
            }
            viewModel.clearScannedUid()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        NfcWarning(nfcEnabled = nfcEnabled)
        
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Felhasználók", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.logout() }) {
                    Text("Kilépés")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Keresés név vagy UID alapján") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Törlés")
                        }
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.fetchAllUsers() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lista Frissítése")
            }
        }

        if (isLoading && userList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
            ) {
                Text(text = "Név", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                Text(text = "Kredit", modifier = Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(userList, key = { it.uid ?: it.hashCode() }) { user ->
                    val isExpanded = expandedUserId == user.uid
                    Column {
                        UserRow(
                            user = user, 
                            onClick = { 
                                expandedUserId = if (isExpanded) null else user.uid
                            }
                        )
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                UserDetailCard(
                                    user = user,
                                    onEditClick = {
                                        userToEdit = user
                                    },
                                    onUpdateCredit = { amount ->
                                        viewModel.updateCredit(user, amount)
                                    }
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    userToEdit?.let { user ->
        UserDetailDialog(
            user = user,
            onDismiss = { userToEdit = null },
            onSave = { updatedUser ->
                viewModel.updateUser(updatedUser)
                userToEdit = null
            },
            onDelete = {
                viewModel.deleteUser(user)
                userToEdit = null
            }
        )
    }

    if (registrationUid != null) {
        UserDetailDialog(
            user = User(uid = registrationUid, name = "", credit = 0L, loan = 0L),
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
fun UserRow(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = user.name ?: "Névtelen", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyLarge)
        Text(text = "${user.creditInt} Ft", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UserDetailCard(
    user: User,
    onEditClick: () -> Unit,
    onUpdateCredit: (Int) -> Unit
) {
    var creditAdjustText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.name ?: "Névtelen",
                    style = MaterialTheme.typography.titleSmall, 
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Szerkesztés")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            val onUpdate: () -> Unit = {
                val value = creditAdjustText.toIntOrNull()
                if (value != null) {
                    onUpdateCredit(value)
                    creditAdjustText = ""
                    keyboardController?.hide()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val current = creditAdjustText.toIntOrNull() ?: 0
                        creditAdjustText = (current - 500).toString()
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("-500")
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = creditAdjustText,
                    onValueChange = { if (it.length <= 10) creditAdjustText = it },
                    label = { Text("Összeg") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onUpdate() }),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = {
                        val current = creditAdjustText.toIntOrNull() ?: 0
                        creditAdjustText = (current + 500).toString()
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("+500")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onUpdate,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("OK")
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
    var name by remember { mutableStateOf(user.name ?: "") }
    var credit by remember { mutableStateOf(user.creditInt.toString()) }
    var loan by remember { mutableStateOf(user.loanInt.toString()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNewUser) "Új felhasználó regisztrálása" else "Felhasználó szerkesztése") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNewUser) {
                    Text("Kártya UID: ${user.uid}", fontWeight = FontWeight.Bold)
                }
                TextField(value = name, onValueChange = { name = it }, label = { Text("Név") })
                TextField(
                    value = credit,
                    onValueChange = { credit = it },
                    label = { Text("Kredit (Ft)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TextField(
                    value = loan,
                    onValueChange = { loan = it },
                    label = { Text("Hitel (Ft)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(user.copy(
                    name = name,
                    credit = credit.toLongOrNull() ?: 0L,
                    loan = loan.toLongOrNull() ?: 0L
                ))
            }) {
                Text("Mentés")
            }
        },
        dismissButton = {
            Row {
                if (!isNewUser && onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                        Text("Törlés")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Mégse")
                }
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Törlés megerősítése") },
            text = { Text("Biztosan törölni szeretnéd ezt a felhasználót?") },
            confirmButton = {
                Button(onClick = { 
                    onDelete?.invoke()
                    showDeleteConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Törlés")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Mégse")
                }
            }
        )
    }
}

@Composable
fun MachineScreen(viewModel: UserViewModel, modifier: Modifier = Modifier) {
    val config by viewModel.machineConfig
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val successMessage by viewModel.successMessage
    val context = LocalContext.current

    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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
                    CircularProgressIndicator()
                } else {
                    Text(text = "Beállítások", style = MaterialTheme.typography.titleLarge)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ingyen kávé")
                        Switch(
                            checked = config.free,
                            onCheckedChange = { viewModel.updateMachineConfig(config.copy(free = it)) }
                        )
                    }

                    PriceSettingItem(
                        name = "Ár",
                        price = config.priceInt,
                        onSave = { newPrice ->
                            viewModel.updateMachineConfig(config.copy(price = newPrice))
                        }
                    )

                    error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun PriceSettingItem(name: String, price: Int, onSave: (Long) -> Unit) {
    var textValue by remember { mutableStateOf(price.toString()) }
    var isEditing by remember { mutableStateOf(false) }

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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            IconButton(onClick = {
                textValue.toLongOrNull()?.let {
                    onSave(it)
                    isEditing = false
                }
            }) {
                Icon(Icons.Default.Check, contentDescription = "Mentés")
            }
        } else {
            Text(text = "$price Ft")
            IconButton(onClick = { isEditing = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Szerkesztés")
            }
        }
    }
}
