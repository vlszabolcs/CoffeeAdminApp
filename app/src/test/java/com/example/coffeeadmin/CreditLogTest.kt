package com.example.coffeeadmin

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditLogTest {
    @Test
    fun nextKey_isNowWhenLogIsOlder() {
        assertEquals(1000L, CreditLog.nextKey(1000L, listOf("900", "950")))
    }

    @Test
    fun nextKey_isStrictlyIncreasing() {
        assertEquals(1001L, CreditLog.nextKey(1000L, listOf("1000")))
        assertEquals(1006L, CreditLog.nextKey(1000L, listOf("1005", "x", null)))
    }

    @Test
    fun toEntries_classifiesByDifference() {
        val raw = mapOf(30L to 1000L, 10L to 500L, 20L to 300L)
        val entries = CreditLog.toEntries("UID", raw, previousCredit = null)
        assertEquals(listOf(10L, 20L, 30L), entries.map { it.timestamp })
        assertEquals(
            listOf(CreditLog.ACTION_INITIAL, CreditLog.ACTION_PURCHASE, CreditLog.ACTION_TOP_UP),
            entries.map { it.actionInt }
        )
        assertEquals(listOf(500L, 300L, 1000L), entries.map { it.remainingCredit })
    }

    @Test
    fun toEntries_usesPreviousCredit() {
        val entries = CreditLog.toEntries("UID", mapOf(10L to 200L), previousCredit = 500L)
        assertEquals(CreditLog.ACTION_PURCHASE, entries.single().actionInt)
    }
}
