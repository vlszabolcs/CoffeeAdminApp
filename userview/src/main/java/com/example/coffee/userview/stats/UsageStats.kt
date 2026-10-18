package com.example.coffee.userview.stats

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Totals of a period. */
data class UsageSummary(
    val coffees: Int,
    val spent: Long,
    val toppedUp: Long
)

/** Number of coffees in one chart bucket (a day or a month). */
data class UsageBucket<K>(val key: K, val coffees: Int)

/** Statistics computed from the classified credit log. Pure functions, the time zone is a parameter. */
object UsageStats {
    fun summary(events: List<CreditEvent>, fromEpoch: Long = Long.MIN_VALUE, toEpoch: Long = Long.MAX_VALUE): UsageSummary {
        var coffees = 0
        var spent = 0L
        var toppedUp = 0L
        for (e in events) {
            if (e.timestamp < fromEpoch || e.timestamp >= toEpoch) continue
            when (e.type) {
                CreditEvent.Type.PURCHASE -> {
                    coffees++
                    spent += e.amount
                }
                CreditEvent.Type.TOP_UP -> toppedUp += e.amount
                CreditEvent.Type.INITIAL, CreditEvent.Type.TRANSFER_OUT, CreditEvent.Type.TRANSFER_IN -> Unit
            }
        }
        return UsageSummary(coffees, spent, toppedUp)
    }

    /** The last [count] days ending with [today], oldest first. */
    fun lastDays(today: LocalDate, count: Int): List<LocalDate> =
        (count - 1 downTo 0).map { today.minusDays(it.toLong()) }

    /** The last [count] months ending with [current], oldest first. */
    fun lastMonths(current: YearMonth, count: Int): List<YearMonth> =
        (count - 1 downTo 0).map { current.minusMonths(it.toLong()) }

    fun dailyCoffees(events: List<CreditEvent>, days: List<LocalDate>, zone: ZoneId): List<UsageBucket<LocalDate>> {
        val counts = purchases(events).groupingBy { localDate(it.timestamp, zone) }.eachCount()
        return days.map { UsageBucket(it, counts[it] ?: 0) }
    }

    fun monthlyCoffees(events: List<CreditEvent>, months: List<YearMonth>, zone: ZoneId): List<UsageBucket<YearMonth>> {
        val counts = purchases(events).groupingBy { YearMonth.from(localDate(it.timestamp, zone)) }.eachCount()
        return months.map { UsageBucket(it, counts[it] ?: 0) }
    }

    /** Coffees per hour of the day (index 0..23), over every purchase in [events]. */
    fun coffeesByHour(events: List<CreditEvent>, zone: ZoneId): IntArray {
        val hours = IntArray(24)
        for (e in purchases(events)) {
            hours[Instant.ofEpochSecond(e.timestamp).atZone(zone).hour]++
        }
        return hours
    }

    /** Epoch seconds of the start of [date] in [zone]. */
    fun startOf(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toEpochSecond()

    /**
     * How many more purchases the credit allows. The firmware rule is "credit > 0" (PurchasePolicy.h),
     * so any positive credit still buys one coffee and the credit may go negative.
     */
    fun coffeesLeft(credit: Long, price: Long): Long =
        if (credit <= 0 || price <= 0) 0 else (credit + price - 1) / price

    private fun purchases(events: List<CreditEvent>) = events.filter { it.type == CreditEvent.Type.PURCHASE }

    private fun localDate(epoch: Long, zone: ZoneId): LocalDate = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()
}
