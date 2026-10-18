package com.example.coffee.userapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.coffee.userapp.R
import com.example.coffee.userapp.TransferRules
import com.example.coffee.userapp.TransferState
import com.example.coffee.userview.data.MachinePresence
import com.example.coffee.userview.data.RemotePaths
import com.example.coffee.userview.data.VendProgress
import kotlinx.coroutines.delay

/** Credit transfer: amount in hundreds, confirm, then the recipient taps their card on this phone. */
@Composable
internal fun TransferDialog(
    state: TransferState,
    credit: Long?,
    presence: MachinePresence?,
    onConfirmAmount: (Long) -> Unit,
    onClose: () -> Unit
) {
    val running = state is TransferState.Sending && !state.progress.finished
    AlertDialog(
        onDismissRequest = { if (!running) onClose() },
        title = { Text(stringResource(R.string.ua_transfer)) },
        text = {
            when (state) {
                TransferState.EnterAmount -> AmountStep(credit, presence, onConfirmAmount)
                is TransferState.WaitingForCard -> CardStep(state)
                is TransferState.Sending -> ProgressStep(state)
            }
        },
        confirmButton = {
            if (state is TransferState.Sending && state.progress.finished) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.ua_close)) }
            }
        },
        dismissButton = {
            if (state !is TransferState.Sending) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.ua_cancel)) }
            }
        }
    )
}

@Composable
private fun AmountStep(credit: Long?, presence: MachinePresence?, onConfirm: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(TransferRules.STEP.toString()) }
    val amount = text.toLongOrNull() ?: 0L

    // the machine's presence goes stale without any database change
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val blocked = when {
        presence == null || !presence.transfer -> stringResource(R.string.ua_transfer_unsupported)
        nowMs - presence.lastSeenMs > RemotePaths.PRESENCE_MAX_AGE_MS -> stringResource(R.string.ua_transfer_offline)
        else -> null
    }
    val valid = credit != null && TransferRules.valid(amount, credit)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { text = (amount - TransferRules.STEP).coerceAtLeast(TransferRules.STEP).toString() }) {
                Text("−${TransferRules.STEP}")
            }
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(6) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                suffix = { Text("Ft") },
                modifier = Modifier.width(120.dp)
            )
            OutlinedButton(onClick = { text = (amount + TransferRules.STEP).toString() }) {
                Text("+${TransferRules.STEP}")
            }
        }
        val hint = when {
            blocked != null -> blocked
            amount % TransferRules.STEP != 0L || amount < TransferRules.STEP -> stringResource(R.string.ua_transfer_step)
            credit != null && amount > credit -> stringResource(R.string.ua_transfer_too_much, credit.toInt())
            else -> null
        }
        if (hint != null) {
            Text(hint, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Button(
            onClick = { onConfirm(amount) },
            enabled = valid && blocked == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.ua_transfer_confirm, amount.toInt()))
        }
    }
}

@Composable
private fun CardStep(state: TransferState.WaitingForCard) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            Icons.Default.Contactless,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.ua_transfer_tap_recipient, state.amount.toInt()),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (state.ownCard) {
            Text(stringResource(R.string.ua_transfer_own_card), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ProgressStep(state: TransferState.Sending) {
    val progress = state.progress
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!progress.finished) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        val failed = progress is VendProgress.Failed || progress is VendProgress.TimedOut
        Text(
            transferMessage(state.amount, progress),
            textAlign = TextAlign.Center,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun transferMessage(amount: Long, progress: VendProgress): String = when (progress) {
    VendProgress.Sent, VendProgress.Accepted, is VendProgress.Charged -> stringResource(R.string.ua_transfer_sending, amount.toInt())
    is VendProgress.Done -> progress.credit?.let { stringResource(R.string.ua_transfer_done, amount.toInt(), it.toInt()) }
        ?: stringResource(R.string.ua_transfer_done_no_credit, amount.toInt())
    VendProgress.TimedOut -> stringResource(R.string.ua_transfer_timeout)
    is VendProgress.Failed -> when (progress.code) {
        null -> stringResource(R.string.ua_transfer_failed, progress.message ?: "")
        23 -> stringResource(R.string.ua_transfer_uncertain)
        24 -> stringResource(R.string.ua_transfer_e24)
        30 -> stringResource(R.string.ua_transfer_e30)
        31 -> stringResource(R.string.ua_transfer_e31)
        33 -> stringResource(R.string.ua_transfer_e33)
        else -> stringResource(R.string.ua_transfer_failed, "E${progress.code}")
    }
}
