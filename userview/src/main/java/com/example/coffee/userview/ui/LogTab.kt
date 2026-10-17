package com.example.coffee.userview.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coffee.userview.R
import com.example.coffee.userview.stats.CreditEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logTimeFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd. HH:mm")

/** The credit history, newest first. */
@Composable
internal fun LogTab(events: List<CreditEvent>) {
    if (events.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.uv_log_empty))
        }
        return
    }
    val newestFirst = remember(events) { events.asReversed() }
    val zone = remember { ZoneId.systemDefault() }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(newestFirst, key = { it.timestamp }) { event ->
            LogRow(event, zone)
            HorizontalDivider()
        }
    }
}

@Composable
private fun LogRow(event: CreditEvent, zone: ZoneId) {
    val label = when (event.type) {
        CreditEvent.Type.PURCHASE -> stringResource(R.string.uv_event_purchase)
        CreditEvent.Type.TOP_UP -> stringResource(R.string.uv_event_top_up)
        CreditEvent.Type.INITIAL -> stringResource(R.string.uv_event_initial)
    }
    val (amountText, amountColor) = when (event.type) {
        CreditEvent.Type.PURCHASE -> "−${event.amount} Ft" to MaterialTheme.colorScheme.error
        CreditEvent.Type.TOP_UP -> "+${event.amount} Ft" to MaterialTheme.colorScheme.primary
        CreditEvent.Type.INITIAL -> "${event.amount} Ft" to MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                logTimeFormat.format(Instant.ofEpochSecond(event.timestamp).atZone(zone)),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amountText, color = amountColor, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.uv_balance_after, event.balance.toInt()), style = MaterialTheme.typography.bodySmall)
        }
    }
}
