package com.example.coffee.userview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coffee.userview.R
import com.example.coffee.userview.data.CardUser
import com.example.coffee.userview.data.MachineConfig
import com.example.coffee.userview.data.RemoteVendRepository
import com.example.coffee.userview.data.UserState
import com.example.coffee.userview.data.UserViewRepository
import com.example.coffee.userview.stats.CreditHistory
import com.example.coffee.userview.stats.UsageStats

/**
 * Credit, history, statistics and in-app purchase of one card (users/<cardUid>).
 * The listeners live as long as this screen is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    cardUid: String,
    /** null: no close button (the standalone user app, where this is the main screen) */
    onClose: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    /** title while the user has no name; the standalone user app passes its own so the UID is never shown */
    untitled: String = cardUid,
    repository: UserViewRepository = remember { UserViewRepository() },
    vendRepository: RemoteVendRepository = remember { RemoteVendRepository(machineId = repository.machineId) }
) {
    val userState by remember(cardUid) { repository.observeUser(cardUid) }.collectAsState(initial = UserState.Loading)
    val config by remember { repository.observeConfig() }.collectAsState(initial = null)
    val presence by remember { repository.observePresence() }.collectAsState(initial = null)

    val title = (userState as? UserState.Ready)?.user?.name?.takeIf { it.isNotBlank() } ?: untitled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.uv_close))
                        }
                    }
                },
                actions = actions
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = userState) {
                UserState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                UserState.NotFound -> CenteredText(stringResource(R.string.uv_not_found))
                is UserState.Failed -> CenteredText(stringResource(R.string.uv_load_failed, state.message ?: ""))
                is UserState.Ready -> UserContent(
                    user = state.user,
                    config = config ?: MachineConfig(),
                    buyPanel = {
                        BuyPanel(
                            cardUid = cardUid,
                            credit = state.user.credit,
                            config = config,
                            presence = presence,
                            vendRepository = vendRepository
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun UserContent(user: CardUser, config: MachineConfig, buyPanel: @Composable () -> Unit) {
    val events = remember(user.log) { CreditHistory.classify(user.log) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        CreditHeader(credit = user.credit, price = config.price)
        buyPanel()
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.uv_tab_log)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.uv_tab_stats)) })
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> LogTab(events)
                else -> StatsTab(events)
            }
        }
    }
}

@Composable
private fun CreditHeader(credit: Long, price: Long) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.uv_credit), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.uv_amount_ft, credit.toInt()),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = if (credit > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            val left = UsageStats.coffeesLeft(credit, price)
            when {
                credit <= 0 -> Text(stringResource(R.string.uv_no_credit), style = MaterialTheme.typography.bodyMedium)
                // price unknown (conf not loaded yet): nothing to say about the number of coffees
                price > 0 -> Text(
                    stringResource(R.string.uv_coffees_left, left.toInt(), price.toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
