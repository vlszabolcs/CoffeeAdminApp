package com.example.coffee.userapp.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.coffee.userapp.AuthState
import com.example.coffee.userapp.NfcStatus
import com.example.coffee.userapp.PairCode
import com.example.coffee.userapp.R
import com.example.coffee.userapp.SavedCard
import com.example.coffee.userapp.ScanState
import com.example.coffee.userapp.UserAppViewModel
import com.example.coffee.userview.ui.UserScreen
import com.example.coffee.userview.update.UpdateNotice

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
                        onPairCode = viewModel::pairWithCode
                    )
                } else {
                    var confirmReplace by remember { mutableStateOf(false) }
                    UserScreen(
                        cardUid = saved.uid,
                        onClose = null,
                        untitled = stringResource(R.string.app_name),
                        actions = {
                            IconButton(onClick = viewModel::startTransfer) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.ua_transfer))
                            }
                            IconButton(onClick = { confirmReplace = true }) {
                                Icon(Icons.Default.CreditCard, contentDescription = stringResource(R.string.ua_replace_card))
                            }
                        }
                    )
                    val transfer by viewModel.transfer.collectAsState()
                    val credit by viewModel.credit.collectAsState()
                    val presence by viewModel.presence.collectAsState()
                    transfer?.let { state ->
                        TransferDialog(
                            state = state,
                            credit = credit,
                            presence = presence,
                            onConfirmAmount = viewModel::confirmTransferAmount,
                            onClose = viewModel::closeTransfer
                        )
                    }
                    if (confirmReplace) {
                        AlertDialog(
                            onDismissRequest = { confirmReplace = false },
                            title = { Text(stringResource(R.string.ua_replace_title)) },
                            text = { Text(stringResource(R.string.ua_replace_text)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmReplace = false
                                    viewModel.startReplace()
                                }) { Text(stringResource(R.string.ua_replace_confirm)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.ua_cancel)) }
                            }
                        )
                    }
                }
            }
        }
        UpdateNotice(tagPrefix = "userapp-v")
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
    onPairCode: (String) -> Unit
) {
    val context = LocalContext.current
    var enterCode by remember { mutableStateOf(false) }
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
                    ScanState.Checking -> {
                        CircularProgressIndicator()
                        CenteredText(stringResource(R.string.ua_checking))
                    }
                    ScanState.Refused, ScanState.Lost, ScanState.BadCode -> {
                        CenteredText(
                            stringResource(
                                when (scan) {
                                    ScanState.Lost -> R.string.ua_card_lost
                                    ScanState.BadCode -> R.string.ua_pair_bad_code
                                    else -> R.string.ua_refused
                                }
                            )
                        )
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
            // the code works without NFC too: virtual cards, or when the card is not at hand
            if (scan != ScanState.Checking) {
                TextButton(onClick = { enterCode = true }) { Text(stringResource(R.string.ua_pair_enter)) }
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.ua_cancel)) }
            }
        }
    }

    if (enterCode) {
        PairCodeDialog(
            onDismiss = { enterCode = false },
            onConfirm = { code ->
                enterCode = false
                onPairCode(code)
            }
        )
    }
}

@Composable
private fun PairCodeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ua_pair_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ua_pair_text))
                OutlinedTextField(
                    value = code,
                    onValueChange = { value -> code = value.filter { it.isDigit() }.take(PairCode.LENGTH) },
                    label = { Text(stringResource(R.string.ua_pair_code)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = PairCode.valid(code)) {
                Text(stringResource(R.string.ua_pair_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ua_cancel)) }
        }
    )
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
