package com.example.coffee.userview.stats

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class UsageStatsTest {
    private val zone = ZoneId.of("Europe/Budapest")

    private fun epoch(y: Int, m: Int, d: Int, h: Int) = LocalDateTime.of(y, m, d, h, 0).atZone(zone).toEpochSecond()

    private fun purchase(epoch: Long, amount: Long = 150) = CreditEvent(epoch, CreditEvent.Type.PURCHASE, amount, 0)

    @Test
    fun summaryCountsPurchasesAndTopUpsInRange() {
        val events = listOf(
            CreditEvent(epoch(2026, 10, 1, 8), CreditEvent.Type.INITIAL, 1000, 1000),
            purchase(epoch(2026, 10, 1, 9)),
            CreditEvent(epoch(2026, 10, 2, 9), CreditEvent.Type.TOP_UP, 2000, 2850),
            purchase(epoch(2026, 10, 3, 9), 200),
            purchase(epoch(2026, 11, 1, 9))
        )
        val from = UsageStats.startOf(LocalDate.of(2026, 10, 1), zone)
        val to = UsageStats.startOf(LocalDate.of(2026, 11, 1), zone)

        assertEquals(UsageSummary(coffees = 2, spent = 350, toppedUp = 2000), UsageStats.summary(events, from, to))
        assertEquals(UsageSummary(coffees = 3, spent = 500, toppedUp = 2000), UsageStats.summary(events))
    }

    @Test
    fun dailyBucketsUseLocalDays() {
        // 23:30 local on Oct 16 is still Oct 16, although it is Oct 16 21:30 UTC
        val events = listOf(
            purchase(LocalDateTime.of(2026, 10, 16, 23, 30).atZone(zone).toEpochSecond()),
            purchase(epoch(2026, 10, 17, 0)),
            purchase(epoch(2026, 10, 17, 10))
        )
        val days = UsageStats.lastDays(LocalDate.of(2026, 10, 17), 3)

        assertEquals(listOf(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 17)), days)
        assertEquals(listOf(0, 1, 2), UsageStats.dailyCoffees(events, days, zone).map { it.coffees })
    }

    @Test
    fun monthlyBuckets() {
        val events = listOf(purchase(epoch(2026, 9, 30, 12)), purchase(epoch(2026, 10, 1, 12)), purchase(epoch(2026, 10, 5, 12)))
        val months = UsageStats.lastMonths(YearMonth.of(2026, 10), 3)

        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), months)
        assertEquals(listOf(0, 1, 2), UsageStats.monthlyCoffees(events, months, zone).map { it.coffees })
    }

    @Test
    fun coffeesByHour() {
        val hours = UsageStats.coffeesByHour(listOf(purchase(epoch(2026, 10, 1, 8)), purchase(epoch(2026, 10, 2, 8)), purchase(epoch(2026, 10, 2, 14))), zone)
        val expected = IntArray(24).also { it[8] = 2; it[14] = 1 }
        assertArrayEquals(expected, hours)
    }

    @Test
    fun coffeesLeftFollowsTheCreditAboveZeroRule() {
        assertEquals(0, UsageStats.coffeesLeft(0, 150))
        assertEquals(0, UsageStats.coffeesLeft(-50, 150))
        assertEquals(1, UsageStats.coffeesLeft(100, 150))
        assertEquals(2, UsageStats.coffeesLeft(300, 150))
        assertEquals(3, UsageStats.coffeesLeft(301, 150))
        assertEquals(0, UsageStats.coffeesLeft(300, 0))
    }
}
