package com.example.coffee.userapp.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.coffee.userapp.AuthState
import com.example.coffee.userapp.NfcStatus
import com.example.coffee.userapp.R
import com.example.coffee.userapp.SavedCard
import com.example.coffee.userapp.ScanState
import com.example.coffee.userapp.UserAppViewModel
import com.example.coffee.userview.ui.UserScreen

@Composable
fun UserApp(viewModel: UserAppViewModel) {
    val auth by viewModel.authState.collectAsState()
    val card by viewModel.card.collectAsState()
    val replacing by viewModel.replacing.collectAsState()
    val scan by viewModel.scan.collectAsState()
    val nfc by viewModel.nfcStatus.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = auth) {
            AuthState.SigningIn -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is AuthState.Failed -> Message(
                text = stringResource(R.string.ua_sign_in_failed, state.message ?: ""),
                action = stringResource(R.string.ua_retry),
                onAction = viewModel::signIn
            )
            AuthState.SignedIn -> {
                val saved = card
                if (saved == null || replacing) {
                    ScanScreen(
                        saved = saved,
                        scan = scan,
                        nfc = nfc,
                        onCancel = if (saved != null) viewModel::cancelReplace else null,
                        onDismissMessage = viewModel::dismissScanMessage,
                        onConfirmReplace = viewModel::confirmReplace,
                        onRejectReplace = viewModel::rejectReplace
                    )
                } else {
                    UserScreen(
                        cardUid = saved.uid,
                        onClose = null,
                        actions = {
                            IconButton(onClick = viewModel::startReplace) {
                                Icon(Icons.Default.CreditCard, contentDescription = stringResource(R.string.ua_replace_card))
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanScreen(
    saved: SavedCard?,
    scan: ScanState,
    nfc: NfcStatus,
    onCancel: (() -> Unit)?,
    onDismissMessage: () -> Unit,
    onConfirmReplace: () -> Unit,
    onRejectReplace: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(stringResource(if (saved == null) R.string.app_name else R.string.ua_replace_card))
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Icon(
                Icons.Default.Contactless,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            when (nfc) {
                NfcStatus.Missing -> CenteredText(stringResource(R.string.ua_nfc_missing))
                NfcStatus.Disabled -> {
                    CenteredText(stringResource(R.string.ua_nfc_disabled))
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                        Text(stringResource(R.string.ua_nfc_settings))
                    }
                }
                else -> when (scan) {
                    is ScanState.Checking -> {
                        CircularProgressIndicator()
                        CenteredText(stringResource(R.string.ua_checking, scan.uid))
                    }
                    is ScanState.NotRegistered -> {
                        CenteredText(stringResource(R.string.ua_not_registered, scan.uid))
                        OutlinedButton(onClick = onDismissMessage) { Text(stringResource(R.string.ua_try_again)) }
                    }
                    is ScanState.Failed -> {
                        CenteredText(stringResource(R.string.ua_check_failed, scan.message ?: ""))
                        OutlinedButton(onClick = onDismissMessage) { Text(stringResource(R.string.ua_try_again)) }
                    }
                    else -> {
                        CenteredText(stringResource(R.string.ua_tap_card), MaterialTheme.typography.titleMedium)
                        CenteredText(stringResource(R.string.ua_one_card))
                    }
                }
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.ua_cancel)) }
            }
        }
    }

    if (scan is ScanState.ConfirmReplace) {
        AlertDialog(
            onDismissRequest = onRejectReplace,
            title = { Text(stringResource(R.string.ua_replace_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ua_replace_text,
                        scan.old.name ?: scan.old.uid,
                        scan.new.name ?: scan.new.uid
                    )
                )
            },
            confirmButton = { TextButton(onClick = onConfirmReplace) { Text(stringResource(R.string.ua_replace_confirm)) } },
            dismissButton = { TextButton(onClick = onRejectReplace) { Text(stringResource(R.string.ua_cancel)) } }
        )
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        CenteredText(text)
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun CenteredText(text: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    Text(text, style = style, textAlign = TextAlign.Center)
}
