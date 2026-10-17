package com.example.coffee.userview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.coffee.userview.R
import com.example.coffee.userview.data.MachineConfig
import com.example.coffee.userview.data.MachinePresence
import com.example.coffee.userview.data.RemotePaths
import com.example.coffee.userview.data.RemoteVendRepository
import com.example.coffee.userview.data.VendProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Buy a coffee": asks the machine for a virtual card tap. The machine charges the credit itself,
 * so the button only sends the request and shows what the machine reports.
 */
@Composable
internal fun BuyPanel(
    cardUid: String,
    credit: Long,
    config: MachineConfig?,
    presence: MachinePresence?,
    vendRepository: RemoteVendRepository
) {
    val scope = rememberCoroutineScope()
    var progress by remember(cardUid) { mutableStateOf<VendProgress?>(null) }

    // re-evaluate the machine's presence regularly, it goes stale without any database change
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }

    val running = progress?.let { !it.finished } ?: false
    val blockedReason = when {
        config?.free == true -> stringResource(R.string.uv_buy_free)
        presence == null -> stringResource(R.string.uv_buy_unsupported)
        nowMs - presence.lastSeenMs > RemotePaths.PRESENCE_MAX_AGE_MS -> stringResource(R.string.uv_buy_offline)
        !presence.ready -> stringResource(R.string.uv_buy_busy)
        credit <= 0 -> stringResource(R.string.uv_buy_no_credit)
        else -> null
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                // shown at once, so the button cannot send a second request meanwhile
                progress = VendProgress.Sent
                scope.launch {
                    val finished = withTimeoutOrNull(RemotePaths.VEND_TIMEOUT_MS) {
                        vendRepository.requestVend(cardUid).collect { next ->
                            // the late local write confirmation must not hide an answer of the machine
                            if (next != VendProgress.Sent) progress = next
                        }
                        true
                    }
                    if (finished == null) progress = VendProgress.TimedOut
                }
            },
            enabled = blockedReason == null && !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Coffee, contentDescription = null)
            Text(stringResource(R.string.uv_buy), modifier = Modifier.padding(start = 8.dp))
        }

        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        val message = progress?.let { vendMessage(it) } ?: blockedReason
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (progress is VendProgress.Failed || progress is VendProgress.TimedOut) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun vendMessage(progress: VendProgress): String = when (progress) {
    VendProgress.Sent -> stringResource(R.string.uv_vend_sending)
    VendProgress.Accepted -> stringResource(R.string.uv_vend_accepted)
    is VendProgress.Charged -> stringResource(R.string.uv_vend_charged)
    is VendProgress.Done -> progress.credit?.let { stringResource(R.string.uv_vend_done, it.toInt()) }
        ?: stringResource(R.string.uv_vend_done_no_credit)
    VendProgress.TimedOut -> stringResource(R.string.uv_vend_timeout)
    is VendProgress.Failed -> stringResource(
        R.string.uv_vend_failed,
        when (val code = progress.code) {
            null -> progress.message ?: ""
            25 -> stringResource(R.string.uv_vend_e25)
            30 -> stringResource(R.string.uv_vend_e30)
            31 -> stringResource(R.string.uv_vend_e31)
            33 -> stringResource(R.string.uv_vend_e33)
            50 -> stringResource(R.string.uv_vend_e50)
            51 -> stringResource(R.string.uv_vend_e51)
            52 -> stringResource(R.string.uv_vend_e52)
            53 -> stringResource(R.string.uv_vend_e53)
            else -> stringResource(R.string.uv_vend_error_code, code)
        }
    )
}
