package com.example.coffee.userview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coffee.userview.R
import com.example.coffee.userview.stats.CreditEvent
import com.example.coffee.userview.stats.UsageStats
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class StatsPeriod { WEEK, MONTH, YEAR }

private data class Bar(val label: String, val value: Int, val highlighted: Boolean)

private val dayLabel = DateTimeFormatter.ofPattern("d")
private val weekdayLabel = DateTimeFormatter.ofPattern("EEE")
private val monthLabel = DateTimeFormatter.ofPattern("MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsTab(events: List<CreditEvent>) {
    var period by rememberSaveable { mutableStateOf(StatsPeriod.WEEK) }
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)

    val (bars, from) = remember(events, period, today) {
        when (period) {
            StatsPeriod.WEEK, StatsPeriod.MONTH -> {
                val days = UsageStats.lastDays(today, if (period == StatsPeriod.WEEK) 7 else 30)
                val format = if (period == StatsPeriod.WEEK) weekdayLabel else dayLabel
                UsageStats.dailyCoffees(events, days, zone).map {
                    Bar(format.format(it.key), it.coffees, it.key == today)
                } to UsageStats.startOf(days.first(), zone)
            }
            StatsPeriod.YEAR -> {
                val months = UsageStats.lastMonths(YearMonth.from(today), 12)
                UsageStats.monthlyCoffees(events, months, zone).map {
                    Bar(monthLabel.format(it.key), it.coffees, it.key == YearMonth.from(today))
                } to UsageStats.startOf(months.first().atDay(1), zone)
            }
        }
    }
    val summary = remember(events, from) { UsageStats.summary(events, fromEpoch = from) }
    val total = remember(events) { UsageStats.summary(events) }
    val byHour = remember(events) { UsageStats.coffeesByHour(events, zone) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = {
                        Text(
                            stringResource(
                                when (p) {
                                    StatsPeriod.WEEK -> R.string.uv_period_week
                                    StatsPeriod.MONTH -> R.string.uv_period_month
                                    StatsPeriod.YEAR -> R.string.uv_period_year
                                }
                            )
                        )
                    }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard(stringResource(R.string.uv_stat_coffees), summary.coffees.toString(), Modifier.weight(1f))
            SummaryCard(stringResource(R.string.uv_stat_spent), "${summary.spent} Ft", Modifier.weight(1f))
            SummaryCard(stringResource(R.string.uv_stat_topped_up), "${summary.toppedUp} Ft", Modifier.weight(1f))
        }

        BarChart(bars = bars, showValues = bars.size <= 12, labelEvery = if (bars.size > 12) 5 else 1)

        Text(stringResource(R.string.uv_stat_by_hour), style = MaterialTheme.typography.titleSmall)
        BarChart(
            bars = byHour.mapIndexed { hour, count -> Bar(hour.toString(), count, false) },
            showValues = false,
            labelEvery = 3
        )

        Text(
            stringResource(R.string.uv_stat_total, total.coffees, total.spent.toInt()),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BarChart(bars: List<Bar>, showValues: Boolean, labelEvery: Int) {
    val max = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEachIndexed { index, bar ->
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    if (bar.value > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight(bar.value.toFloat() / max)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(
                                    if (bar.highlighted) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary
                                )
                        )
                    }
                }
                Text(if (showValues) bar.value.toString() else "", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(if (index % labelEvery == 0) bar.label else "", fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}
