package com.example.coffeeadmin

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.coffeeadmin.ui.theme.CoffeeAdminTheme

enum class AdminScreen {
    LIST, SEARCH
}

class MainActivity : ComponentActivity() {
    private val viewModel: UserViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    private var currentScreenState = mutableStateOf(AdminScreen.SEARCH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
                                    icon = { Icon(Icons.Default.List, contentDescription = "Lista") },
                                    label = { Text("Lista") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AdminScreen.SEARCH,
                                    onClick = { currentScreen = AdminScreen.SEARCH },
                                    icon = { Icon(Icons.Default.Search, contentDescription = "Keresés") },
                                    label = { Text("Keresés") }
                                )
                            }
                        }
                    ) { innerPadding ->
                        when (currentScreen) {
                            AdminScreen.LIST -> UserListScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                            AdminScreen.SEARCH -> UserSearchScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
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
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val uid = bytesToHexString(it.id)
                handleScannedUid(uid)
            }
        }
    }

    private fun handleScannedUid(uid: String) {
        if (viewModel.isLoggedIn.value) {
            currentScreenState.value = AdminScreen.SEARCH
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
fun LoginScreen(viewModel: UserViewModel, modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading
    val error by viewModel.error

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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Jelszó") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { viewModel.login(email, password) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Bejelentkezés")
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = it, color = Color.Red)
        }
    }
}

@Composable
fun UserListScreen(viewModel: UserViewModel, modifier: Modifier = Modifier) {
    val userList by viewModel.filteredUserList
    val searchQuery by viewModel.searchQuery
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    
    var showEditDialog by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<User?>(null) }
    var expandedUserId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Text(text = "Hiba: $it", color = Color.Red)
        }

        Spacer(modifier = Modifier.height(8.dp))

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

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(userList, key = { it.uid ?: it.hashCode() }) { user ->
                Column {
                    UserRow(
                        user = user, 
                        onClick = { 
                            expandedUserId = if (expandedUserId == user.uid) null else user.uid
                        }
                    )
                    AnimatedVisibility(
                        visible = expandedUserId == user.uid,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            UserDetailCard(
                                user = user,
                                onEditClick = {
                                    userToEdit = user
                                    showEditDialog = true
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

    if (showEditDialog && userToEdit != null) {
        EditUserDialog(
            user = userToEdit!!,
            onDismiss = { 
                showEditDialog = false
                userToEdit = null
            },
            onSave = { updatedUser ->
                viewModel.updateUser(updatedUser)
                showEditDialog = false
                userToEdit = null
            }
        )
    }
}

@Composable
fun UserSearchScreen(
    viewModel: UserViewModel, 
    modifier: Modifier = Modifier
) {
    val searchedUser by viewModel.searchedUser
    val registrationUid by viewModel.scannedUidForRegistration
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    
    var showEditDialog by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<User?>(null) }
    var manualUidSearch by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Keresés", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.logout() }) {
                Text("Kilépés")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = manualUidSearch,
            onValueChange = { if (it.length <= 10000) manualUidSearch = it },
            label = { Text("UID Kézi keresés") },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 10000.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { 
                viewModel.searchUserByUid(manualUidSearch)
            }),
            trailingIcon = {
                IconButton(onClick = { viewModel.searchUserByUid(manualUidSearch) }) {
                    Icon(Icons.Default.Search, contentDescription = "Keresés")
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        if (searchedUser == null && registrationUid == null && !isLoading) {
            Text(
                text = "Kérlek érints oda egy kártyát vagy keress rá manuálisan!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        if (isLoading) {
            CircularProgressIndicator()
        }

        error?.let {
            Text(text = "Hiba: $it", color = Color.Red)
        }

        searchedUser?.let { user ->
            UserDetailCard(
                user = user,
                onEditClick = {
                    userToEdit = user
                    showEditDialog = true
                },
                onUpdateCredit = { amount ->
                    viewModel.updateCredit(user, amount)
                }
            )
        }
    }

    if (registrationUid != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearRegistrationUid() },
            title = { Text("Felhasználó nem található") },
            text = { Text("A beolvasott kártya ($registrationUid) nincs regisztrálva. Szeretnéd regisztrálni?") },
            confirmButton = {
                TextButton(onClick = {
                    userToEdit = User(uid = registrationUid, name = "", credit = 0, loan = 0)
                    showEditDialog = true
                    viewModel.clearRegistrationUid()
                }) {
                    Text("Igen")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearRegistrationUid() }) {
                    Text("Mégse")
                }
            }
        )
    }

    if (showEditDialog && userToEdit != null) {
        EditUserDialog(
            user = userToEdit!!,
            onDismiss = { 
                showEditDialog = false
                userToEdit = null
            },
            onSave = { updatedUser ->
                viewModel.updateUser(updatedUser)
                showEditDialog = false
                userToEdit = null
            }
        )
    }
}

@Composable
fun UserDetailCard(
    user: User,
    onEditClick: () -> Unit,
    onUpdateCredit: (Int) -> Unit
) {
    var creditAdjustText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Felhasználó adatai", 
                    style = MaterialTheme.typography.titleMedium, 
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Szerkesztés")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Név: ${user.name ?: "Nincs megadva"}", fontWeight = FontWeight.Bold)
            Text(text = "UID: ${user.uid ?: "Ismeretlen"}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(text = "Loan (Hitel): ${user.loanInt}", color = if (user.loanInt > 0) Color.Red else Color.Unspecified)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Kredit: ${user.creditInt}", fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.width(8.dp))

                val onUpdate: () -> Unit = {
                    val value = creditAdjustText.toIntOrNull()
                    if (value != null) {
                        onUpdateCredit(value)
                        creditAdjustText = ""
                    }
                }

                Button(
                    onClick = {
                        val current = creditAdjustText.toIntOrNull() ?: 0
                        creditAdjustText = (current - 500).toString()
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("-")
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = creditAdjustText,
                    onValueChange = { if (it.length <= 10000) creditAdjustText = it },
                    label = { Text("Összeg") },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 10000.dp),
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
                    Text("+")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
fun EditUserDialog(user: User, onDismiss: () -> Unit, onSave: (User) -> Unit) {
    var uid by remember { mutableStateOf(user.uid ?: "") }
    var name by remember { mutableStateOf(user.name ?: "") }
    var credit by remember { mutableStateOf(user.creditInt.toString()) }
    var loan by remember { mutableStateOf(user.loanInt.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Felhasználó szerkesztése") },
        text = {
            Column {
                TextField(value = uid, onValueChange = { uid = it }, label = { Text("UID") })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = name, onValueChange = { name = it }, label = { Text("Név") })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = credit, 
                    onValueChange = { credit = it }, 
                    label = { Text("Kredit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = loan, 
                    onValueChange = { loan = it }, 
                    label = { Text("Loan") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(user.copy(
                    uid = uid,
                    name = name,
                    credit = credit.toIntOrNull() ?: 0,
                    loan = loan.toIntOrNull() ?: 0
                ))
            }) {
                Text("Mentés")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
            }) {
                Text("Mégse")
            }
        }
    )
}

@Composable
fun UserRow(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = user.name ?: "Névtelen", modifier = Modifier.weight(1.5f))
        Text(text = "${user.creditInt}", modifier = Modifier.weight(0.5f))
    }
}
